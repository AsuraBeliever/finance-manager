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
        // Card skins are a cloud-only feature; the legacy local DB has no such
        // column. This desktop path is dead (the shell loads the web app).
        skin: None,
        notes: r.get("notes")?,
        is_archived: r.get::<_, i64>("is_archived")? != 0,
        // Yield-bearing wallets are a cloud-only feature; the legacy local DB
        // has no such columns. This desktop path is dead (shell loads the web).
        yield_rate_bps: None,
        yield_frequency: None,
        yield_anchor_date: None,
        created_at: r.get("created_at")?,
    })
}

fn fetch_wallet(conn: &Connection, id: i64) -> AppResult<Wallet> {
    let sql = format!("{WALLET_SELECT} WHERE w.id = ?1");
    conn.query_row(&sql, [id], wallet_from_row)
        .map_err(|e| match e {
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
    let filter = if include_archived.unwrap_or(false) {
        ""
    } else {
        " WHERE w.is_archived = 0"
    };
    let sql = format!("{WALLET_SELECT}{filter} ORDER BY w.created_at, w.id");
    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt
        .query_map([], wallet_from_row)?
        .collect::<Result<Vec<_>, _>>()?;
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
#[allow(clippy::too_many_arguments)]
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
        params![
            id,
            name.trim(),
            category_id,
            currency_code,
            initial_balance_cents,
            color,
            notes
        ],
    )?;
    if updated == 0 {
        return Err(AppError::NotFound("cartera"));
    }
    fetch_wallet(&conn, id)
}

/// Deletes the wallet and everything that references it: its transactions,
/// the sibling legs of its transfers (an orphan half-transfer would corrupt
/// the other wallet's history), and any investment links.
pub fn delete_wallet_tx(conn: &mut Connection, id: i64) -> AppResult<()> {
    let tx = conn.transaction()?;
    tx.execute(
        "DELETE FROM transactions WHERE transfer_group_id IN (
           SELECT transfer_group_id FROM transactions
           WHERE wallet_id = ?1 AND transfer_group_id IS NOT NULL)",
        [id],
    )?;
    tx.execute("DELETE FROM transactions WHERE wallet_id = ?1", [id])?;
    tx.execute(
        "UPDATE investments SET linked_wallet_id = NULL WHERE linked_wallet_id = ?1",
        [id],
    )?;
    let deleted = tx.execute("DELETE FROM wallets WHERE id = ?1", [id])?;
    if deleted == 0 {
        return Err(AppError::NotFound("cartera"));
    }
    tx.commit()?;
    Ok(())
}

#[tauri::command]
pub fn delete_wallet(db: State<Db>, id: i64) -> AppResult<()> {
    let mut conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    delete_wallet_tx(&mut conn, id)
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::commands::transactions::{insert_simple, insert_transfer};
    use crate::db::open_in_memory;

    fn make_wallet(conn: &Connection, name: &str, initial_cents: i64) -> i64 {
        conn.execute(
            "INSERT INTO wallets (name, category_id, currency_code, initial_balance_cents)
             VALUES (?1, 1, 'MXN', ?2)",
            params![name, initial_cents],
        )
        .unwrap();
        conn.last_insert_rowid()
    }

    #[test]
    fn delete_wallet_removes_transactions_and_transfer_pairs() {
        let mut conn = open_in_memory();
        let a = make_wallet(&conn, "A", 100_000);
        let b = make_wallet(&conn, "B", 0);
        insert_simple(&conn, a, "income", 5_000, None, None, "2026-06-01").unwrap();
        insert_simple(&conn, b, "expense", 1_000, None, None, "2026-06-01").unwrap();
        insert_transfer(&mut conn, a, b, 20_000, 20_000, None, "2026-06-02").unwrap();
        conn.execute(
            "INSERT INTO investments (name, calculator, principal_cents, start_date, linked_wallet_id)
             VALUES ('inv', 'manual', 1000, '2026-01-01', ?1)",
            [a],
        )
        .unwrap();

        delete_wallet_tx(&mut conn, a).unwrap();

        let wallets: i64 = conn
            .query_row("SELECT COUNT(*) FROM wallets", [], |r| r.get(0))
            .unwrap();
        assert_eq!(wallets, 1);
        // only B's own expense survives; A's income and BOTH transfer legs are gone
        let txs: i64 = conn
            .query_row("SELECT COUNT(*) FROM transactions", [], |r| r.get(0))
            .unwrap();
        assert_eq!(txs, 1);
        let linked: Option<i64> = conn
            .query_row("SELECT linked_wallet_id FROM investments", [], |r| r.get(0))
            .unwrap();
        assert_eq!(linked, None);
        assert!(delete_wallet_tx(&mut conn, a).is_err()); // already gone
    }
}
