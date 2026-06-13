//! Spending analytics: category breakdown + period trends. Every amount is
//! converted to MXN in Rust (i128 intermediate), reusing dashboard.rs helpers;
//! the frontend only divides for the % labels and formats.

use std::collections::HashMap;

use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{load_rates, to_mxn};
use crate::db::all;
use crate::jsv;

// ---- category breakdown ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BreakdownArgs {
    pub kind: String,           // 'income' | 'expense'
    pub period: Option<String>, // 'month' (default) | 'week' | 'all'
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CategorySlice {
    pub category_id: Option<i64>,
    pub name: String,
    pub color: Option<String>,
    pub icon: Option<String>,
    pub mxn_cents: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CategoryBreakdown {
    pub total_mxn_cents: i64,
    pub slices: Vec<CategorySlice>,
}

/// Allowlisted SQL date expression for the start of the requested window.
/// Never interpolates user text — `period` only selects a fixed literal.
fn period_start_sql(period: Option<&str>) -> &'static str {
    match period {
        Some("week") => "date('now', '-6 days')",
        Some("all") => "date('1970-01-01')",
        _ => "date('now', 'start of month')",
    }
}

#[derive(Deserialize)]
struct SliceRow {
    category_id: Option<i64>,
    name: Option<String>,
    color: Option<String>,
    icon: Option<String>,
    currency_code: String,
    sum_cents: i64,
}

pub async fn get_category_breakdown(
    db: &D1Database,
    uid: i64,
    a: BreakdownArgs,
) -> AppResult<CategoryBreakdown> {
    if a.kind != "income" && a.kind != "expense" {
        return Err(AppError::InvalidInput("tipo inválido".into()));
    }
    let rates = load_rates(db).await?;
    let sql = format!(
        "SELECT t.category_id AS category_id, c.name AS name, c.color AS color, c.icon AS icon,
                w.currency_code AS currency_code, SUM(t.amount_cents) AS sum_cents
         FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id
         LEFT JOIN transaction_categories c ON c.id = t.category_id
         WHERE w.user_id = ?1 AND t.kind = ?2 AND t.occurred_at >= {}
         GROUP BY t.category_id, w.currency_code",
        period_start_sql(a.period.as_deref())
    );
    let rows: Vec<SliceRow> = all(db, &sql, jsv![uid, a.kind]).await?;

    // Accumulate per category across currencies, converting each to MXN.
    let mut by_cat: HashMap<Option<i64>, CategorySlice> = HashMap::new();
    for r in rows {
        let mxn = rates
            .get(&r.currency_code)
            .map(|rate| to_mxn(r.sum_cents, *rate))
            .unwrap_or(0);
        let entry = by_cat
            .entry(r.category_id)
            .or_insert_with(|| CategorySlice {
                category_id: r.category_id,
                name: r.name.clone().unwrap_or_else(|| "Sin categoría".into()),
                color: r.color.clone(),
                icon: r.icon.clone(),
                mxn_cents: 0,
            });
        entry.mxn_cents += mxn;
    }
    let mut slices: Vec<CategorySlice> = by_cat.into_values().collect();
    slices.sort_by_key(|s| std::cmp::Reverse(s.mxn_cents));
    let total_mxn_cents = slices.iter().map(|s| s.mxn_cents).sum();
    Ok(CategoryBreakdown {
        total_mxn_cents,
        slices,
    })
}

// ---- period trends ----

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DailyFlow {
    pub day: String, // 'DD'
    pub income_mxn_cents: i64,
    pub expense_mxn_cents: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SpendingTrends {
    pub income_mxn_cents: i64,
    pub expense_mxn_cents: i64,
    pub income_prev_mxn_cents: i64,
    pub expense_prev_mxn_cents: i64,
    /// (current - previous) / previous in basis points; 0 when previous is 0.
    pub income_trend_bps: i64,
    pub expense_trend_bps: i64,
    pub daily: Vec<DailyFlow>,
}

fn trend_bps(cur: i64, prev: i64) -> i64 {
    if prev <= 0 {
        0
    } else {
        (((cur - prev) as i128 * 10_000) / prev as i128) as i64
    }
}

#[derive(Deserialize)]
struct MonthRow {
    currency_code: String,
    kind: String,
    cur: i64,
    prev: i64,
}

#[derive(Deserialize)]
struct DayRow {
    day: String,
    kind: String,
    currency_code: String,
    sum_cents: i64,
}

pub async fn get_spending_trends(db: &D1Database, uid: i64) -> AppResult<SpendingTrends> {
    let rates = load_rates(db).await?;

    // Current month vs previous month, split by kind, per currency.
    let month_rows: Vec<MonthRow> = all(
        db,
        "SELECT w.currency_code AS currency_code, t.kind AS kind,
            SUM(CASE WHEN t.occurred_at >= date('now','start of month')
                     THEN t.amount_cents ELSE 0 END) AS cur,
            SUM(CASE WHEN t.occurred_at >= date('now','start of month','-1 month')
                      AND t.occurred_at < date('now','start of month')
                     THEN t.amount_cents ELSE 0 END) AS prev
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind IN ('income','expense')
           AND t.occurred_at >= date('now','start of month','-1 month')
         GROUP BY w.currency_code, t.kind",
        jsv![uid],
    )
    .await?;

    let (mut income, mut expense, mut income_prev, mut expense_prev) = (0i64, 0i64, 0i64, 0i64);
    for r in month_rows {
        let rate = rates.get(&r.currency_code).copied().unwrap_or(0);
        let cur = to_mxn(r.cur, rate);
        let prev = to_mxn(r.prev, rate);
        if r.kind == "income" {
            income += cur;
            income_prev += prev;
        } else {
            expense += cur;
            expense_prev += prev;
        }
    }

    // Daily series for the current month (for the income/expense bar widget).
    let day_rows: Vec<DayRow> = all(
        db,
        "SELECT strftime('%d', t.occurred_at) AS day, t.kind AS kind,
                w.currency_code AS currency_code, SUM(t.amount_cents) AS sum_cents
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind IN ('income','expense')
           AND t.occurred_at >= date('now','start of month')
         GROUP BY day, t.kind, w.currency_code",
        jsv![uid],
    )
    .await?;
    let mut daily_map: HashMap<String, DailyFlow> = HashMap::new();
    for r in day_rows {
        let mxn = to_mxn(
            r.sum_cents,
            rates.get(&r.currency_code).copied().unwrap_or(0),
        );
        let entry = daily_map.entry(r.day.clone()).or_insert_with(|| DailyFlow {
            day: r.day,
            income_mxn_cents: 0,
            expense_mxn_cents: 0,
        });
        if r.kind == "income" {
            entry.income_mxn_cents += mxn;
        } else {
            entry.expense_mxn_cents += mxn;
        }
    }
    let mut daily: Vec<DailyFlow> = daily_map.into_values().collect();
    daily.sort_by(|a, b| a.day.cmp(&b.day));

    Ok(SpendingTrends {
        income_mxn_cents: income,
        expense_mxn_cents: expense,
        income_prev_mxn_cents: income_prev,
        expense_prev_mxn_cents: expense_prev,
        income_trend_bps: trend_bps(income, income_prev),
        expense_trend_bps: trend_bps(expense, expense_prev),
        daily,
    })
}
