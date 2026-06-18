//! Daily yield accrual for yield-bearing wallets (see
//! finanzas_core::wallet_yield). Run from the cron: for every wallet with an
//! active rate, post one 'income' (Intereses) transaction per payout period
//! that has fully elapsed, advancing `yield_last_paid_date` as it goes.
//!
//! Idempotent two ways: each posted period carries a deterministic
//! `client_id` ("yield:<wallet>:<period-end>") guarded by the unique index, and
//! `yield_last_paid_date` only moves forward — a re-run on the same UTC day is a
//! no-op.

use chrono::NaiveDate;
use finanzas_core::error::AppResult;
use finanzas_core::wallet_yield::{accrued_interest, next_period_end};
use serde::Deserialize;
use worker::D1Database;

use crate::db::{all, batch, first, stmt, today_mx};
use crate::jsv;

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

/// Closing balance of `wallet_id` at end of `date` (inclusive): initial balance
/// plus the signed sum of every transaction up to and including that day.
async fn balance_as_of(db: &D1Database, wallet_id: i64, date: &str) -> AppResult<i64> {
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

/// Signed transactions in the half-open window `(start, end]`.
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
         WHERE t.wallet_id = ?1 AND t.occurred_at > ?2 AND t.occurred_at <= ?3",
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
        if let Err(e) = accrue_one(db, &w, interest_cat, today).await {
            worker::console_warn!("yield accrual failed for wallet {}: {e}", w.id);
        }
    }
    Ok(())
}

async fn accrue_one(
    db: &D1Database,
    w: &YieldWalletRow,
    interest_cat: Option<i64>,
    today: NaiveDate,
) -> AppResult<()> {
    let mut last_paid =
        NaiveDate::parse_from_str(&w.yield_last_paid_date, "%Y-%m-%d").unwrap_or(today);

    // Cap the catch-up so a long-dormant wallet can't run unbounded if a cron
    // run was missed for a while; weekly over a year is still only ~53 periods.
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
        let interest = accrued_interest(
            start_balance,
            &txns,
            last_paid,
            period_end,
            w.yield_rate_bps,
        );

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
