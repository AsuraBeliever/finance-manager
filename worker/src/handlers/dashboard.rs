//! Port of src-tauri/src/commands/dashboard.rs, scoped by user_id.

use finanzas_core::error::AppResult;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use worker::D1Database;

use super::investments::{open_investments_mxn, InvestmentSlice};
use crate::db::{all, today_mx};
use crate::jsv;

const MICROS: i64 = 1_000_000;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WalletBalance {
    pub wallet_id: i64,
    pub name: String,
    pub color: Option<String>,
    pub currency_code: String,
    pub balance_cents: i64,
    /// Converted with the latest rate; equals balance for MXN.
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
    /// Current value of open investments, converted to MXN.
    pub investments_total_mxn_cents: i64,
    /// Per-investment values for the dashboard donut.
    pub investments: Vec<InvestmentSlice>,
}

#[derive(Deserialize)]
struct RateRow {
    currency_code: String,
    rate_to_mxn_micros: i64,
}

/// Latest rate per currency in micros; MXN is always 1.0.
pub async fn load_rates(db: &D1Database) -> AppResult<HashMap<String, i64>> {
    let mut rates = HashMap::from([("MXN".to_string(), MICROS)]);
    let rows: Vec<RateRow> = all(
        db,
        "SELECT currency_code, rate_to_mxn_micros FROM exchange_rates
         WHERE id IN (SELECT MAX(id) FROM exchange_rates GROUP BY currency_code)",
        vec![],
    )
    .await?;
    for r in rows {
        rates.insert(r.currency_code, r.rate_to_mxn_micros);
    }
    Ok(rates)
}

fn to_mxn(cents: i64, rate_micros: i64) -> i64 {
    ((cents as i128 * rate_micros as i128) / MICROS as i128) as i64
}

#[derive(Deserialize)]
struct BalanceRow {
    id: i64,
    name: String,
    color: Option<String>,
    currency_code: String,
    balance_cents: i64,
}

#[derive(Deserialize)]
struct FlowRow {
    month: String,
    kind: String,
    currency_code: String,
    sum_cents: i64,
}

pub async fn get_dashboard_summary(db: &D1Database, uid: i64) -> AppResult<DashboardSummary> {
    let rates = load_rates(db).await?;

    let rows: Vec<BalanceRow> = all(
        db,
        "SELECT w.id, w.name, w.color, w.currency_code,
                w.initial_balance_cents + COALESCE((
                  SELECT SUM(CASE t.kind
                               WHEN 'income' THEN t.amount_cents
                               WHEN 'transfer_in' THEN t.amount_cents
                               ELSE -t.amount_cents END)
                  FROM transactions t WHERE t.wallet_id = w.id), 0) AS balance_cents
         FROM wallets w
         WHERE w.is_archived = 0 AND w.user_id = ?1
         ORDER BY balance_cents DESC",
        jsv![uid],
    )
    .await?;
    let mut wallets: Vec<WalletBalance> = Vec::with_capacity(rows.len());
    for r in rows {
        let balance_mxn_cents = rates
            .get(&r.currency_code)
            .map(|rate| to_mxn(r.balance_cents, *rate))
            .unwrap_or(0);
        wallets.push(WalletBalance {
            wallet_id: r.id,
            name: r.name,
            color: r.color,
            currency_code: r.currency_code,
            balance_cents: r.balance_cents,
            balance_mxn_cents,
        });
    }

    let mut by_currency_map: HashMap<String, CurrencySubtotal> = HashMap::new();
    for w in &wallets {
        let entry = by_currency_map
            .entry(w.currency_code.clone())
            .or_insert_with(|| CurrencySubtotal {
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
    let rows: Vec<FlowRow> = all(
        db,
        "SELECT strftime('%Y-%m', t.occurred_at) AS month, t.kind, w.currency_code,
                SUM(t.amount_cents) AS sum_cents
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE t.kind IN ('income', 'expense')
           AND w.user_id = ?1
           AND t.occurred_at >= date('now', 'start of month', '-5 months')
         GROUP BY month, t.kind, w.currency_code",
        jsv![uid],
    )
    .await?;
    let mut monthly_map: HashMap<String, MonthlyFlow> = HashMap::new();
    for r in rows {
        let mxn = rates
            .get(&r.currency_code)
            .map(|rate| to_mxn(r.sum_cents, *rate))
            .unwrap_or(0);
        let entry = monthly_map
            .entry(r.month.clone())
            .or_insert_with(|| MonthlyFlow {
                month: r.month,
                income_mxn_cents: 0,
                expense_mxn_cents: 0,
            });
        if r.kind == "income" {
            entry.income_mxn_cents += mxn;
        } else {
            entry.expense_mxn_cents += mxn;
        }
    }
    let mut monthly: Vec<MonthlyFlow> = monthly_map.into_values().collect();
    monthly.sort_by(|a, b| a.month.cmp(&b.month));

    let investments = open_investments_mxn(db, uid, &rates, today_mx()).await?;
    let investments_total_mxn_cents = investments.iter().map(|s| s.value_mxn_cents).sum();

    Ok(DashboardSummary {
        total_mxn_cents,
        wallets,
        by_currency,
        monthly,
        missing_rates,
        investments_total_mxn_cents,
        investments,
    })
}
