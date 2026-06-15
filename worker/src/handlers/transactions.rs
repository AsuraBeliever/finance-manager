//! Port of src-tauri/src/commands/transactions.rs, scoped through
//! wallets.user_id. Transfers stay atomic via D1 batch (its only transaction
//! primitive).

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::models::{Transaction, TransactionCategory};
use serde::Deserialize;
use wasm_bindgen::JsValue;
use worker::D1Database;

use crate::db::{all, batch, changes, exec, first, last_row_id, new_group_id, stmt, CountRow, ToJs};
use crate::jsv;

fn validate_amount(amount_cents: i64) -> AppResult<()> {
    if amount_cents <= 0 {
        return Err(AppError::InvalidInput("el monto debe ser positivo".into()));
    }
    Ok(())
}

fn validate_date(date: &str) -> AppResult<()> {
    chrono::NaiveDate::parse_from_str(date, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha inválida (se espera YYYY-MM-DD)".into()))?;
    Ok(())
}

async fn wallet_exists(db: &D1Database, uid: i64, id: i64) -> AppResult<()> {
    let row: Option<CountRow> = first(
        db,
        "SELECT COUNT(*) AS n FROM wallets WHERE id = ?1 AND user_id = ?2",
        jsv![id, uid],
    )
    .await?;
    if row.map(|r| r.n).unwrap_or(0) > 0 {
        Ok(())
    } else {
        Err(AppError::NotFound("cartera"))
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimpleTxArgs {
    pub wallet_id: i64,
    pub amount_cents: i64,
    pub category_id: Option<i64>,
    pub description: Option<String>,
    pub occurred_at: String,
    /// Offline-outbox idempotency: a replay with the same id never inserts twice.
    pub client_id: Option<String>,
}

#[derive(Deserialize)]
struct IdRow {
    id: i64,
}

async fn insert_simple(
    db: &D1Database,
    uid: i64,
    kind: &str, // 'income' | 'expense'
    a: SimpleTxArgs,
) -> AppResult<i64> {
    validate_amount(a.amount_cents)?;
    validate_date(&a.occurred_at)?;
    wallet_exists(db, uid, a.wallet_id).await?;
    if let Some(client_id) = &a.client_id {
        // already applied (response was lost mid-flight): return the same id
        let existing: Option<IdRow> = first(
            db,
            "SELECT id FROM transactions WHERE client_id = ?1",
            jsv![client_id],
        )
        .await?;
        if let Some(row) = existing {
            return Ok(row.id);
        }
    }
    let res = exec(
        db,
        "INSERT INTO transactions (wallet_id, kind, amount_cents, category_id, description, occurred_at, client_id)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        jsv![a.wallet_id, kind, a.amount_cents, a.category_id, a.description, a.occurred_at, a.client_id],
    )
    .await?;
    last_row_id(&res)
}

pub async fn add_income(db: &D1Database, uid: i64, a: SimpleTxArgs) -> AppResult<i64> {
    insert_simple(db, uid, "income", a).await
}

pub async fn add_expense(db: &D1Database, uid: i64, a: SimpleTxArgs) -> AppResult<i64> {
    insert_simple(db, uid, "expense", a).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TransferArgs {
    pub from_wallet_id: i64,
    pub to_wallet_id: i64,
    pub amount_from_cents: i64,
    pub amount_to_cents: i64,
    pub description: Option<String>,
    pub occurred_at: String,
    /// Offline-outbox idempotency; stored on the transfer_out leg only.
    pub client_id: Option<String>,
}

pub async fn add_transfer(db: &D1Database, uid: i64, a: TransferArgs) -> AppResult<String> {
    validate_amount(a.amount_from_cents)?;
    validate_amount(a.amount_to_cents)?;
    validate_date(&a.occurred_at)?;
    if a.from_wallet_id == a.to_wallet_id {
        return Err(AppError::InvalidInput(
            "la cartera origen y destino deben ser distintas".into(),
        ));
    }
    wallet_exists(db, uid, a.from_wallet_id).await?;
    wallet_exists(db, uid, a.to_wallet_id).await?;

    if let Some(client_id) = &a.client_id {
        #[derive(Deserialize)]
        struct GroupIdRow {
            transfer_group_id: String,
        }
        let existing: Option<GroupIdRow> = first(
            db,
            "SELECT transfer_group_id FROM transactions
             WHERE client_id = ?1 AND transfer_group_id IS NOT NULL",
            jsv![client_id],
        )
        .await?;
        if let Some(row) = existing {
            return Ok(row.transfer_group_id);
        }
    }

    let group_id = new_group_id();
    // Both legs in one batch: a transfer never half-applies.
    let insert = "INSERT INTO transactions (wallet_id, kind, amount_cents, transfer_group_id, description, occurred_at, client_id)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)";
    let stmts = vec![
        stmt(
            db,
            insert,
            jsv![
                a.from_wallet_id,
                "transfer_out",
                a.amount_from_cents,
                group_id,
                a.description,
                a.occurred_at,
                a.client_id
            ],
        )?,
        stmt(
            db,
            insert,
            jsv![
                a.to_wallet_id,
                "transfer_in",
                a.amount_to_cents,
                group_id,
                a.description,
                a.occurred_at,
                Option::<String>::None
            ],
        )?,
    ];
    batch(db, stmts).await?;
    Ok(group_id)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

#[derive(Deserialize)]
struct GroupRow {
    transfer_group_id: Option<String>,
}

/// Deleting any leg of a transfer removes both legs.
pub async fn delete_transaction(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let row: Option<GroupRow> = first(
        db,
        "SELECT t.transfer_group_id FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id AND w.user_id = ?2
         WHERE t.id = ?1",
        jsv![a.id, uid],
    )
    .await?;
    match row {
        None => Err(AppError::NotFound("transacción")),
        Some(GroupRow {
            transfer_group_id: Some(group_id),
        }) => {
            exec(
                db,
                "DELETE FROM transactions WHERE transfer_group_id = ?1",
                jsv![group_id],
            )
            .await?;
            Ok(())
        }
        Some(GroupRow {
            transfer_group_id: None,
        }) => {
            exec(db, "DELETE FROM transactions WHERE id = ?1", jsv![a.id]).await?;
            Ok(())
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateTxArgs {
    pub id: i64,
    pub wallet_id: i64,
    pub amount_cents: i64,
    pub category_id: Option<i64>,
    pub description: Option<String>,
    pub occurred_at: String,
}

/// Edits an income/expense transaction (amount, wallet, category, note, date).
/// Transfers aren't editable here — they're two linked legs; delete & recreate.
pub async fn update_transaction(db: &D1Database, uid: i64, a: UpdateTxArgs) -> AppResult<()> {
    validate_amount(a.amount_cents)?;
    validate_date(&a.occurred_at)?;
    wallet_exists(db, uid, a.wallet_id).await?;

    #[derive(Deserialize)]
    struct KindRow {
        kind: String,
        transfer_group_id: Option<String>,
    }
    // Confirm ownership (JOIN wallets) and that it's a plain income/expense.
    let row: Option<KindRow> = first(
        db,
        "SELECT t.kind, t.transfer_group_id FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id AND w.user_id = ?2
         WHERE t.id = ?1",
        jsv![a.id, uid],
    )
    .await?;
    let row = row.ok_or(AppError::NotFound("transacción"))?;
    if row.transfer_group_id.is_some() || (row.kind != "income" && row.kind != "expense") {
        return Err(AppError::InvalidInput(
            "las transferencias no se pueden editar; elimínala y créala de nuevo".into(),
        ));
    }
    let res = exec(
        db,
        "UPDATE transactions
         SET wallet_id = ?2, amount_cents = ?3, category_id = ?4, description = ?5, occurred_at = ?6
         WHERE id = ?1",
        jsv![
            a.id,
            a.wallet_id,
            a.amount_cents,
            a.category_id,
            a.description,
            a.occurred_at
        ],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("transacción"));
    }
    Ok(())
}

#[derive(Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TxFilter {
    pub wallet_id: Option<i64>,
    pub kind: Option<String>,
    pub category_id: Option<i64>,
    pub from: Option<String>,
    pub to: Option<String>,
    pub limit: Option<i64>,
    pub offset: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListTransactionsArgs {
    #[serde(default)]
    pub filter: TxFilter,
}

#[derive(Deserialize)]
struct TxRow {
    id: i64,
    wallet_id: i64,
    wallet_name: String,
    kind: String,
    amount_cents: i64,
    category_id: Option<i64>,
    category_name: Option<String>,
    transfer_group_id: Option<String>,
    description: Option<String>,
    occurred_at: String,
    created_at: String,
}

impl From<TxRow> for Transaction {
    fn from(r: TxRow) -> Self {
        Transaction {
            id: r.id,
            wallet_id: r.wallet_id,
            wallet_name: r.wallet_name,
            kind: r.kind,
            amount_cents: r.amount_cents,
            category_id: r.category_id,
            category_name: r.category_name,
            transfer_group_id: r.transfer_group_id,
            description: r.description,
            occurred_at: r.occurred_at,
            created_at: r.created_at,
        }
    }
}

pub async fn list_transactions(
    db: &D1Database,
    uid: i64,
    args: ListTransactionsArgs,
) -> AppResult<Vec<Transaction>> {
    let f = args.filter;
    let mut sql = String::from(
        "SELECT t.id, t.wallet_id, w.name AS wallet_name, t.kind, t.amount_cents,
                t.category_id, tc.name AS category_name, t.transfer_group_id,
                t.description, t.occurred_at, t.created_at
         FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id
         LEFT JOIN transaction_categories tc ON tc.id = t.category_id
         WHERE w.user_id = ?",
    );
    let mut params: Vec<JsValue> = vec![uid.to_js()];
    if let Some(wid) = f.wallet_id {
        sql.push_str(" AND t.wallet_id = ?");
        params.push(wid.to_js());
    }
    if let Some(kind) = &f.kind {
        sql.push_str(" AND t.kind = ?");
        params.push(kind.to_js());
    }
    if let Some(cid) = f.category_id {
        sql.push_str(" AND t.category_id = ?");
        params.push(cid.to_js());
    }
    if let Some(from) = &f.from {
        sql.push_str(" AND t.occurred_at >= ?");
        params.push(from.to_js());
    }
    if let Some(to) = &f.to {
        sql.push_str(" AND t.occurred_at <= ?");
        params.push(to.to_js());
    }
    sql.push_str(" ORDER BY t.occurred_at DESC, t.id DESC LIMIT ? OFFSET ?");
    params.push(f.limit.unwrap_or(100).to_js());
    params.push(f.offset.unwrap_or(0).to_js());

    Ok(all::<TxRow>(db, &sql, params)
        .await?
        .into_iter()
        .map(Into::into)
        .collect())
}

#[derive(Deserialize)]
struct CategoryRow {
    id: i64,
    name: String,
    kind: String,
    icon: Option<String>,
    color: Option<String>,
    is_system: i64,
    is_hidden: i64,
}

impl From<CategoryRow> for TransactionCategory {
    fn from(r: CategoryRow) -> Self {
        TransactionCategory {
            id: r.id,
            name: r.name,
            kind: r.kind,
            icon: r.icon,
            color: r.color,
            is_system: r.is_system != 0,
            is_hidden: r.is_hidden != 0,
        }
    }
}

/// Categories offered in the pickers: the user's own plus the seeds they
/// haven't hidden. `is_hidden` is always false here.
pub async fn list_transaction_categories(
    db: &D1Database,
    uid: i64,
) -> AppResult<Vec<TransactionCategory>> {
    let rows: Vec<CategoryRow> = all(
        db,
        "SELECT tc.id, tc.name, tc.kind, tc.icon, tc.color, tc.is_system, 0 AS is_hidden
         FROM transaction_categories tc
         WHERE (tc.user_id IS NULL OR tc.user_id = ?1)
           AND NOT EXISTS (
             SELECT 1 FROM hidden_categories h
             WHERE h.user_id = ?1 AND h.category_id = tc.id)
         ORDER BY tc.kind, tc.is_system DESC, tc.id",
        jsv![uid],
    )
    .await?;
    Ok(rows.into_iter().map(Into::into).collect())
}

/// Everything the user can manage: own categories + all seeds, each flagged
/// with `is_hidden` so the manager can show hidden seeds with a restore action.
pub async fn list_manage_categories(
    db: &D1Database,
    uid: i64,
) -> AppResult<Vec<TransactionCategory>> {
    let rows: Vec<CategoryRow> = all(
        db,
        "SELECT tc.id, tc.name, tc.kind, tc.icon, tc.color, tc.is_system,
                EXISTS (SELECT 1 FROM hidden_categories h
                        WHERE h.user_id = ?1 AND h.category_id = tc.id) AS is_hidden
         FROM transaction_categories tc
         WHERE tc.user_id IS NULL OR tc.user_id = ?1
         ORDER BY tc.kind, tc.is_system DESC, tc.id",
        jsv![uid],
    )
    .await?;
    Ok(rows.into_iter().map(Into::into).collect())
}

fn validate_category(name: &str, kind: &str) -> AppResult<()> {
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    if kind != "income" && kind != "expense" {
        return Err(AppError::InvalidInput("tipo inválido".into()));
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateCategoryArgs {
    pub name: String,
    pub kind: String,
    pub color: Option<String>,
}

pub async fn create_transaction_category(
    db: &D1Database,
    uid: i64,
    a: CreateCategoryArgs,
) -> AppResult<i64> {
    validate_category(&a.name, &a.kind)?;
    let res = exec(
        db,
        "INSERT INTO transaction_categories (user_id, name, kind, color) VALUES (?1, ?2, ?3, ?4)",
        jsv![uid, a.name.trim(), a.kind, a.color],
    )
    .await?;
    last_row_id(&res)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCategoryArgs {
    pub id: i64,
    pub name: String,
    pub color: Option<String>,
}

/// Renames / recolors one of the user's OWN categories. Seeds (user_id NULL)
/// are shared and can't be edited, so they match no row here.
pub async fn update_transaction_category(
    db: &D1Database,
    uid: i64,
    a: UpdateCategoryArgs,
) -> AppResult<()> {
    if a.name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    let res = exec(
        db,
        "UPDATE transaction_categories SET name = ?3, color = ?4
         WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, a.name.trim(), a.color],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("categoría"));
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CategoryIdArgs {
    pub id: i64,
}

/// "Removes" a category. Own category → reassign its transactions to NULL,
/// drop its budget (the per-user unique index forbids a second NULL-category
/// budget) and uncategorize its subscriptions, then delete it — one atomic
/// batch so history survives intact. Seed category → just hide it for this
/// user (it stays shared and its existing transactions keep their label).
pub async fn delete_transaction_category(
    db: &D1Database,
    uid: i64,
    a: CategoryIdArgs,
) -> AppResult<()> {
    // is this an own category, a seed, or not visible to the caller?
    let owner: Option<CountRow> = first(
        db,
        "SELECT COUNT(*) AS n FROM transaction_categories
         WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    let is_own = owner.map(|r| r.n).unwrap_or(0) > 0;

    if is_own {
        let stmts = vec![
            stmt(
                db,
                "UPDATE transactions SET category_id = NULL WHERE category_id = ?1",
                jsv![a.id],
            )?,
            stmt(
                db,
                "UPDATE subscriptions SET category_id = NULL WHERE category_id = ?1 AND user_id = ?2",
                jsv![a.id, uid],
            )?,
            stmt(
                db,
                "DELETE FROM budgets WHERE category_id = ?1 AND user_id = ?2",
                jsv![a.id, uid],
            )?,
            stmt(
                db,
                "DELETE FROM transaction_categories WHERE id = ?1 AND user_id = ?2",
                jsv![a.id, uid],
            )?,
        ];
        batch(db, stmts).await?;
        return Ok(());
    }

    // Seed (or unknown id): hide it for this user. Confirm it's a visible seed.
    let seed: Option<CountRow> = first(
        db,
        "SELECT COUNT(*) AS n FROM transaction_categories WHERE id = ?1 AND user_id IS NULL",
        jsv![a.id],
    )
    .await?;
    if seed.map(|r| r.n).unwrap_or(0) == 0 {
        return Err(AppError::NotFound("categoría"));
    }
    exec(
        db,
        "INSERT OR IGNORE INTO hidden_categories (user_id, category_id) VALUES (?1, ?2)",
        jsv![uid, a.id],
    )
    .await?;
    Ok(())
}

/// Un-hides a previously hidden seed category for this user.
pub async fn restore_transaction_category(
    db: &D1Database,
    uid: i64,
    a: CategoryIdArgs,
) -> AppResult<()> {
    exec(
        db,
        "DELETE FROM hidden_categories WHERE user_id = ?1 AND category_id = ?2",
        jsv![uid, a.id],
    )
    .await?;
    Ok(())
}
