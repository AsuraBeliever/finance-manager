//! Port of src-tauri/src/commands/wallets.rs, scoped by user_id.

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::models::Wallet;
use serde::Deserialize;
use worker::D1Database;

use crate::db::{all, batch, changes, exec, first, last_row_id, stmt};
use crate::jsv;

/// Balance is always computed: initial + signed sum of transactions.
const WALLET_SELECT: &str = "
    SELECT w.id, w.name, w.category_id, wc.name AS category_name, w.currency_code,
           w.initial_balance_cents,
           w.initial_balance_cents + COALESCE((
             SELECT SUM(CASE t.kind
                          WHEN 'income' THEN t.amount_cents
                          WHEN 'transfer_in' THEN t.amount_cents
                          ELSE -t.amount_cents END)
             FROM transactions t WHERE t.wallet_id = w.id), 0) AS balance_cents,
           w.color, w.skin, w.notes, w.is_archived, w.created_at
    FROM wallets w
    JOIN wallet_categories wc ON wc.id = w.category_id";

/// D1 rows arrive with snake_case column names; models serialize camelCase.
#[derive(Deserialize)]
struct WalletRow {
    id: i64,
    name: String,
    category_id: i64,
    category_name: String,
    currency_code: String,
    initial_balance_cents: i64,
    balance_cents: i64,
    color: Option<String>,
    skin: Option<String>,
    notes: Option<String>,
    is_archived: i64,
    created_at: String,
}

impl From<WalletRow> for Wallet {
    fn from(r: WalletRow) -> Self {
        Wallet {
            id: r.id,
            name: r.name,
            category_id: r.category_id,
            category_name: r.category_name,
            currency_code: r.currency_code,
            initial_balance_cents: r.initial_balance_cents,
            balance_cents: r.balance_cents,
            color: r.color,
            skin: r.skin,
            notes: r.notes,
            is_archived: r.is_archived != 0,
            created_at: r.created_at,
        }
    }
}

pub async fn fetch_wallet(db: &D1Database, uid: i64, id: i64) -> AppResult<Wallet> {
    let sql = format!("{WALLET_SELECT} WHERE w.id = ?1 AND w.user_id = ?2");
    first::<WalletRow>(db, &sql, jsv![id, uid])
        .await?
        .map(Into::into)
        .ok_or(AppError::NotFound("cartera"))
}

fn validate(name: &str) -> AppResult<()> {
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListWalletsArgs {
    pub include_archived: Option<bool>,
}

pub async fn list_wallets(
    db: &D1Database,
    uid: i64,
    args: ListWalletsArgs,
) -> AppResult<Vec<Wallet>> {
    let filter = if args.include_archived.unwrap_or(false) {
        ""
    } else {
        " AND w.is_archived = 0"
    };
    let sql = format!(
        "{WALLET_SELECT} WHERE w.user_id = ?1{filter} ORDER BY w.sort_order, w.created_at, w.id"
    );
    Ok(all::<WalletRow>(db, &sql, jsv![uid])
        .await?
        .into_iter()
        .map(Into::into)
        .collect())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

pub async fn get_wallet(db: &D1Database, uid: i64, args: IdArgs) -> AppResult<Wallet> {
    fetch_wallet(db, uid, args.id).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateWalletArgs {
    pub name: String,
    pub category_id: i64,
    pub currency_code: String,
    pub initial_balance_cents: i64,
    pub color: Option<String>,
    pub skin: Option<String>,
    pub notes: Option<String>,
}

pub async fn create_wallet(db: &D1Database, uid: i64, a: CreateWalletArgs) -> AppResult<Wallet> {
    validate(&a.name)?;
    let res = exec(
        db,
        "INSERT INTO wallets (user_id, name, category_id, currency_code, initial_balance_cents, color, skin, notes, sort_order)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8,
                 COALESCE((SELECT MAX(sort_order) + 1 FROM wallets WHERE user_id = ?1), 0))",
        jsv![uid, a.name.trim(), a.category_id, a.currency_code, a.initial_balance_cents, a.color, a.skin, a.notes],
    )
    .await?;
    fetch_wallet(db, uid, last_row_id(&res)?).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateWalletArgs {
    pub id: i64,
    pub name: String,
    pub category_id: i64,
    pub currency_code: String,
    pub initial_balance_cents: i64,
    pub color: Option<String>,
    pub skin: Option<String>,
    pub notes: Option<String>,
}

pub async fn update_wallet(db: &D1Database, uid: i64, a: UpdateWalletArgs) -> AppResult<Wallet> {
    validate(&a.name)?;
    let res = exec(
        db,
        "UPDATE wallets
         SET name = ?3, category_id = ?4, currency_code = ?5,
             initial_balance_cents = ?6, color = ?7, skin = ?8, notes = ?9
         WHERE id = ?1 AND user_id = ?2",
        jsv![
            a.id,
            uid,
            a.name.trim(),
            a.category_id,
            a.currency_code,
            a.initial_balance_cents,
            a.color,
            a.skin,
            a.notes
        ],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("cartera"));
    }
    fetch_wallet(db, uid, a.id).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ArchiveWalletArgs {
    pub id: i64,
    pub archived: bool,
}

pub async fn archive_wallet(db: &D1Database, uid: i64, a: ArchiveWalletArgs) -> AppResult<()> {
    let res = exec(
        db,
        "UPDATE wallets SET is_archived = ?3 WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, a.archived],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("cartera"));
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReorderWalletsArgs {
    /// Wallet ids in the desired display order (front to back).
    pub ids: Vec<i64>,
}

/// Persists a new wallet order: each id's `sort_order` becomes its index in
/// the list. Scoped by user, so an id the caller doesn't own simply matches
/// no row. One atomic batch.
pub async fn reorder_wallets(db: &D1Database, uid: i64, a: ReorderWalletsArgs) -> AppResult<()> {
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
                "UPDATE wallets SET sort_order = ?3 WHERE id = ?1 AND user_id = ?2",
                jsv![id, uid, i as i64],
            )
        })
        .collect::<AppResult<Vec<_>>>()?;
    batch(db, stmts).await?;
    Ok(())
}

/// Deletes the wallet and everything that references it: its transactions,
/// the sibling legs of its transfers (an orphan half-transfer would corrupt
/// the other wallet's history), and any investment links. One atomic batch.
pub async fn delete_wallet(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    // ownership / existence check up front: a batch can't return NotFound
    fetch_wallet(db, uid, a.id).await?;
    let stmts = vec![
        stmt(
            db,
            "DELETE FROM transactions WHERE transfer_group_id IN (
               SELECT transfer_group_id FROM transactions
               WHERE wallet_id = ?1 AND transfer_group_id IS NOT NULL)",
            jsv![a.id],
        )?,
        stmt(
            db,
            "DELETE FROM transactions WHERE wallet_id = ?1",
            jsv![a.id],
        )?,
        stmt(
            db,
            "UPDATE investments SET linked_wallet_id = NULL
             WHERE linked_wallet_id = ?1 AND user_id = ?2",
            jsv![a.id, uid],
        )?,
        stmt(
            db,
            "DELETE FROM wallets WHERE id = ?1 AND user_id = ?2",
            jsv![a.id, uid],
        )?,
    ];
    batch(db, stmts).await?;
    Ok(())
}
