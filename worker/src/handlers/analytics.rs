//! Spending analytics: category breakdown + period trends. Every amount is
//! converted to MXN in Rust (i128 intermediate), reusing dashboard.rs helpers;
//! the frontend only divides for the % labels and formats.

use std::collections::HashMap;

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::period::{resolve_period, Period, ResolvedPeriod};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{load_rates, to_mxn};
use crate::db::{all, today_mx};
use crate::jsv;

/// SQL date literals (`YYYY-MM-DD`) for a resolved window. Bound as parameters,
/// never interpolated, so user input never reaches the query text.
fn window_bounds(r: &ResolvedPeriod) -> (String, String, String, String) {
    (
        r.start.to_string(),
        r.end.to_string(),
        r.prev_start.to_string(),
        r.prev_end.to_string(),
    )
}

// ---- category breakdown ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BreakdownArgs {
    pub kind: String, // 'income' | 'expense'
    #[serde(default)]
    pub period: Period,
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
    let rates = load_rates(db, uid).await?;
    let (start, end, _, _) = window_bounds(&resolve_period(&a.period, today_mx()));
    let rows: Vec<SliceRow> = all(
        db,
        "SELECT t.category_id AS category_id, c.name AS name, c.color AS color, c.icon AS icon,
                w.currency_code AS currency_code, SUM(t.amount_cents) AS sum_cents
         FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id
         LEFT JOIN transaction_categories c ON c.id = t.category_id
         WHERE w.user_id = ?1 AND t.kind = ?2
           AND t.occurred_at >= ?3 AND t.occurred_at < ?4
         GROUP BY t.category_id, w.currency_code",
        jsv![uid, a.kind, start, end],
    )
    .await?;

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

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct TrendsArgs {
    #[serde(default)]
    pub period: Period,
}

/// One bar of the flow chart. `key` is the bucket identifier: `YYYY-MM-DD` for
/// daily windows, `YYYY-MM` for monthly ones (the `bucketUnit` says which).
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FlowBucket {
    pub key: String,
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
    /// 'day' | 'month' — how `buckets` are grouped, so the chart formats labels.
    pub bucket_unit: String,
    pub buckets: Vec<FlowBucket>,
}

fn trend_bps(cur: i64, prev: i64) -> i64 {
    if prev <= 0 {
        0
    } else {
        (((cur - prev) as i128 * 10_000) / prev as i128) as i64
    }
}

#[derive(Deserialize)]
struct TotalsRow {
    currency_code: String,
    kind: String,
    cur: i64,
    prev: i64,
}

#[derive(Deserialize)]
struct BucketRow {
    bucket: String,
    kind: String,
    currency_code: String,
    sum_cents: i64,
}

pub async fn get_spending_trends(
    db: &D1Database,
    uid: i64,
    a: TrendsArgs,
) -> AppResult<SpendingTrends> {
    let rates = load_rates(db, uid).await?;
    let resolved = resolve_period(&a.period, today_mx());
    let (start, end, prev_start, prev_end) = window_bounds(&resolved);

    // Selected window vs the comparable previous one, split by kind, per
    // currency. Both windows are scanned in one pass via conditional sums.
    let totals_rows: Vec<TotalsRow> = all(
        db,
        "SELECT w.currency_code AS currency_code, t.kind AS kind,
            SUM(CASE WHEN t.occurred_at >= ?2 AND t.occurred_at < ?3
                     THEN t.amount_cents ELSE 0 END) AS cur,
            SUM(CASE WHEN t.occurred_at >= ?4 AND t.occurred_at < ?5
                     THEN t.amount_cents ELSE 0 END) AS prev
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind IN ('income','expense')
           AND t.occurred_at >= ?4 AND t.occurred_at < ?3
         GROUP BY w.currency_code, t.kind",
        jsv![uid, start, end, prev_start, prev_end],
    )
    .await?;

    let (mut income, mut expense, mut income_prev, mut expense_prev) = (0i64, 0i64, 0i64, 0i64);
    for r in totals_rows {
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

    // Flow series over the selected window, bucketed by day or month.
    let fmt = match resolved.bucket {
        finanzas_core::period::Bucket::Day => "%Y-%m-%d",
        finanzas_core::period::Bucket::Month => "%Y-%m",
    };
    let sql = format!(
        "SELECT strftime('{fmt}', t.occurred_at) AS bucket, t.kind AS kind,
                w.currency_code AS currency_code, SUM(t.amount_cents) AS sum_cents
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind IN ('income','expense')
           AND t.occurred_at >= ?2 AND t.occurred_at < ?3
         GROUP BY bucket, t.kind, w.currency_code"
    );
    let bucket_rows: Vec<BucketRow> = all(db, &sql, jsv![uid, start, end]).await?;
    let mut buckets_map: HashMap<String, FlowBucket> = HashMap::new();
    for r in bucket_rows {
        let mxn = to_mxn(
            r.sum_cents,
            rates.get(&r.currency_code).copied().unwrap_or(0),
        );
        let entry = buckets_map
            .entry(r.bucket.clone())
            .or_insert_with(|| FlowBucket {
                key: r.bucket,
                income_mxn_cents: 0,
                expense_mxn_cents: 0,
            });
        if r.kind == "income" {
            entry.income_mxn_cents += mxn;
        } else {
            entry.expense_mxn_cents += mxn;
        }
    }
    let mut buckets: Vec<FlowBucket> = buckets_map.into_values().collect();
    buckets.sort_by(|a, b| a.key.cmp(&b.key));

    Ok(SpendingTrends {
        income_mxn_cents: income,
        expense_mxn_cents: expense,
        income_prev_mxn_cents: income_prev,
        expense_prev_mxn_cents: expense_prev,
        income_trend_bps: trend_bps(income, income_prev),
        expense_trend_bps: trend_bps(expense, expense_prev),
        bucket_unit: resolved.bucket.as_str().to_string(),
        buckets,
    })
}
