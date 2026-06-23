//! Port of src-tauri/src/commands/dashboard.rs, scoped by user_id.

use finanzas_core::error::AppResult;
use finanzas_core::period::{resolve_period, Period};
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
pub struct DashboardSummary {
    /// Cash (wallets) total in MXN at the START of the selected period.
    pub total_start_mxn_cents: i64,
    /// Cash (wallets) total in MXN at the END of the period (headline cash).
    pub total_end_mxn_cents: i64,
    /// Per-wallet balances at the end of the period.
    pub wallets: Vec<WalletBalance>,
    pub by_currency: Vec<CurrencySubtotal>,
    /// Currencies with non-MXN wallets but no exchange rate configured;
    /// their balances are excluded from the MXN total.
    pub missing_rates: Vec<String>,
    /// Open-investment value in MXN at the start and end of the period.
    pub investments_start_mxn_cents: i64,
    pub investments_total_mxn_cents: i64,
    /// Per-investment values (end of period) for the dashboard donut.
    pub investments: Vec<InvestmentSlice>,
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct SummaryArgs {
    #[serde(default)]
    pub period: Period,
}

#[derive(Deserialize)]
struct RateRow {
    currency_code: String,
    rate_to_mxn_micros: i64,
}

/// Latest rate per currency in micros; MXN is always 1.0. The user's own manual
/// rate wins over the global auto-fetched one (user_id 0); else the global is
/// used. `(user_id = ?1) DESC` sorts the user's rows ahead of the global ones,
/// then the newest id within that group.
pub async fn load_rates(db: &D1Database, uid: i64) -> AppResult<HashMap<String, i64>> {
    let mut rates = HashMap::from([("MXN".to_string(), MICROS)]);
    let rows: Vec<RateRow> = all(
        db,
        "SELECT currency_code, rate_to_mxn_micros FROM (
           SELECT currency_code, rate_to_mxn_micros,
                  ROW_NUMBER() OVER (PARTITION BY currency_code
                                     ORDER BY (user_id = ?1) DESC, id DESC) AS rn
           FROM exchange_rates WHERE user_id IN (?1, 0)
         ) WHERE rn = 1",
        jsv![uid],
    )
    .await?;
    for r in rows {
        rates.insert(r.currency_code, r.rate_to_mxn_micros);
    }
    Ok(rates)
}

pub(crate) fn to_mxn(cents: i64, rate_micros: i64) -> i64 {
    ((cents as i128 * rate_micros as i128) / MICROS as i128) as i64
}

/// Convert an amount between two currencies using their rates-to-MXN (micros).
/// `cents` is in `from`; the result is in `to`. The MICROS scale cancels, so we
/// go through i128 directly: cents_to = cents_from * rate[from] / rate[to].
/// Errors if either currency has no configured rate.
pub(crate) fn convert(
    cents: i64,
    from: &str,
    to: &str,
    rates: &HashMap<String, i64>,
) -> AppResult<i64> {
    if from == to {
        return Ok(cents);
    }
    let from_rate = *rates.get(from).ok_or_else(|| {
        finanzas_core::error::AppError::InvalidInput(format!("no hay tipo de cambio para {from}"))
    })?;
    let to_rate = *rates.get(to).ok_or_else(|| {
        finanzas_core::error::AppError::InvalidInput(format!("no hay tipo de cambio para {to}"))
    })?;
    Ok(((cents as i128 * from_rate as i128) / to_rate as i128) as i64)
}

#[derive(Deserialize)]
struct BalanceRow {
    id: i64,
    name: String,
    color: Option<String>,
    currency_code: String,
    balance_cents: i64,
}

/// Per-wallet balance as of `cutoff` (exclusive): initial + Σ transactions
/// strictly before that date. `cutoff` is a 'YYYY-MM-DD' bound parameter.
async fn wallet_balances_at(db: &D1Database, uid: i64, cutoff: &str) -> AppResult<Vec<BalanceRow>> {
    all(
        db,
        "SELECT w.id, w.name, w.color, w.currency_code,
                w.initial_balance_cents + COALESCE((
                  SELECT SUM(CASE t.kind
                               WHEN 'income' THEN t.amount_cents
                               WHEN 'transfer_in' THEN t.amount_cents
                               ELSE -t.amount_cents END)
                  FROM transactions t
                  WHERE t.wallet_id = w.id AND t.occurred_at < ?2), 0) AS balance_cents
         FROM wallets w
         WHERE w.is_archived = 0 AND w.user_id = ?1
         ORDER BY balance_cents DESC",
        jsv![uid, cutoff],
    )
    .await
}

fn sum_mxn(rows: &[BalanceRow], rates: &HashMap<String, i64>) -> i64 {
    rows.iter()
        .map(|r| {
            rates
                .get(&r.currency_code)
                .map(|rate| to_mxn(r.balance_cents, *rate))
                .unwrap_or(0)
        })
        .sum()
}

pub async fn get_dashboard_summary(
    db: &D1Database,
    uid: i64,
    a: SummaryArgs,
) -> AppResult<DashboardSummary> {
    let rates = load_rates(db, uid).await?;
    let resolved = resolve_period(&a.period, today_mx());
    let start = resolved.start.to_string();
    let end = resolved.end.to_string();
    // Value investments at the opening (day before the period) and the close
    // (last day of the period), never projecting past today. Using the day before
    // the exclusive bounds also drops investments that only start on the boundary.
    let today = today_mx();
    let inv_start = resolved.start.pred_opt().unwrap_or(resolved.start).min(today);
    let inv_end = resolved.end.pred_opt().unwrap_or(resolved.end).min(today);

    // End-of-period balances drive the wallet list, donut and by-currency totals.
    let rows = wallet_balances_at(db, uid, &end).await?;
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

    let total_end_mxn_cents = by_currency.iter().map(|c| c.balance_mxn_cents).sum();
    let missing_rates: Vec<String> = by_currency
        .iter()
        .filter(|c| !c.has_rate)
        .map(|c| c.currency_code.clone())
        .collect();

    // Cash at the start of the period (a single MXN figure for the hero).
    let total_start_mxn_cents = sum_mxn(&wallet_balances_at(db, uid, &start).await?, &rates);

    let investments = open_investments_mxn(db, uid, &rates, inv_end).await?;
    let investments_total_mxn_cents = investments.iter().map(|s| s.value_mxn_cents).sum();
    let investments_start_mxn_cents = open_investments_mxn(db, uid, &rates, inv_start)
        .await?
        .iter()
        .map(|s| s.value_mxn_cents)
        .sum();

    Ok(DashboardSummary {
        total_start_mxn_cents,
        total_end_mxn_cents,
        wallets,
        by_currency,
        missing_rates,
        investments_start_mxn_cents,
        investments_total_mxn_cents,
        investments,
    })
}
