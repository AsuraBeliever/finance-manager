//! Daily yield accrual for yield-bearing wallets (see
//! finanzas_core::wallet_yield). Run from the cron: for every wallet with an
//! active rate, post one 'income' (Intereses) transaction per payout period
//! that has fully elapsed, advancing `yield_last_paid_date` as it goes.
//!
//! Idempotent two ways: each posted period carries a deterministic
//! `client_id` ("yield:<wallet>:<period-end>") guarded by the unique index, and
//! `yield_last_paid_date` only moves forward — a re-run on the same UTC day is a
//! no-op.
//!
//! After the forward pass, `reconcile_one` re-derives the last
//! `RECONCILE_DAYS` days from scratch and pays the difference. That is what
//! rescues a movement registered **after** the cron already closed its day: the
//! cursor never walks backwards, so without this a back-dated deposit would
//! silently never earn the yield it was owed. Priced off `wallet_yield_rates`
//! (migration 0036) so past days keep their own rate instead of being repainted
//! with today's.

use chrono::{Duration, NaiveDate};
use finanzas_core::error::AppResult;
use finanzas_core::wallet_yield::{
    accrued_interest_scheduled, next_period_end, reconciliation_delta,
};
use serde::Deserialize;
use worker::D1Database;

use crate::db::{all, batch, first, stmt, today_mx};
use crate::jsv;

/// How far back each run re-checks. Comfortably covers a movement typed in days
/// late while keeping the pass at two extra queries per wallet.
const RECONCILE_DAYS: i64 = 30;

#[derive(Deserialize)]
struct YieldWalletRow {
    id: i64,
    yield_rate_bps: i64,
    yield_frequency: String,
    yield_last_paid_date: String,
}

#[derive(Deserialize)]
struct BalanceRow {
    bal: i64,
}

#[derive(Deserialize)]
struct TxRow {
    occurred_at: String,
    amount: i64,
}

#[derive(Deserialize)]
struct IdRow {
    id: i64,
}

#[derive(Deserialize)]
struct RateRow {
    effective_from: String,
    rate_bps: i64,
}

#[derive(Deserialize)]
struct SumRow {
    total: i64,
}

/// A wallet's rate history, oldest first. Empty when the wallet predates
/// migration 0036 and has never been edited since.
async fn rate_history(db: &D1Database, wallet_id: i64) -> AppResult<Vec<(NaiveDate, i64)>> {
    let rows: Vec<RateRow> = all(
        db,
        "SELECT effective_from, rate_bps FROM wallet_yield_rates
         WHERE wallet_id = ?1 ORDER BY effective_from",
        jsv![wallet_id],
    )
    .await?;
    Ok(rows
        .into_iter()
        .filter_map(|r| {
            NaiveDate::parse_from_str(&r.effective_from, "%Y-%m-%d")
                .ok()
                .map(|d| (d, r.rate_bps))
        })
        .collect())
}

/// Closing balance of `wallet_id` at end of `date` (inclusive): initial balance
/// plus the signed sum of every transaction up to and including that day.
/// Also used by handlers::credit for the statement balance at the cut.
pub async fn balance_as_of(db: &D1Database, wallet_id: i64, date: &str) -> AppResult<i64> {
    let row: Option<BalanceRow> = first(
        db,
        "SELECT w.initial_balance_cents + COALESCE((
                  SELECT SUM(CASE t.kind
                               WHEN 'income' THEN t.amount_cents
                               WHEN 'transfer_in' THEN t.amount_cents
                               ELSE -t.amount_cents END)
                  FROM transactions t
                  WHERE t.wallet_id = w.id AND t.occurred_at <= ?2), 0) AS bal
         FROM wallets w WHERE w.id = ?1",
        jsv![wallet_id, date],
    )
    .await?;
    Ok(row.map(|r| r.bal).unwrap_or(0))
}

/// Signed transactions in the half-open window `(start, end]`, **excluding our
/// own interest postings**: the accrual re-credits the interest it computes as
/// it walks, so feeding the already-posted rows back in would double-count them
/// over any window longer than one period.
async fn period_txns(
    db: &D1Database,
    wallet_id: i64,
    start: &str,
    end: &str,
) -> AppResult<Vec<(NaiveDate, i64)>> {
    let rows: Vec<TxRow> = all(
        db,
        "SELECT t.occurred_at,
                CASE t.kind
                  WHEN 'income' THEN t.amount_cents
                  WHEN 'transfer_in' THEN t.amount_cents
                  ELSE -t.amount_cents END AS amount
         FROM transactions t
         WHERE t.wallet_id = ?1 AND t.occurred_at > ?2 AND t.occurred_at <= ?3
           AND (t.client_id IS NULL OR t.client_id NOT LIKE 'yield%')",
        jsv![wallet_id, start, end],
    )
    .await?;
    Ok(rows
        .into_iter()
        .filter_map(|r| {
            NaiveDate::parse_from_str(&r.occurred_at, "%Y-%m-%d")
                .ok()
                .map(|d| (d, r.amount))
        })
        .collect())
}

/// Post any due interest for every yield-bearing wallet. Best-effort per
/// wallet: one wallet's failure is logged and never blocks the others.
pub async fn accrue_yield(db: &D1Database) -> AppResult<()> {
    let wallets: Vec<YieldWalletRow> = all(
        db,
        "SELECT id, yield_rate_bps, yield_frequency, yield_last_paid_date
         FROM wallets
         WHERE yield_rate_bps IS NOT NULL AND yield_rate_bps > 0
           AND yield_frequency IS NOT NULL AND yield_anchor_date IS NOT NULL
           AND yield_last_paid_date IS NOT NULL AND is_archived = 0",
        vec![],
    )
    .await?;
    if wallets.is_empty() {
        return Ok(());
    }

    // Seed 'Intereses' income category, so the posted entries read naturally and
    // localize via the frontend's seedName(). NULL if the user removed it.
    let interest_cat: Option<i64> = first::<IdRow>(
        db,
        "SELECT id FROM transaction_categories
         WHERE name = 'Intereses' AND kind = 'income' AND user_id IS NULL",
        vec![],
    )
    .await?
    .map(|r| r.id);

    let today = today_mx();
    for w in wallets {
        let rates = match rate_history(db, w.id).await {
            Ok(r) => r,
            Err(e) => {
                worker::console_warn!("yield rate history failed for wallet {}: {e}", w.id);
                continue;
            }
        };
        if let Err(e) = accrue_one(db, &w, &rates, interest_cat, today).await {
            worker::console_warn!("yield accrual failed for wallet {}: {e}", w.id);
        }
        if let Err(e) = reconcile_one(db, &w, &rates, interest_cat, today).await {
            worker::console_warn!("yield reconcile failed for wallet {}: {e}", w.id);
        }
    }
    Ok(())
}

/// The schedule to price the forward pass with: the recorded history, plus the
/// wallet's current rate covering anything older than the first record. Days
/// before migration 0036 have no record, and for the forward pass the current
/// rate is the only sane reading — refusing to price them would stop paying a
/// weekly wallet whose period opened before the table existed.
fn forward_schedule(rates: &[(NaiveDate, i64)], current_bps: i64) -> Vec<(NaiveDate, i64)> {
    let mut schedule = Vec::with_capacity(rates.len() + 1);
    schedule.push((NaiveDate::MIN, current_bps));
    schedule.extend_from_slice(rates);
    schedule
}

async fn accrue_one(
    db: &D1Database,
    w: &YieldWalletRow,
    rates: &[(NaiveDate, i64)],
    interest_cat: Option<i64>,
    today: NaiveDate,
) -> AppResult<()> {
    let schedule = forward_schedule(rates, w.yield_rate_bps);
    let mut last_paid =
        NaiveDate::parse_from_str(&w.yield_last_paid_date, "%Y-%m-%d").unwrap_or(today);

    // Cap the catch-up so a long-dormant wallet can't run unbounded if a cron
    // run was missed for a while; weekly over a year is still only ~53 periods,
    // and a daily one caps at 400 days per run — the next run picks up the rest.
    for _ in 0..400 {
        let Some(period_end) = next_period_end(&w.yield_frequency, last_paid) else {
            break;
        };
        if period_end > today {
            break;
        }
        let start = last_paid.to_string();
        let end = period_end.to_string();

        let start_balance = balance_as_of(db, w.id, &start).await?;
        let txns = period_txns(db, w.id, &start, &end).await?;
        let interest =
            accrued_interest_scheduled(start_balance, &txns, last_paid, period_end, &schedule);

        if interest > 0 {
            let client_id = format!("yield:{}:{}", w.id, end);
            let stmts = vec![
                stmt(
                    db,
                    "INSERT OR IGNORE INTO transactions
                       (wallet_id, kind, amount_cents, category_id, occurred_at, client_id)
                     VALUES (?1, 'income', ?2, ?3, ?4, ?5)",
                    jsv![w.id, interest, interest_cat, end, client_id],
                )?,
                stmt(
                    db,
                    "UPDATE wallets SET yield_last_paid_date = ?2 WHERE id = ?1",
                    jsv![w.id, end],
                )?,
            ];
            batch(db, stmts).await?;
        } else {
            crate::db::exec(
                db,
                "UPDATE wallets SET yield_last_paid_date = ?2 WHERE id = ?1",
                jsv![w.id, end],
            )
            .await?;
        }
        last_paid = period_end;
    }
    Ok(())
}

/// Sum of the interest we have already posted inside `(start, end]`, ignoring
/// `skip_client_id` so today's own correction never counts towards the total it
/// is being computed from.
async fn posted_yield(
    db: &D1Database,
    wallet_id: i64,
    start: &str,
    end: &str,
    skip_client_id: &str,
) -> AppResult<i64> {
    let row: Option<SumRow> = first(
        db,
        "SELECT COALESCE(SUM(CASE kind WHEN 'income' THEN amount_cents
                                       ELSE -amount_cents END), 0) AS total
         FROM transactions
         WHERE wallet_id = ?1 AND occurred_at > ?2 AND occurred_at <= ?3
           AND client_id LIKE 'yield%' AND client_id <> ?4",
        jsv![wallet_id, start, end, skip_client_id],
    )
    .await?;
    Ok(row.map(|r| r.total).unwrap_or(0))
}

/// Record the rate a wallet runs at from today on. Called whenever yield is
/// turned on or its rate edited; re-setting it the same day overwrites rather
/// than stacking, so the history stays one row per actual change.
///
/// Without this the reconciliation pass has nothing to price past days with —
/// see migration 0036.
pub async fn record_yield_rate(db: &D1Database, wallet_id: i64, rate_bps: i64) -> AppResult<()> {
    crate::db::exec(
        db,
        "INSERT INTO wallet_yield_rates (wallet_id, effective_from, rate_bps)
         VALUES (?1, ?2, ?3)
         ON CONFLICT(wallet_id, effective_from) DO UPDATE SET rate_bps = excluded.rate_bps",
        jsv![wallet_id, today_mx().to_string(), rate_bps],
    )
    .await?;
    Ok(())
}

/// Re-derive the last `RECONCILE_DAYS` of interest and settle the difference.
///
/// The forward pass can only ever look at periods it has not paid yet, so a
/// movement typed in after its day was closed — a deposit registered two days
/// late, an amount corrected, a transaction deleted — leaves the wallet
/// permanently off. Here the whole window is recomputed from the real
/// transactions at their historical rates and the gap is posted as one
/// adjustment dated today, which is also where it starts compounding.
///
/// Bounded to days we can actually price: `wallet_yield_rates` is the floor, so
/// a wallet whose rate changed before the table existed is left alone rather
/// than repainted at the current rate.
async fn reconcile_one(
    db: &D1Database,
    w: &YieldWalletRow,
    rates: &[(NaiveDate, i64)],
    interest_cat: Option<i64>,
    today: NaiveDate,
) -> AppResult<()> {
    let Some(&(earliest, _)) = rates.first() else {
        return Ok(()); // no priced history yet — nothing safe to recompute
    };
    let last_paid = NaiveDate::parse_from_str(&w.yield_last_paid_date, "%Y-%m-%d").unwrap_or(today);
    let start = earliest.max(last_paid - Duration::days(RECONCILE_DAYS));
    if start >= last_paid {
        return Ok(());
    }

    let (start_s, end_s) = (start.to_string(), last_paid.to_string());
    let client_id = format!("yield-fix:{}:{}", w.id, today);

    // Both sides must line up on the same window — see reconciliation_delta:
    // balance AT start, external movements and postings in (start, last_paid].
    let start_balance = balance_as_of(db, w.id, &start_s).await?;
    let txns = period_txns(db, w.id, &start_s, &end_s).await?;
    let posted = posted_yield(db, w.id, &start_s, &end_s, &client_id).await?;
    let diff = reconciliation_delta(start_balance, &txns, posted, start, last_paid, rates);

    // Rewrite today's adjustment from scratch instead of adding to it: the cron
    // runs three times a day, and each run must land on the same number rather
    // than stack corrections on top of each other.
    let mut stmts = vec![stmt(
        db,
        "DELETE FROM transactions WHERE client_id = ?1",
        jsv![client_id],
    )?];
    if diff != 0 {
        let (kind, amount) = if diff > 0 {
            ("income", diff)
        } else {
            ("expense", -diff)
        };
        worker::console_log!(
            "yield reconcile wallet {}: {} .. {} posted {posted} -> {kind} {amount}",
            w.id,
            start_s,
            end_s
        );
        stmts.push(stmt(
            db,
            "INSERT INTO transactions
               (wallet_id, kind, amount_cents, category_id, occurred_at, client_id, description)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'Ajuste de rendimiento')",
            jsv![
                w.id,
                kind,
                amount,
                interest_cat,
                today.to_string(),
                client_id
            ],
        )?);
    }
    batch(db, stmts).await?;
    Ok(())
}
