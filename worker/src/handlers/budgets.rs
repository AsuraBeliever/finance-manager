//! Monthly budgets / spending limits. One per (user, category); category NULL
//! is the overall limit. "spent" is computed at read time from this month's
//! expenses, converted to MXN in Rust — never stored.

use std::collections::HashMap;

use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{load_rates, to_mxn};
use crate::db::{all, changes, exec};
use crate::jsv;

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

fn progress_bps(spent: i64, limit: i64) -> i64 {
    if limit <= 0 {
        0
    } else {
        (((spent as i128) * 10_000) / limit as i128) as i64
    }
}

pub async fn list_budgets(db: &D1Database, uid: i64) -> AppResult<Vec<Budget>> {
    let rates = load_rates(db).await?;

    // This month's expenses per category + currency → MXN map + grand total.
    let spent_rows: Vec<SpentRow> = all(
        db,
        "SELECT t.category_id AS category_id, w.currency_code AS currency_code,
                SUM(t.amount_cents) AS sum_cents
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1 AND t.kind = 'expense'
           AND t.occurred_at >= date('now','start of month')
         GROUP BY t.category_id, w.currency_code",
        jsv![uid],
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
            Budget {
                progress_bps: progress_bps(spent, b.limit_cents),
                spent_mxn_cents: spent,
                id: b.id,
                category_id: b.category_id,
                category_name: b.category_name,
                color: b.color,
                limit_cents: b.limit_cents,
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
