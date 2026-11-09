//! Keeps `investment_yield_days` (migration 0043) current: the day-by-day
//! yield of every investment, computed by `finanzas_core::investments::
//! daily_yield`. Read by the transactions list (as read-only «Rendimiento»
//! rows) and by the income figures of the dashboard and analytics.
//!
//! - **On every write** to an investment (create/edit/close, movements,
//!   snapshots) `refresh` rebuilds a modeled history from scratch, so an edit
//!   never leaves stale days behind.
//! - **From the daily cron** `accrue_all` only extends each open history up to
//!   today — the cheap step, run for every user under the free tier's CPU cap.
//!
//! Observed histories (crypto, BONDDIA títulos) can't be rebuilt: there is no
//! past price. They get one row per day as it happens, starting from a zero
//! baseline (see `daily_yield::observed_day`).

use chrono::{Duration, NaiveDate};
use finanzas_core::error::AppResult;
use finanzas_core::investments::daily_yield::{
    daily_gains, full_history, gain_at, history_end, observed_day, YieldDay,
};
use finanzas_core::investments::{find, parse_start_date, CalcContext, InvestmentCalculator};
use finanzas_core::models::Investment;
use finanzas_core::period::{resolve_period, Period};
use serde::{Deserialize, Serialize};
use worker::console_error;
use worker::D1Database;

use super::investments::{fetch_investment, load_calc_context, open_investments_all};
use crate::db::{all, batch, first, stmt, today_mx};
use crate::jsv;

/// Most days one cron run adds to one history. The first run after deploying
/// 0043 builds every history from scratch; capped like this it spreads over a
/// few runs (three a day) instead of risking the CPU limit in one, and what
/// each run computed is stored before the next investment starts.
const MAX_DAYS_PER_RUN: i64 = 370;

/// Rows per INSERT. Each goes as one JSON parameter unpacked by `json_each`,
/// so a long history is a handful of statements, not one per day.
const ROWS_PER_INSERT: usize = 400;

const UPSERT: &str = "INSERT OR REPLACE INTO investment_yield_days
       (investment_id, day, gain_cents, total_gain_cents)
     SELECT ?1, json_extract(value, '$[0]'), json_extract(value, '$[1]'),
            json_extract(value, '$[2]')
     FROM json_each(?2)";

/// `[["YYYY-MM-DD", gain, total], …]` for `json_each`.
fn rows_json(days: &[YieldDay]) -> String {
    let rows: Vec<String> = days
        .iter()
        .map(|y| format!("[\"{}\",{},{}]", y.day, y.gain_cents, y.total_gain_cents))
        .collect();
    format!("[{}]", rows.join(","))
}

fn upserts(
    db: &D1Database,
    id: i64,
    days: &[YieldDay],
) -> AppResult<Vec<worker::D1PreparedStatement>> {
    days.chunks(ROWS_PER_INSERT)
        .map(|chunk| stmt(db, UPSERT, jsv![id, rows_json(chunk)]))
        .collect()
}

/// Rebuild one investment's history after a write. Modeled: replaced whole,
/// in one batch (so a reader never sees it half gone). Observed: today's row
/// recomputed, the past kept.
pub async fn refresh(db: &D1Database, inv: &Investment) -> AppResult<()> {
    let calc = find(&inv.calculator)?;
    let ctx = load_calc_context(db, inv).await?;
    let today = today_mx();
    if !calc.values_past_days(inv) {
        return record_observed(db, calc, inv, &ctx, today).await;
    }
    rebuild(db, calc, inv, &ctx, today).await
}

/// Replace a modeled history whole, in one batch (a reader never sees it half
/// gone).
async fn rebuild(
    db: &D1Database,
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    ctx: &CalcContext,
    today: NaiveDate,
) -> AppResult<()> {
    let days = full_history(calc, inv, ctx, today)?;
    let mut stmts = vec![stmt(
        db,
        "DELETE FROM investment_yield_days WHERE investment_id = ?1",
        jsv![inv.id],
    )?];
    stmts.extend(upserts(db, inv.id, &days)?);
    batch(db, stmts).await?;
    Ok(())
}

/// `refresh`, logging instead of failing: the write the user asked for has
/// already happened, and the history is derived data — the cron rebuilds a
/// modeled history whose last total no longer matches the gain.
pub async fn refresh_quietly(db: &D1Database, inv: &Investment) {
    if let Err(e) = refresh(db, inv).await {
        console_error!("yield history refresh of investment {} failed: {e}", inv.id);
    }
}

#[derive(Deserialize)]
struct LastRow {
    day: String,
    total_gain_cents: i64,
}

/// Today's entry of an observed history, continuing from the last day before
/// today (re-running on the same day just rewrites today's row).
async fn record_observed(
    db: &D1Database,
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    ctx: &CalcContext,
    today: NaiveDate,
) -> AppResult<()> {
    let prev: Option<LastRow> = first(
        db,
        "SELECT day, total_gain_cents FROM investment_yield_days
         WHERE investment_id = ?1 AND day < ?2 ORDER BY day DESC LIMIT 1",
        jsv![inv.id, today.to_string()],
    )
    .await?;
    let total = gain_at(calc, inv, ctx, today)?;
    let entry = observed_day(prev.map(|p| p.total_gain_cents), today, total);
    batch(db, upserts(db, inv.id, &[entry])?).await?;
    Ok(())
}

/// Extend a modeled history from its last stored day towards `history_end`
/// (at most `MAX_DAYS_PER_RUN`). A history whose last stored total no longer
/// equals the gain on that day was left stale by a write that didn't refresh
/// it — a wallet deleted with its movement legs, a failed refresh — and is
/// rebuilt from scratch instead.
async fn extend_modeled(
    db: &D1Database,
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    ctx: &CalcContext,
    today: NaiveDate,
) -> AppResult<()> {
    let last: Option<LastRow> = first(
        db,
        "SELECT day, total_gain_cents FROM investment_yield_days
         WHERE investment_id = ?1 ORDER BY day DESC LIMIT 1",
        jsv![inv.id],
    )
    .await?;
    // Nothing stored yet (a new deploy, or an investment from before 0043):
    // this builds the whole history once.
    let after = match last {
        Some(row) => {
            let day = NaiveDate::parse_from_str(&row.day, "%Y-%m-%d").unwrap_or(today);
            if gain_at(calc, inv, ctx, day)? != row.total_gain_cents {
                return rebuild(db, calc, inv, ctx, today).await;
            }
            day
        }
        None => parse_start_date(inv)? - Duration::days(1),
    };
    let end = history_end(calc, inv, today);
    let through = end.min(after + Duration::days(MAX_DAYS_PER_RUN));
    if after >= through {
        return Ok(());
    }
    let mut days = daily_gains(calc, inv, ctx, after, through)?;
    // Days that earned nothing aren't stored, so a cut-short window can end on
    // no row at all — and the next run would start from the same day and walk
    // the same empty window forever (a manual position with one far snapshot).
    // A zero-gain checkpoint on the window's last day carries the walk forward;
    // every reader skips or just adds zero rows.
    if through < end && days.last().map(|y| y.day) != Some(through) {
        days.push(YieldDay {
            day: through,
            gain_cents: 0,
            total_gain_cents: gain_at(calc, inv, ctx, through)?,
        });
    }
    if !days.is_empty() {
        batch(db, upserts(db, inv.id, &days)?).await?;
    }
    Ok(())
}

/// Daily cron: bring every open investment's history up to today. One
/// investment failing is logged and skipped, never stops the rest.
pub async fn accrue_all(db: &D1Database) -> AppResult<()> {
    let today = today_mx();
    for inv in open_investments_all(db).await? {
        let result = async {
            let calc = find(&inv.calculator)?;
            let ctx = load_calc_context(db, &inv).await?;
            if calc.values_past_days(&inv) {
                extend_modeled(db, calc, &inv, &ctx, today).await
            } else {
                record_observed(db, calc, &inv, &ctx, today).await
            }
        }
        .await;
        if let Err(e) = result {
            console_error!("yield accrual of investment {} failed: {e}", inv.id);
        }
    }
    Ok(())
}

// ---- reading one investment's history ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentYieldArgs {
    pub investment_id: i64,
    /// Most recent days to return; the detail page grows it with «Mostrar más».
    #[serde(default)]
    pub limit: Option<i64>,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct YieldDayOut {
    pub day: String,
    // Read straight from D1, whose columns are snake_case.
    #[serde(alias = "gain_cents")]
    pub gain_cents: i64,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct YieldMonthOut {
    /// 'YYYY-MM'
    pub month: String,
    // Read straight from D1, whose columns are snake_case.
    #[serde(alias = "gain_cents")]
    pub gain_cents: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentYield {
    /// In the investment's currency.
    pub currency_code: String,
    /// What it earned per month, newest first — every month it earned.
    pub months: Vec<YieldMonthOut>,
    /// What it earned per day, newest first, up to `limit`.
    pub days: Vec<YieldDayOut>,
    /// More days exist beyond `limit`.
    pub has_more: bool,
    /// Recorded day by day as it happens (crypto, BONDDIA títulos): there is
    /// no history from before the first record.
    pub observed: bool,
}

/// Day-by-day and month-by-month yield of one investment, for its detail page.
pub async fn get_investment_yield(
    db: &D1Database,
    uid: i64,
    a: InvestmentYieldArgs,
) -> AppResult<InvestmentYield> {
    let inv = fetch_investment(db, uid, a.investment_id).await?;
    let observed = !find(&inv.calculator)?.values_past_days(&inv);
    let limit = a.limit.unwrap_or(30).clamp(1, 5_000);
    let months: Vec<YieldMonthOut> = all(
        db,
        "SELECT substr(day, 1, 7) AS month, SUM(gain_cents) AS gain_cents
         FROM investment_yield_days WHERE investment_id = ?1
         GROUP BY month HAVING SUM(gain_cents) <> 0 ORDER BY month DESC",
        jsv![a.investment_id],
    )
    .await?;
    // One past the limit, only to learn whether there is more.
    let mut days: Vec<YieldDayOut> = all(
        db,
        "SELECT day, gain_cents FROM investment_yield_days
         WHERE investment_id = ?1 AND gain_cents <> 0
         ORDER BY day DESC LIMIT ?2",
        jsv![a.investment_id, limit + 1],
    )
    .await?;
    let has_more = days.len() as i64 > limit;
    days.truncate(limit as usize);
    Ok(InvestmentYield {
        currency_code: inv.currency_code,
        months,
        days,
        has_more,
        observed,
    })
}

// ---- the detail page's combined table (FIN-12) ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentActivityArgs {
    pub investment_id: i64,
    /// Date window, resolved like the transactions list's. Absent = every date.
    #[serde(default)]
    pub period: Option<Period>,
    /// `false` leaves the daily yield out of the rows and the totals.
    #[serde(default)]
    pub include_yield: Option<bool>,
    /// Newest rows to return; the page grows it with «Mostrar más».
    #[serde(default)]
    pub limit: Option<i64>,
}

/// One line of the table: a deposit or withdrawal (with its movement id, so
/// it can be edited or deleted) or one day's yield (read-only, no id).
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ActivityRow {
    #[serde(alias = "movement_id")]
    pub movement_id: Option<i64>,
    /// 'deposit' | 'withdrawal' | 'yield'
    pub kind: String,
    #[serde(alias = "amount_cents")]
    pub amount_cents: i64,
    #[serde(alias = "occurred_at")]
    pub occurred_at: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentActivity {
    /// In the investment's currency, like every amount here.
    pub currency_code: String,
    /// Everything the filter selects, not just the rows returned.
    pub yield_cents: i64,
    pub deposit_cents: i64,
    pub withdrawal_cents: i64,
    /// How many rows the filter selects in all.
    pub count: i64,
    pub rows: Vec<ActivityRow>,
    /// More rows exist beyond `limit`.
    pub has_more: bool,
    /// Recorded day by day as it happens (crypto, BONDDIA títulos): there is
    /// no yield from before the first record, and the page says so.
    pub observed: bool,
}

#[derive(Deserialize)]
struct ActivityTotalsRow {
    kind: String,
    sum_cents: i64,
    n: i64,
}

/// Deposits, withdrawals and daily yield of one investment in one table,
/// newest first, with the totals of the whole filtered window. Yield rows are
/// the stored daily history (days that earned nothing are skipped, as in the
/// transactions list); scoped to the caller through `fetch_investment`.
pub async fn list_investment_activity(
    db: &D1Database,
    uid: i64,
    a: InvestmentActivityArgs,
) -> AppResult<InvestmentActivity> {
    let inv = fetch_investment(db, uid, a.investment_id).await?;
    let observed = !find(&inv.calculator)?.values_past_days(&inv);
    let (start, end) = match &a.period {
        Some(p) => {
            let r = resolve_period(p, today_mx());
            (r.start.to_string(), r.end.to_string())
        }
        // Every date: a window wide enough for any business date.
        None => ("0000-01-01".to_string(), "9999-12-31".to_string()),
    };
    let with_yield = a.include_yield.unwrap_or(true);
    let limit = a.limit.unwrap_or(100).clamp(1, 10_000);
    // ?1 investment, ?2/?3 window, ?4 whether yield rows count.
    let source = "SELECT id AS movement_id, kind, amount_cents, occurred_at, 0 AS ord
         FROM investment_movements
         WHERE investment_id = ?1 AND occurred_at >= ?2 AND occurred_at < ?3
         UNION ALL
         SELECT NULL, 'yield', gain_cents, day, 1
         FROM investment_yield_days
         WHERE investment_id = ?1 AND ?4 = 1 AND gain_cents <> 0
           AND day >= ?2 AND day < ?3";
    let totals: Vec<ActivityTotalsRow> = all(
        db,
        &format!(
            "SELECT kind, SUM(amount_cents) AS sum_cents, COUNT(*) AS n
             FROM ({source}) GROUP BY kind"
        ),
        jsv![inv.id, start.clone(), end.clone(), with_yield as i64],
    )
    .await?;
    // A movement sorts above that day's yield; one past the limit to learn
    // whether there is more.
    let mut rows: Vec<ActivityRow> = all(
        db,
        &format!(
            "SELECT movement_id, kind, amount_cents, occurred_at FROM ({source})
             ORDER BY occurred_at DESC, ord, movement_id DESC LIMIT ?5"
        ),
        jsv![inv.id, start, end, with_yield as i64, limit + 1],
    )
    .await?;
    let has_more = rows.len() as i64 > limit;
    rows.truncate(limit as usize);
    let total = |k: &str| {
        totals
            .iter()
            .filter(|t| t.kind == k)
            .map(|t| t.sum_cents)
            .sum()
    };
    Ok(InvestmentActivity {
        currency_code: inv.currency_code,
        yield_cents: total("yield"),
        deposit_cents: total("deposit"),
        withdrawal_cents: total("withdrawal"),
        count: totals.iter().map(|t| t.n).sum(),
        rows,
        has_more,
        observed,
    })
}
