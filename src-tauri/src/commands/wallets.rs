use rusqlite::{params, Connection, Row};
use tauri::State;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::models::Wallet;

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
           w.color, w.notes, w.is_archived, w.created_at
    FROM wallets w
    JOIN wallet_categories wc ON wc.id = w.category_id";

fn wallet_from_row(r: &Row) -> rusqlite::Result<Wallet> {
    Ok(Wallet {
        id: r.get("id")?,
        name: r.get("name")?,
        category_id: r.get("category_id")?,
        category_name: r.get("category_name")?,
        currency_code: r.get("currency_code")?,
        initial_balance_cents: r.get("initial_balance_cents")?,
        balance_cents: r.get("balance_cents")?,
        color: r.get("color")?,
        notes: r.get("notes")?,
        is_archived: r.get::<_, i64>("is_archived")? != 0,
        created_at: r.get("created_at")?,
    })
}

fn fetch_wallet(conn: &Connection, id: i64) -> AppResult<Wallet> {
    let sql = format!("{WALLET_SELECT} WHERE w.id = ?1");
    conn.query_row(&sql, [id], wallet_from_row).map_err(|e| match e {
        rusqlite::Error::QueryReturnedNoRows => AppError::NotFound("cartera"),
        other => other.into(),
    })
}

fn validate(name: &str) -> AppResult<()> {
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    Ok(())
}

#[tauri::command]
pub fn list_wallets(db: State<Db>, include_archived: Option<bool>) -> AppResult<Vec<Wallet>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let filter = if include_archived.unwrap_or(false) { "" } else { " WHERE w.is_archived = 0" };
    let sql = format!("{WALLET_SELECT}{filter} ORDER BY w.created_at, w.id");
    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt.query_map([], wallet_from_row)?.collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

#[tauri::command]
pub fn get_wallet(db: State<Db>, id: i64) -> AppResult<Wallet> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    fetch_wallet(&conn, id)
}

#[tauri::command]
pub fn create_wallet(
    db: State<Db>,
    name: String,
    category_id: i64,
    currency_code: String,
    initial_balance_cents: i64,
    color: Option<String>,
    notes: Option<String>,
) -> AppResult<Wallet> {
    validate(&name)?;
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    conn.execute(
        "INSERT INTO wallets (name, category_id, currency_code, initial_balance_cents, color, notes)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![name.trim(), category_id, currency_code, initial_balance_cents, color, notes],
    )?;
    fetch_wallet(&conn, conn.last_insert_rowid())
}

#[tauri::command]
pub fn update_wallet(
    db: State<Db>,
    id: i64,
    name: String,
    category_id: i64,
    currency_code: String,
    initial_balance_cents: i64,
    color: Option<String>,
    notes: Option<String>,
) -> AppResult<Wallet> {
    validate(&name)?;
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let updated = conn.execute(
        "UPDATE wallets
         SET name = ?2, category_id = ?3, currency_code = ?4,
             initial_balance_cents = ?5, color = ?6, notes = ?7
         WHERE id = ?1",
        params![id, name.trim(), category_id, currency_code, initial_balance_cents, color, notes],
    )?;
    if updated == 0 {
        return Err(AppError::NotFound("cartera"));
    }
    fetch_wallet(&conn, id)
}

#[tauri::command]
pub fn archive_wallet(db: State<Db>, id: i64, archived: bool) -> AppResult<()> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let updated = conn.execute(
        "UPDATE wallets SET is_archived = ?2 WHERE id = ?1",
        params![id, archived as i64],
    )?;
    if updated == 0 {
        return Err(AppError::NotFound("cartera"));
    }
    Ok(())
}
