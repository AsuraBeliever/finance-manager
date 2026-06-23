//! Savings goals — "apartados" earmarked inside a wallet (like BBVA Apartados /
//! Nu cajitas) or plain tracking entries. A goal linked to a wallet RESERVES
//! part of that wallet's balance: contributing earmarks (no transaction, the
//! money stays in the wallet and in net worth); only "using" a goal posts a real
//! expense and archives it. Track-only goals (no wallet) are abstract progress.
//! Progress in basis points, capped at 100%. All scoped by user_id.

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::period::{resolve_period, Period};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use crate::db::{all, batch, changes, exec, first, last_row_id, stmt, today_mx};
use crate::jsv;

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct GoalArgs {
    #[serde(default)]
    pub period: Period,
}

/// Record a goal's saved amount for today (one snapshot per day; replace it if
/// it already exists). Lets historical views reconstruct progress over time.
async fn snapshot_goal(db: &D1Database, goal_id: i64, saved_cents: i64) -> AppResult<()> {
    exec(
        db,
        "DELETE FROM goal_snapshots WHERE goal_id = ?1 AND as_of = date('now')",
        jsv![goal_id],
    )
    .await?;
    exec(
        db,
        "INSERT INTO goal_snapshots (goal_id, saved_cents, as_of, source)
         VALUES (?1, ?2, date('now'), 'manual')",
        jsv![goal_id, saved_cents],
    )
    .await?;
    Ok(())
}

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
    /// Wallet this goal is an apartado of (its money is reserved there). None =
    /// a plain tracking goal with no real money behind it.
    pub linked_wallet_id: Option<i64>,
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
    #[serde(default)]
    linked_wallet_id: Option<i64>,
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
            linked_wallet_id: r.linked_wallet_id,
        }
    }
}

const SELECT: &str = "SELECT id, name, icon, color, currency_code, target_cents, saved_cents,
        linked_wallet_id FROM savings_goals";

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

pub async fn list_savings_goals(
    db: &D1Database,
    uid: i64,
    a: GoalArgs,
) -> AppResult<Vec<SavingsGoal>> {
    let end = resolve_period(&a.period, today_mx()).end.to_string();
    // Only goals that already existed by the period end (a goal created later
    // shouldn't appear in a past period), with `saved_cents` as of that date:
    // the latest snapshot at or before it (so it reflects the progress then —
    // 0%, partial, or already met).
    let rows: Vec<GoalRow> = all(
        db,
        "SELECT g.id, g.name, g.icon, g.color, g.currency_code, g.target_cents,
                g.linked_wallet_id,
                COALESCE((SELECT s.saved_cents FROM goal_snapshots s
                          WHERE s.goal_id = g.id AND s.as_of <= ?2
                          ORDER BY s.as_of DESC, s.id DESC LIMIT 1), 0) AS saved_cents
         FROM savings_goals g
         WHERE g.user_id = ?1 AND date(g.created_at) < ?2
           AND (g.archived_at IS NULL OR g.archived_at >= ?2)
         ORDER BY g.sort_order, g.created_at, g.id",
        jsv![uid, end],
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
    /// Wallet to make this goal an apartado of (None = track only). When set, the
    /// goal's currency follows the wallet's.
    #[serde(default)]
    pub wallet_id: Option<i64>,
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

/// Resolve the goal's wallet link + currency: an apartado follows its wallet's
/// currency; a track-only goal keeps the requested currency.
async fn resolve_link(
    db: &D1Database,
    uid: i64,
    a: &GoalInput,
) -> AppResult<(Option<i64>, String)> {
    match a.wallet_id {
        Some(wid) => {
            let w: CurrencyRow = first(
                db,
                "SELECT currency_code FROM wallets WHERE id = ?1 AND user_id = ?2",
                jsv![wid, uid],
            )
            .await?
            .ok_or(AppError::NotFound("cartera"))?;
            Ok((Some(wid), w.currency_code))
        }
        None => Ok((None, a.currency_code.clone())),
    }
}

pub async fn create_savings_goal(
    db: &D1Database,
    uid: i64,
    a: GoalInput,
) -> AppResult<SavingsGoal> {
    validate(&a)?;
    let (linked_wallet_id, currency) = resolve_link(db, uid, &a).await?;
    let res = exec(
        db,
        "INSERT INTO savings_goals
           (user_id, name, icon, color, currency_code, target_cents, linked_wallet_id, sort_order)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7,
                 COALESCE((SELECT MAX(sort_order) + 1 FROM savings_goals WHERE user_id = ?1), 0))",
        jsv![
            uid,
            a.name.trim(),
            a.icon,
            a.color,
            currency,
            a.target_cents,
            linked_wallet_id
        ],
    )
    .await?;
    let goal = fetch_goal(db, uid, last_row_id(&res)?).await?;
    snapshot_goal(db, goal.id, goal.saved_cents).await?;
    Ok(goal)
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
    let (linked_wallet_id, currency) = resolve_link(db, uid, &a.input).await?;
    let res = exec(
        db,
        "UPDATE savings_goals
         SET name = ?3, icon = ?4, color = ?5, currency_code = ?6, target_cents = ?7,
             linked_wallet_id = ?8
         WHERE id = ?1 AND user_id = ?2",
        jsv![
            a.id,
            uid,
            a.input.name.trim(),
            a.input.icon,
            a.input.color,
            currency,
            a.input.target_cents,
            linked_wallet_id
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
    /// Positive reserves more, negative releases; saved_cents never drops below 0.
    pub amount_cents: i64,
}

#[derive(Deserialize)]
struct CurrencyRow {
    currency_code: String,
}

#[derive(Deserialize)]
struct ReserveRow {
    balance_cents: i64,
    reserved_cents: i64,
}

/// Available to reserve in a wallet right now: balance minus everything already
/// earmarked by active goals on it.
async fn wallet_available(db: &D1Database, uid: i64, wallet_id: i64) -> AppResult<i64> {
    let row: ReserveRow = first(
        db,
        "SELECT
           w.initial_balance_cents + COALESCE((
             SELECT SUM(CASE t.kind
                          WHEN 'income' THEN t.amount_cents
                          WHEN 'transfer_in' THEN t.amount_cents
                          ELSE -t.amount_cents END)
             FROM transactions t WHERE t.wallet_id = w.id), 0) AS balance_cents,
           COALESCE((SELECT SUM(g.saved_cents) FROM savings_goals g
                     WHERE g.linked_wallet_id = w.id AND g.archived_at IS NULL), 0)
             AS reserved_cents
         FROM wallets w WHERE w.id = ?1 AND w.user_id = ?2",
        jsv![wallet_id, uid],
    )
    .await?
    .ok_or(AppError::NotFound("cartera"))?;
    Ok(row.balance_cents - row.reserved_cents)
}

/// Reserve (deposit) or release (withdraw) money in a goal. For an apartado, the
/// money stays in the wallet — this only changes the earmark, never a balance —
/// and a deposit can't exceed the wallet's available. No transaction is posted.
pub async fn contribute_savings_goal(
    db: &D1Database,
    uid: i64,
    a: ContributeArgs,
) -> AppResult<SavingsGoal> {
    if a.amount_cents == 0 {
        return Err(AppError::InvalidInput("el monto no puede ser cero".into()));
    }
    let goal = fetch_goal(db, uid, a.id).await?;
    // A withdrawal can't release more than what's saved.
    let amount = if a.amount_cents < 0 {
        a.amount_cents.max(-goal.saved_cents)
    } else {
        a.amount_cents
    };
    if amount == 0 {
        return Err(AppError::InvalidInput("no hay nada que retirar".into()));
    }

    // Apartado deposits can't reserve more than the wallet has available.
    if amount > 0 {
        if let Some(wallet_id) = goal.linked_wallet_id {
            if amount > wallet_available(db, uid, wallet_id).await? {
                return Err(AppError::InvalidInput(
                    "no hay suficiente disponible en la cartera para apartar ese monto".into(),
                ));
            }
        }
    }

    exec(
        db,
        "UPDATE savings_goals SET saved_cents = MAX(0, saved_cents + ?3)
         WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, amount],
    )
    .await?;

    let goal = fetch_goal(db, uid, a.id).await?;
    snapshot_goal(db, goal.id, goal.saved_cents).await?;
    Ok(goal)
}

/// "Use" a goal: spend the money you saved for it. For an apartado, a real
/// expense posts on its wallet for the saved amount (money finally leaves), then
/// the goal is archived; a track-only goal is just archived. Releases the
/// earmark either way.
pub async fn use_savings_goal(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let goal = fetch_goal(db, uid, a.id).await?;
    let today = today_mx().to_string();
    match goal.linked_wallet_id {
        Some(wallet_id) if goal.saved_cents > 0 => {
            batch(
                db,
                vec![
                    stmt(
                        db,
                        "INSERT INTO transactions (wallet_id, kind, amount_cents, description, occurred_at)
                         VALUES (?1, 'expense', ?2, ?3, ?4)",
                        jsv![wallet_id, goal.saved_cents, format!("Meta: {}", goal.name), today],
                    )?,
                    stmt(
                        db,
                        "UPDATE savings_goals SET archived_at = ?3 WHERE id = ?1 AND user_id = ?2",
                        jsv![a.id, uid, today],
                    )?,
                ],
            )
            .await?;
        }
        _ => {
            let res = exec(
                db,
                "UPDATE savings_goals SET archived_at = ?3 WHERE id = ?1 AND user_id = ?2",
                jsv![a.id, uid, today],
            )
            .await?;
            if changes(&res) == 0 {
                return Err(AppError::NotFound("meta"));
            }
        }
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

/// Daily cron: record today's saved amount for every goal so the progress curve
/// fills in between manual contributions.
pub async fn snapshot_all_goals(db: &D1Database) -> AppResult<()> {
    exec(
        db,
        "DELETE FROM goal_snapshots WHERE as_of = date('now') AND source = 'auto'",
        vec![],
    )
    .await?;
    exec(
        db,
        "INSERT INTO goal_snapshots (goal_id, saved_cents, as_of, source)
         SELECT id, saved_cents, date('now'), 'auto' FROM savings_goals",
        vec![],
    )
    .await?;
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReorderArgs {
    /// Goal ids in the desired display order (first = principal/gauge).
    pub ids: Vec<i64>,
}

/// Persist a new goal order: each id's `sort_order` becomes its index. Scoped by
/// user; an id the caller doesn't own simply matches no row. One atomic batch.
pub async fn reorder_savings_goals(db: &D1Database, uid: i64, a: ReorderArgs) -> AppResult<()> {
    if a.ids.is_empty() {
        return Ok(());
    }
    let stmts = a
        .ids
        .iter()
        .enumerate()
        .map(|(i, id)| {
            stmt(
                db,
                "UPDATE savings_goals SET sort_order = ?3 WHERE id = ?1 AND user_id = ?2",
                jsv![id, uid, i as i64],
            )
        })
        .collect::<AppResult<Vec<_>>>()?;
    batch(db, stmts).await?;
    Ok(())
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
