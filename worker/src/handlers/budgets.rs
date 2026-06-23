//! Monthly budgets / spending limits. One per (user, category); category NULL
//! is the overall limit. "spent" is computed at read time from the selected
//! period's expenses (MXN). The limit is scoped to the period too — prorated by
//! day and honoring limit changes via `budget_limit_history`.

use std::collections::HashMap;

use chrono::NaiveDate;
use finanzas_core::budget::prorated_limit;
use finanzas_core::error::{AppError, AppResult};
use finanzas_core::period::{resolve_period, Period};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{load_rates, to_mxn};
use crate::db::{all, changes, exec, today_mx};
use crate::jsv;

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct BudgetArgs {
    #[serde(default)]
    pub period: Period,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Budget {
    pub id: i64,
    pub category_id: Option<i64>,
    pub category_name: Option<String>,
    pub color: Option<String>,
    pub limit_cents: i64,
    pub spent_mxn_cents: i64,
    pub progress_bps: i64,
}

#[derive(Deserialize)]
struct BudgetRow {
    id: i64,
    category_id: Option<i64>,
    category_name: Option<String>,
    color: Option<String>,
    limit_cents: i64,
}

#[derive(Deserialize)]
struct SpentRow {
    category_id: Option<i64>,
    currency_code: String,
    sum_cents: i64,
}

#[derive(Deserialize)]
struct HistRow {
    category_id: Option<i64>,
    limit_cents: i64,
    effective_from: String,
}

fn progress_bps(spent: i64, limit: i64) -> i64 {
    if limit <= 0 {
        0
    } else {
        (((spent as i128) * 10_000) / limit as i128) as i64
    }
}

pub async fn list_budgets(db: &D1Database, uid: i64, a: BudgetArgs) -> AppResult<Vec<Budget>> {
    let rates = load_rates(db, uid).await?;
    let resolved = resolve_period(&a.period, today_mx());
    let start = resolved.start.to_string();
    let end = resolved.end.to_string();

    // Expenses within the period, per category + currency → MXN map + grand total.
    let spent_rows: Vec<SpentRow> = all(
        db,
        "SELECT t.category_id AS category_id, w.currency_code AS currency_code,
                SUM(t.amount_cents) AS sum_cents
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind = 'expense'
           AND t.occurred_at >= ?2 AND t.occurred_at < ?3
         GROUP BY t.category_id, w.currency_code",
        jsv![uid, start, end],
    )
    .await?;
    let mut by_cat: HashMap<i64, i64> = HashMap::new();
    let mut overall = 0i64;
    for r in spent_rows {
        let mxn = to_mxn(
            r.sum_cents,
            rates.get(&r.currency_code).copied().unwrap_or(0),
        );
        overall += mxn;
        if let Some(cid) = r.category_id {
            *by_cat.entry(cid).or_insert(0) += mxn;
        }
    }

    // Limit-change history, grouped per category and sorted ascending.
    let hist: Vec<HistRow> = all(
        db,
        "SELECT category_id, limit_cents, effective_from FROM budget_limit_history
         WHERE user_id = ?1 ORDER BY effective_from ASC, id ASC",
        jsv![uid],
    )
    .await?;
    let mut hist_by_cat: HashMap<Option<i64>, Vec<(NaiveDate, i64)>> = HashMap::new();
    for h in hist {
        if let Ok(ef) = NaiveDate::parse_from_str(&h.effective_from, "%Y-%m-%d") {
            hist_by_cat
                .entry(h.category_id)
                .or_default()
                .push((ef, h.limit_cents));
        }
    }

    let rows: Vec<BudgetRow> = all(
        db,
        "SELECT b.id, b.category_id, c.name AS category_name, c.color AS color, b.limit_cents
         FROM budgets b
         LEFT JOIN transaction_categories c ON c.id = b.category_id
         WHERE b.user_id = ?1
         ORDER BY (b.category_id IS NOT NULL), c.name",
        jsv![uid],
    )
    .await?;

    Ok(rows
        .into_iter()
        .map(|b| {
            let spent = match b.category_id {
                None => overall,
                Some(cid) => by_cat.get(&cid).copied().unwrap_or(0),
            };
            // Limit prorated over the period; legacy budgets without history fall
            // back to the current limit applied from the epoch.
            let fallback = [(NaiveDate::from_ymd_opt(1970, 1, 1).unwrap(), b.limit_cents)];
            let history = hist_by_cat
                .get(&b.category_id)
                .map(|v| v.as_slice())
                .filter(|v| !v.is_empty())
                .unwrap_or(&fallback);
            let limit = prorated_limit(history, resolved.start, resolved.end);
            Budget {
                progress_bps: progress_bps(spent, limit),
                spent_mxn_cents: spent,
                id: b.id,
                category_id: b.category_id,
                category_name: b.category_name,
                color: b.color,
                limit_cents: limit,
            }
        })
        .collect())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BudgetInput {
    pub category_id: Option<i64>,
    pub limit_cents: i64,
}

/// Create or update the limit for (user, category). Matches the expression
/// unique index idx_budgets_unique on (user_id, COALESCE(category_id, 0)).
pub async fn set_budget(db: &D1Database, uid: i64, a: BudgetInput) -> AppResult<()> {
    if a.limit_cents <= 0 {
        return Err(AppError::InvalidInput("el límite debe ser positivo".into()));
    }
    exec(
        db,
        "INSERT INTO budgets (user_id, category_id, limit_cents) VALUES (?1, ?2, ?3)
         ON CONFLICT(user_id, COALESCE(category_id, 0))
         DO UPDATE SET limit_cents = excluded.limit_cents",
        jsv![uid, a.category_id, a.limit_cents],
    )
    .await?;
    // Record the change in history (one row per month; replace this month's).
    exec(
        db,
        "DELETE FROM budget_limit_history
         WHERE user_id = ?1 AND COALESCE(category_id, 0) = COALESCE(?2, 0)
           AND effective_from = date('now','start of month')",
        jsv![uid, a.category_id],
    )
    .await?;
    exec(
        db,
        "INSERT INTO budget_limit_history (user_id, category_id, limit_cents, effective_from)
         VALUES (?1, ?2, ?3, date('now','start of month'))",
        jsv![uid, a.category_id, a.limit_cents],
    )
    .await?;
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

pub async fn delete_budget(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let res = exec(
        db,
        "DELETE FROM budgets WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("presupuesto"));
    }
    Ok(())
}
