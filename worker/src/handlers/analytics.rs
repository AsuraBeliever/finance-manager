//! Spending analytics: category breakdown + period trends. Every amount is
//! converted to MXN in Rust (i128 intermediate), reusing dashboard.rs helpers;
//! the frontend only divides for the % labels and formats.
//!
//! Refunds (income rows with `is_refund = 1`, migration 0040) are never
//! income: they net against the spending of their expense category in the
//! period they land, floored at zero per category
//! (`finanzas_core::refund::net_spending` / `net_spending_total`), so every
//! total equals the sum of the per-category figures.

use std::collections::HashMap;

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::models::Transaction;
use finanzas_core::period::{resolve_period, Period, ResolvedPeriod};
use finanzas_core::refund::{net_spending, net_spending_total};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{load_rates, to_mxn};
use crate::db::{all, today_mx, ToJs};
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
    /// Expenses (or income) of the slice in its wallet currency.
    sum_cents: i64,
    /// Refunds landed against the slice; always 0 for the income breakdown.
    refund_cents: i64,
}

/// SQL predicate selecting the rows of one analytics `kind` from `transactions t`:
/// income without refunds, or expenses together with the refunds that net
/// against them. Picks a fixed literal, never user input.
fn kind_clause(kind: &str) -> AppResult<&'static str> {
    match kind {
        "income" => Ok("t.kind = 'income' AND t.is_refund = 0"),
        "expense" => Ok("(t.kind = 'expense' OR t.is_refund = 1)"),
        _ => Err(AppError::InvalidInput("tipo inválido".into())),
    }
}

pub async fn get_category_breakdown(
    db: &D1Database,
    uid: i64,
    a: BreakdownArgs,
) -> AppResult<CategoryBreakdown> {
    let clause = kind_clause(&a.kind)?;
    let rates = load_rates(db, uid).await?;
    let (start, end, _, _) = window_bounds(&resolve_period(&a.period, today_mx()));
    let rows: Vec<SliceRow> = all(
        db,
        &format!(
            "SELECT t.category_id AS category_id, c.name AS name, c.color AS color, c.icon AS icon,
                    w.currency_code AS currency_code,
                    SUM(CASE WHEN t.is_refund = 0 THEN t.amount_cents ELSE 0 END) AS sum_cents,
                    SUM(CASE WHEN t.is_refund = 1 THEN t.amount_cents ELSE 0 END) AS refund_cents
             FROM transactions t
             JOIN wallets w ON w.id = t.wallet_id
             LEFT JOIN transaction_categories c ON c.id = t.category_id
             WHERE w.user_id = ?1 AND {clause}
               AND t.occurred_at >= ?2 AND t.occurred_at < ?3
             GROUP BY t.category_id, w.currency_code"
        ),
        jsv![uid, start, end],
    )
    .await?;

    // Accumulate per category across currencies, converting each to MXN; the
    // refunds of a category are kept apart and netted once all are summed.
    let mut by_cat: HashMap<Option<i64>, (CategorySlice, i64)> = HashMap::new();
    for r in rows {
        let rate = rates.get(&r.currency_code).copied();
        let mxn = rate.map(|rate| to_mxn(r.sum_cents, rate)).unwrap_or(0);
        let refund_mxn = rate.map(|rate| to_mxn(r.refund_cents, rate)).unwrap_or(0);
        let entry = by_cat.entry(r.category_id).or_insert_with(|| {
            (
                CategorySlice {
                    category_id: r.category_id,
                    name: r.name.clone().unwrap_or_else(|| "Sin categoría".into()),
                    color: r.color.clone(),
                    icon: r.icon.clone(),
                    mxn_cents: 0,
                },
                0,
            )
        });
        entry.0.mxn_cents += mxn;
        entry.1 += refund_mxn;
    }
    // A category whose refunds cover all its spending in the window has no
    // slice to draw.
    let by_cat: Vec<CategorySlice> = by_cat
        .into_values()
        .map(|(mut slice, refund)| {
            slice.mxn_cents = net_spending(slice.mxn_cents, refund);
            slice
        })
        .filter(|s| s.mxn_cents > 0)
        .collect();
    let mut slices = by_cat;
    slices.sort_by_key(|s| std::cmp::Reverse(s.mxn_cents));
    let total_mxn_cents = slices.iter().map(|s| s.mxn_cents).sum();
    Ok(CategoryBreakdown {
        total_mxn_cents,
        slices,
    })
}

// ---- per-category drill-down ----

/// The transactions behind one slice of the breakdown: same kind and window
/// (an expense slice also lists the refunds that netted against it),
/// filtered to a single category (`categoryId = null` means the "Sin categoría"
/// slice, i.e. rows with no category). Amounts stay in their wallet's currency
/// (the row renders its own currency), unlike the MXN-normalized slice totals.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CategoryTxArgs {
    pub kind: String, // 'income' | 'expense'
    #[serde(default)]
    pub period: Period,
    #[serde(default)]
    pub category_id: Option<i64>,
}

#[derive(Deserialize)]
struct CatTxRow {
    id: i64,
    wallet_id: i64,
    wallet_name: String,
    kind: String,
    amount_cents: i64,
    category_id: Option<i64>,
    category_name: Option<String>,
    description: Option<String>,
    occurred_at: String,
    occurred_time: Option<String>,
    created_at: String,
    refund_of_id: Option<i64>,
    refunded_cents: i64,
}

pub async fn get_category_transactions(
    db: &D1Database,
    uid: i64,
    a: CategoryTxArgs,
) -> AppResult<Vec<Transaction>> {
    let clause = kind_clause(&a.kind)?;
    let (start, end, _, _) = window_bounds(&resolve_period(&a.period, today_mx()));
    let mut params = jsv![uid, start, end];
    // Match the exact slice: a real category by id, or the NULL bucket.
    let cat_clause = match a.category_id {
        Some(cid) => {
            params.push(cid.to_js());
            "t.category_id = ?4"
        }
        None => "t.category_id IS NULL",
    };
    let sql = format!(
        "SELECT t.id, t.wallet_id, w.name AS wallet_name,
                CASE WHEN t.is_refund = 1 THEN 'refund' ELSE t.kind END AS kind,
                t.amount_cents, t.category_id, c.name AS category_name,
                t.description, t.occurred_at, t.occurred_time, t.created_at,
                t.refund_of_id,
                CASE WHEN t.kind = 'expense'
                     THEN COALESCE((SELECT SUM(r.amount_cents) FROM transactions r
                                    WHERE r.refund_of_id = t.id), 0)
                     ELSE 0 END AS refunded_cents
         FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id
         LEFT JOIN transaction_categories c ON c.id = t.category_id
         WHERE w.user_id = ?1 AND {clause}
           AND t.occurred_at >= ?2 AND t.occurred_at < ?3
           AND {cat_clause}
         ORDER BY t.occurred_at DESC,
                  COALESCE(t.occurred_time,
                           strftime('%H:%M', datetime(t.created_at, '-6 hours'))) DESC,
                  t.id DESC"
    );
    let rows: Vec<CatTxRow> = all(db, &sql, params).await?;
    Ok(rows
        .into_iter()
        .map(|r| Transaction {
            refundable_cents: super::transactions::refundable(
                &r.kind,
                r.amount_cents,
                r.refunded_cents,
            ),
            id: r.id,
            wallet_id: r.wallet_id,
            wallet_name: r.wallet_name,
            kind: r.kind,
            amount_cents: r.amount_cents,
            category_id: r.category_id,
            category_name: r.category_name,
            transfer_group_id: None,
            description: r.description,
            occurred_at: r.occurred_at,
            occurred_time: r.occurred_time,
            created_at: r.created_at,
            refund_of_id: r.refund_of_id,
            refunded_cents: r.refunded_cents,
        })
        .collect())
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

/// (expense, refund) in MXN per category (None = uncategorized).
type SpendByCategory = HashMap<Option<i64>, (i64, i64)>;

/// Splits flow rows three ways: income, expense, and refunds (stored as income
/// with `is_refund = 1`), which net into expense instead of counting as income.
const FLOW_KIND: &str = "CASE WHEN t.is_refund = 1 THEN 'refund' ELSE t.kind END";

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
    category_id: Option<i64>,
    kind: String,
    cur: i64,
    prev: i64,
}

#[derive(Deserialize)]
struct BucketRow {
    bucket: String,
    category_id: Option<i64>,
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
        &format!(
            "SELECT w.currency_code AS currency_code, t.category_id AS category_id,
                {FLOW_KIND} AS kind,
            SUM(CASE WHEN t.occurred_at >= ?2 AND t.occurred_at < ?3
                     THEN t.amount_cents ELSE 0 END) AS cur,
            SUM(CASE WHEN t.occurred_at >= ?4 AND t.occurred_at < ?5
                     THEN t.amount_cents ELSE 0 END) AS prev
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind IN ('income','expense')
           AND t.occurred_at >= ?4 AND t.occurred_at < ?3
         GROUP BY w.currency_code, t.category_id, 3"
        ),
        jsv![uid, start, end, prev_start, prev_end],
    )
    .await?;

    let mut income = 0i64;
    let mut income_prev = 0i64;
    // Per category: (expense, refund) for the current and the previous window.
    // Refunds net only against their own category (`net_spending_total`), so
    // these totals match the breakdown and the budgets.
    let mut cur_by_cat: HashMap<Option<i64>, (i64, i64)> = HashMap::new();
    let mut prev_by_cat: HashMap<Option<i64>, (i64, i64)> = HashMap::new();
    for r in totals_rows {
        let rate = rates.get(&r.currency_code).copied().unwrap_or(0);
        let cur = to_mxn(r.cur, rate);
        let prev = to_mxn(r.prev, rate);
        if r.kind == "income" {
            income += cur;
            income_prev += prev;
            continue;
        }
        let c = cur_by_cat.entry(r.category_id).or_insert((0, 0));
        let p = prev_by_cat.entry(r.category_id).or_insert((0, 0));
        if r.kind == "refund" {
            c.1 += cur;
            p.1 += prev;
        } else {
            c.0 += cur;
            p.0 += prev;
        }
    }
    let expense = net_spending_total(cur_by_cat.into_values());
    let expense_prev = net_spending_total(prev_by_cat.into_values());

    // Flow series over the selected window, bucketed by day or month.
    let fmt = match resolved.bucket {
        finanzas_core::period::Bucket::Day => "%Y-%m-%d",
        finanzas_core::period::Bucket::Month => "%Y-%m",
    };
    let sql = format!(
        "SELECT strftime('{fmt}', t.occurred_at) AS bucket, t.category_id AS category_id,
                {FLOW_KIND} AS kind,
                w.currency_code AS currency_code, SUM(t.amount_cents) AS sum_cents
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind IN ('income','expense')
           AND t.occurred_at >= ?2 AND t.occurred_at < ?3
         GROUP BY bucket, t.category_id, 3, w.currency_code"
    );
    let bucket_rows: Vec<BucketRow> = all(db, &sql, jsv![uid, start, end]).await?;
    // Per bucket: income, and (expense, refund) per category in MXN; each
    // category nets on its own, as in the totals above.
    let mut buckets_map: HashMap<String, (i64, SpendByCategory)> = HashMap::new();
    for r in bucket_rows {
        let mxn = to_mxn(
            r.sum_cents,
            rates.get(&r.currency_code).copied().unwrap_or(0),
        );
        let entry = buckets_map.entry(r.bucket).or_default();
        match r.kind.as_str() {
            "income" => entry.0 += mxn,
            "refund" => entry.1.entry(r.category_id).or_insert((0, 0)).1 += mxn,
            _ => entry.1.entry(r.category_id).or_insert((0, 0)).0 += mxn,
        }
    }
    let mut buckets: Vec<FlowBucket> = buckets_map
        .into_iter()
        .map(|(key, (income, by_cat))| FlowBucket {
            key,
            income_mxn_cents: income,
            expense_mxn_cents: net_spending_total(by_cat.into_values()),
        })
        .collect();
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
