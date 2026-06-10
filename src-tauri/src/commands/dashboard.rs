use rusqlite::Connection;
use serde::Serialize;
use std::collections::HashMap;
use tauri::State;

use crate::db::Db;
use crate::error::{AppError, AppResult};

const MICROS: i64 = 1_000_000;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WalletBalance {
    pub wallet_id: i64,
    pub name: String,
    pub color: Option<String>,
    pub currency_code: String,
    pub balance_cents: i64,
    /// Converted with the latest manual rate; equals balance for MXN.
    pub balance_mxn_cents: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CurrencySubtotal {
    pub currency_code: String,
    pub balance_cents: i64,
    pub balance_mxn_cents: i64,
    pub has_rate: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MonthlyFlow {
    pub month: String, // 'YYYY-MM'
    pub income_mxn_cents: i64,
    pub expense_mxn_cents: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DashboardSummary {
    pub total_mxn_cents: i64,
    pub wallets: Vec<WalletBalance>,
    pub by_currency: Vec<CurrencySubtotal>,
    pub monthly: Vec<MonthlyFlow>,
    /// Currencies with non-MXN wallets but no exchange rate configured;
    /// their balances are excluded from the MXN total.
    pub missing_rates: Vec<String>,
}

/// Latest rate per currency in micros; MXN is always 1.0.
fn load_rates(conn: &Connection) -> AppResult<HashMap<String, i64>> {
    let mut rates = HashMap::from([("MXN".to_string(), MICROS)]);
    let mut stmt = conn.prepare(
        "SELECT currency_code, rate_to_mxn_micros FROM exchange_rates
         WHERE id IN (SELECT MAX(id) FROM exchange_rates GROUP BY currency_code)",
    )?;
    let rows = stmt.query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, i64>(1)?)))?;
    for row in rows {
        let (code, rate) = row?;
        rates.insert(code, rate);
    }
    Ok(rates)
}

fn to_mxn(cents: i64, rate_micros: i64) -> i64 {
    ((cents as i128 * rate_micros as i128) / MICROS as i128) as i64
}

pub fn summarize(conn: &Connection) -> AppResult<DashboardSummary> {
    let rates = load_rates(conn)?;

    let mut stmt = conn.prepare(
        "SELECT w.id, w.name, w.color, w.currency_code,
                w.initial_balance_cents + COALESCE((
                  SELECT SUM(CASE t.kind
                               WHEN 'income' THEN t.amount_cents
                               WHEN 'transfer_in' THEN t.amount_cents
                               ELSE -t.amount_cents END)
                  FROM transactions t WHERE t.wallet_id = w.id), 0) AS balance_cents
         FROM wallets w
         WHERE w.is_archived = 0
         ORDER BY balance_cents DESC",
    )?;
    let mut wallets: Vec<WalletBalance> = Vec::new();
    let rows = stmt.query_map([], |r| {
        Ok((
            r.get::<_, i64>(0)?,
            r.get::<_, String>(1)?,
            r.get::<_, Option<String>>(2)?,
            r.get::<_, String>(3)?,
            r.get::<_, i64>(4)?,
        ))
    })?;
    for row in rows {
        let (wallet_id, name, color, currency_code, balance_cents) = row?;
        let balance_mxn_cents = rates
            .get(&currency_code)
            .map(|rate| to_mxn(balance_cents, *rate))
            .unwrap_or(0);
        wallets.push(WalletBalance {
            wallet_id,
            name,
            color,
            currency_code,
            balance_cents,
            balance_mxn_cents,
        });
    }

    let mut by_currency_map: HashMap<String, CurrencySubtotal> = HashMap::new();
    for w in &wallets {
        let entry =
            by_currency_map.entry(w.currency_code.clone()).or_insert_with(|| CurrencySubtotal {
                currency_code: w.currency_code.clone(),
                balance_cents: 0,
                balance_mxn_cents: 0,
                has_rate: rates.contains_key(&w.currency_code),
            });
        entry.balance_cents += w.balance_cents;
        entry.balance_mxn_cents += w.balance_mxn_cents;
    }
    let mut by_currency: Vec<CurrencySubtotal> = by_currency_map.into_values().collect();
    by_currency.sort_by(|a, b| a.currency_code.cmp(&b.currency_code));

    let total_mxn_cents = by_currency.iter().map(|c| c.balance_mxn_cents).sum();
    let missing_rates: Vec<String> = by_currency
        .iter()
        .filter(|c| !c.has_rate)
        .map(|c| c.currency_code.clone())
        .collect();

    // Income/expense per month, last 6 months (transfers excluded), in MXN.
    let mut monthly_map: HashMap<String, MonthlyFlow> = HashMap::new();
    let mut stmt = conn.prepare(
        "SELECT strftime('%Y-%m', t.occurred_at) AS month, t.kind, w.currency_code,
                SUM(t.amount_cents)
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE t.kind IN ('income', 'expense')
           AND t.occurred_at >= date('now', 'start of month', '-5 months')
         GROUP BY month, t.kind, w.currency_code",
    )?;
    let rows = stmt.query_map([], |r| {
        Ok((
            r.get::<_, String>(0)?,
            r.get::<_, String>(1)?,
            r.get::<_, String>(2)?,
            r.get::<_, i64>(3)?,
        ))
    })?;
    for row in rows {
        let (month, kind, currency_code, sum_cents) = row?;
        let mxn = rates.get(&currency_code).map(|rate| to_mxn(sum_cents, *rate)).unwrap_or(0);
        let entry = monthly_map.entry(month.clone()).or_insert_with(|| MonthlyFlow {
            month,
            income_mxn_cents: 0,
            expense_mxn_cents: 0,
        });
        if kind == "income" {
            entry.income_mxn_cents += mxn;
        } else {
            entry.expense_mxn_cents += mxn;
        }
    }
    let mut monthly: Vec<MonthlyFlow> = monthly_map.into_values().collect();
    monthly.sort_by(|a, b| a.month.cmp(&b.month));

    Ok(DashboardSummary { total_mxn_cents, wallets, by_currency, monthly, missing_rates })
}

#[tauri::command]
pub fn get_dashboard_summary(db: State<Db>) -> AppResult<DashboardSummary> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    summarize(&conn)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::commands::transactions::{insert_simple, insert_transfer};
    use crate::db::open_in_memory;
    use rusqlite::params;

    fn make_wallet(conn: &Connection, name: &str, currency: &str, initial_cents: i64) -> i64 {
        conn.execute(
            "INSERT INTO wallets (name, category_id, currency_code, initial_balance_cents)
             VALUES (?1, 1, ?2, ?3)",
            params![name, currency, initial_cents],
        )
        .unwrap();
        conn.last_insert_rowid()
    }

    #[test]
    fn total_converts_foreign_currency_with_latest_rate() {
        let conn = open_in_memory();
        make_wallet(&conn, "Efectivo", "MXN", 100_000); // $1,000.00 MXN
        make_wallet(&conn, "USD cash", "USD", 10_000); // $100.00 USD
        // stale rate then current rate: latest must win
        conn.execute(
            "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros) VALUES ('USD', 17000000)",
            [],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros) VALUES ('USD', 18500000)",
            [],
        )
        .unwrap();

        let s = summarize(&conn).unwrap();
        // 1,000.00 MXN + 100.00 USD * 18.50 = 1,000 + 1,850 = 2,850.00 MXN
        assert_eq!(s.total_mxn_cents, 285_000);
        assert!(s.missing_rates.is_empty());
    }

    #[test]
    fn currency_without_rate_is_flagged_and_excluded() {
        let conn = open_in_memory();
        make_wallet(&conn, "Efectivo", "MXN", 50_000);
        make_wallet(&conn, "USD cash", "USD", 10_000);

        let s = summarize(&conn).unwrap();
        assert_eq!(s.total_mxn_cents, 50_000);
        assert_eq!(s.missing_rates, vec!["USD".to_string()]);
    }

    #[test]
    fn monthly_flows_exclude_transfers() {
        let mut conn = open_in_memory();
        let a = make_wallet(&conn, "A", "MXN", 0);
        let b = make_wallet(&conn, "B", "MXN", 0);
        let today = chrono::Local::now().format("%Y-%m-%d").to_string();
        insert_simple(&conn, a, "income", 100_000, None, None, &today).unwrap();
        insert_simple(&conn, a, "expense", 30_000, None, None, &today).unwrap();
        insert_transfer(&mut conn, a, b, 20_000, 20_000, None, &today).unwrap();

        let s = summarize(&conn).unwrap();
        assert_eq!(s.monthly.len(), 1);
        assert_eq!(s.monthly[0].income_mxn_cents, 100_000);
        assert_eq!(s.monthly[0].expense_mxn_cents, 30_000);
        // transfers move money between wallets but are not income/expense
        assert_eq!(s.total_mxn_cents, 70_000);
    }
}
