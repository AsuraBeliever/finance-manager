//! Savings goals: a target with a manually-tracked saved amount. Progress is
//! computed in Rust (basis points, capped at 100%). All scoped by user_id.

use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use crate::db::{all, changes, exec, first, last_row_id};
use crate::jsv;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SavingsGoal {
    pub id: i64,
    pub name: String,
    pub icon: Option<String>,
    pub color: Option<String>,
    pub currency_code: String,
    pub target_cents: i64,
    pub saved_cents: i64,
    pub progress_bps: i64,
}

#[derive(Deserialize)]
struct GoalRow {
    id: i64,
    name: String,
    icon: Option<String>,
    color: Option<String>,
    currency_code: String,
    target_cents: i64,
    saved_cents: i64,
}

fn progress_bps(saved: i64, target: i64) -> i64 {
    if target <= 0 {
        0
    } else {
        (((saved as i128) * 10_000) / target as i128).min(10_000) as i64
    }
}

impl From<GoalRow> for SavingsGoal {
    fn from(r: GoalRow) -> Self {
        SavingsGoal {
            progress_bps: progress_bps(r.saved_cents, r.target_cents),
            id: r.id,
            name: r.name,
            icon: r.icon,
            color: r.color,
            currency_code: r.currency_code,
            target_cents: r.target_cents,
            saved_cents: r.saved_cents,
        }
    }
}

const SELECT: &str =
    "SELECT id, name, icon, color, currency_code, target_cents, saved_cents FROM savings_goals";

async fn fetch_goal(db: &D1Database, uid: i64, id: i64) -> AppResult<SavingsGoal> {
    let row: GoalRow = first(
        db,
        &format!("{SELECT} WHERE id = ?1 AND user_id = ?2"),
        jsv![id, uid],
    )
    .await?
    .ok_or(AppError::NotFound("meta"))?;
    Ok(row.into())
}

pub async fn list_savings_goals(db: &D1Database, uid: i64) -> AppResult<Vec<SavingsGoal>> {
    let rows: Vec<GoalRow> = all(
        db,
        &format!("{SELECT} WHERE user_id = ?1 ORDER BY created_at, id"),
        jsv![uid],
    )
    .await?;
    Ok(rows.into_iter().map(Into::into).collect())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GoalInput {
    pub name: String,
    pub icon: Option<String>,
    pub color: Option<String>,
    pub currency_code: String,
    pub target_cents: i64,
}

fn validate(input: &GoalInput) -> AppResult<()> {
    if input.name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    if input.target_cents <= 0 {
        return Err(AppError::InvalidInput("la meta debe ser positiva".into()));
    }
    Ok(())
}

pub async fn create_savings_goal(
    db: &D1Database,
    uid: i64,
    a: GoalInput,
) -> AppResult<SavingsGoal> {
    validate(&a)?;
    let res = exec(
        db,
        "INSERT INTO savings_goals (user_id, name, icon, color, currency_code, target_cents)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        jsv![
            uid,
            a.name.trim(),
            a.icon,
            a.color,
            a.currency_code,
            a.target_cents
        ],
    )
    .await?;
    fetch_goal(db, uid, last_row_id(&res)?).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GoalUpdate {
    pub id: i64,
    #[serde(flatten)]
    pub input: GoalInput,
}

pub async fn update_savings_goal(
    db: &D1Database,
    uid: i64,
    a: GoalUpdate,
) -> AppResult<SavingsGoal> {
    validate(&a.input)?;
    let res = exec(
        db,
        "UPDATE savings_goals
         SET name = ?3, icon = ?4, color = ?5, currency_code = ?6, target_cents = ?7
         WHERE id = ?1 AND user_id = ?2",
        jsv![
            a.id,
            uid,
            a.input.name.trim(),
            a.input.icon,
            a.input.color,
            a.input.currency_code,
            a.input.target_cents
        ],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("meta"));
    }
    fetch_goal(db, uid, a.id).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ContributeArgs {
    pub id: i64,
    /// Positive adds, negative withdraws; saved_cents never drops below 0.
    pub amount_cents: i64,
}

pub async fn contribute_savings_goal(
    db: &D1Database,
    uid: i64,
    a: ContributeArgs,
) -> AppResult<SavingsGoal> {
    let res = exec(
        db,
        "UPDATE savings_goals SET saved_cents = MAX(0, saved_cents + ?3)
         WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, a.amount_cents],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("meta"));
    }
    fetch_goal(db, uid, a.id).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

pub async fn delete_savings_goal(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let res = exec(
        db,
        "DELETE FROM savings_goals WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("meta"));
    }
    Ok(())
}
