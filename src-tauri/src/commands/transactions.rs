use rusqlite::{params, Connection, Row};
use tauri::State;
use uuid::Uuid;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::models::{Transaction, TransactionCategory};

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

fn wallet_exists(conn: &Connection, id: i64) -> AppResult<()> {
    let exists: bool = conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM wallets WHERE id = ?1)",
        [id],
        |r| r.get(0),
    )?;
    if exists {
        Ok(())
    } else {
        Err(AppError::NotFound("cartera"))
    }
}

// Plain functions over &Connection so the semantics are unit-testable
// without Tauri state.

pub fn insert_simple(
    conn: &Connection,
    wallet_id: i64,
    kind: &str, // 'income' | 'expense'
    amount_cents: i64,
    category_id: Option<i64>,
    description: Option<&str>,
    occurred_at: &str,
) -> AppResult<i64> {
    validate_amount(amount_cents)?;
    validate_date(occurred_at)?;
    wallet_exists(conn, wallet_id)?;
    conn.execute(
        "INSERT INTO transactions (wallet_id, kind, amount_cents, category_id, description, occurred_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![wallet_id, kind, amount_cents, category_id, description, occurred_at],
    )?;
    Ok(conn.last_insert_rowid())
}

pub fn insert_transfer(
    conn: &mut Connection,
    from_wallet_id: i64,
    to_wallet_id: i64,
    amount_from_cents: i64,
    amount_to_cents: i64,
    description: Option<&str>,
    occurred_at: &str,
) -> AppResult<String> {
    validate_amount(amount_from_cents)?;
    validate_amount(amount_to_cents)?;
    validate_date(occurred_at)?;
    if from_wallet_id == to_wallet_id {
        return Err(AppError::InvalidInput(
            "la cartera origen y destino deben ser distintas".into(),
        ));
    }
    wallet_exists(conn, from_wallet_id)?;
    wallet_exists(conn, to_wallet_id)?;

    let group_id = Uuid::new_v4().to_string();
    // Both legs in one SQL transaction: a transfer never half-applies.
    let tx = conn.transaction()?;
    tx.execute(
        "INSERT INTO transactions (wallet_id, kind, amount_cents, transfer_group_id, description, occurred_at)
         VALUES (?1, 'transfer_out', ?2, ?3, ?4, ?5)",
        params![from_wallet_id, amount_from_cents, group_id, description, occurred_at],
    )?;
    tx.execute(
        "INSERT INTO transactions (wallet_id, kind, amount_cents, transfer_group_id, description, occurred_at)
         VALUES (?1, 'transfer_in', ?2, ?3, ?4, ?5)",
        params![to_wallet_id, amount_to_cents, group_id, description, occurred_at],
    )?;
    tx.commit()?;
    Ok(group_id)
}

/// Deleting any leg of a transfer removes both legs.
pub fn delete_tx(conn: &Connection, id: i64) -> AppResult<()> {
    let group: Option<Option<String>> = conn
        .query_row(
            "SELECT transfer_group_id FROM transactions WHERE id = ?1",
            [id],
            |r| r.get(0),
        )
        .map(Some)
        .or_else(|e| match e {
            rusqlite::Error::QueryReturnedNoRows => Ok(None),
            other => Err(other),
        })?;

    match group {
        None => Err(AppError::NotFound("transacción")),
        Some(Some(group_id)) => {
            conn.execute(
                "DELETE FROM transactions WHERE transfer_group_id = ?1",
                [group_id],
            )?;
            Ok(())
        }
        Some(None) => {
            conn.execute("DELETE FROM transactions WHERE id = ?1", [id])?;
            Ok(())
        }
    }
}

fn tx_from_row(r: &Row) -> rusqlite::Result<Transaction> {
    Ok(Transaction {
        id: r.get("id")?,
        wallet_id: r.get("wallet_id")?,
        wallet_name: r.get("wallet_name")?,
        kind: r.get("kind")?,
        amount_cents: r.get("amount_cents")?,
        category_id: r.get("category_id")?,
        category_name: r.get("category_name")?,
        transfer_group_id: r.get("transfer_group_id")?,
        description: r.get("description")?,
        occurred_at: r.get("occurred_at")?,
        // Legacy desktop DB (read-only backup) never tracked a time.
        occurred_time: None,
        created_at: r.get("created_at")?,
    })
}

#[derive(Default, serde::Deserialize)]
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

pub fn query_transactions(conn: &Connection, f: &TxFilter) -> AppResult<Vec<Transaction>> {
    let mut sql = String::from(
        "SELECT t.id, t.wallet_id, w.name AS wallet_name, t.kind, t.amount_cents,
                t.category_id, tc.name AS category_name, t.transfer_group_id,
                t.description, t.occurred_at, t.created_at
         FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id
         LEFT JOIN transaction_categories tc ON tc.id = t.category_id
         WHERE 1=1",
    );
    let mut args: Vec<Box<dyn rusqlite::types::ToSql>> = Vec::new();
    if let Some(wid) = f.wallet_id {
        sql.push_str(" AND t.wallet_id = ?");
        args.push(Box::new(wid));
    }
    if let Some(kind) = &f.kind {
        sql.push_str(" AND t.kind = ?");
        args.push(Box::new(kind.clone()));
    }
    if let Some(cid) = f.category_id {
        sql.push_str(" AND t.category_id = ?");
        args.push(Box::new(cid));
    }
    if let Some(from) = &f.from {
        sql.push_str(" AND t.occurred_at >= ?");
        args.push(Box::new(from.clone()));
    }
    if let Some(to) = &f.to {
        sql.push_str(" AND t.occurred_at <= ?");
        args.push(Box::new(to.clone()));
    }
    sql.push_str(" ORDER BY t.occurred_at DESC, t.id DESC LIMIT ? OFFSET ?");
    args.push(Box::new(f.limit.unwrap_or(100)));
    args.push(Box::new(f.offset.unwrap_or(0)));

    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt
        .query_map(
            rusqlite::params_from_iter(args.iter().map(|a| a.as_ref())),
            tx_from_row,
        )?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

// ---- Tauri commands (thin wrappers) ----

#[tauri::command]
pub fn add_income(
    db: State<Db>,
    wallet_id: i64,
    amount_cents: i64,
    category_id: Option<i64>,
    description: Option<String>,
    occurred_at: String,
) -> AppResult<i64> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    insert_simple(
        &conn,
        wallet_id,
        "income",
        amount_cents,
        category_id,
        description.as_deref(),
        &occurred_at,
    )
}

#[tauri::command]
pub fn add_expense(
    db: State<Db>,
    wallet_id: i64,
    amount_cents: i64,
    category_id: Option<i64>,
    description: Option<String>,
    occurred_at: String,
) -> AppResult<i64> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    insert_simple(
        &conn,
        wallet_id,
        "expense",
        amount_cents,
        category_id,
        description.as_deref(),
        &occurred_at,
    )
}

#[tauri::command]
pub fn add_transfer(
    db: State<Db>,
    from_wallet_id: i64,
    to_wallet_id: i64,
    amount_from_cents: i64,
    amount_to_cents: i64,
    description: Option<String>,
    occurred_at: String,
) -> AppResult<String> {
    let mut conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    insert_transfer(
        &mut conn,
        from_wallet_id,
        to_wallet_id,
        amount_from_cents,
        amount_to_cents,
        description.as_deref(),
        &occurred_at,
    )
}

#[tauri::command]
pub fn list_transactions(db: State<Db>, filter: TxFilter) -> AppResult<Vec<Transaction>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    query_transactions(&conn, &filter)
}

#[tauri::command]
pub fn delete_transaction(db: State<Db>, id: i64) -> AppResult<()> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    delete_tx(&conn, id)
}

#[tauri::command]
pub fn list_transaction_categories(db: State<Db>) -> AppResult<Vec<TransactionCategory>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let mut stmt = conn.prepare(
        "SELECT id, name, kind, icon, color, is_system FROM transaction_categories ORDER BY kind, id",
    )?;
    let rows = stmt
        .query_map([], |r| {
            Ok(TransactionCategory {
                id: r.get(0)?,
                name: r.get(1)?,
                kind: r.get(2)?,
                icon: r.get(3)?,
                color: r.get(4)?,
                is_system: r.get::<_, i64>(5)? != 0,
                // Single-user desktop DB: no per-user hiding.
                is_hidden: false,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

#[tauri::command]
pub fn create_transaction_category(db: State<Db>, name: String, kind: String) -> AppResult<i64> {
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    if kind != "income" && kind != "expense" {
        return Err(AppError::InvalidInput("tipo inválido".into()));
    }
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    conn.execute(
        "INSERT INTO transaction_categories (name, kind) VALUES (?1, ?2)",
        params![name.trim(), kind],
    )?;
    Ok(conn.last_insert_rowid())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;

    fn balance(conn: &Connection, wallet_id: i64) -> i64 {
        conn.query_row(
            "SELECT w.initial_balance_cents + COALESCE(SUM(
               CASE t.kind WHEN 'income' THEN t.amount_cents
                           WHEN 'transfer_in' THEN t.amount_cents
                           ELSE -t.amount_cents END), 0)
             FROM wallets w LEFT JOIN transactions t ON t.wallet_id = w.id
             WHERE w.id = ?1",
            [wallet_id],
            |r| r.get(0),
        )
        .unwrap()
    }

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
    fn income_and_expense_move_balance() {
        let conn = open_in_memory();
        let w = make_wallet(&conn, "Efectivo", 50_000); // $500.00
        insert_simple(
            &conn,
            w,
            "income",
            100_000,
            None,
            Some("sueldo"),
            "2026-06-01",
        )
        .unwrap();
        insert_simple(
            &conn,
            w,
            "expense",
            25_000,
            None,
            Some("comida"),
            "2026-06-02",
        )
        .unwrap();
        // 500.00 + 1000.00 - 250.00 = 1250.00
        assert_eq!(balance(&conn, w), 125_000);
    }

    #[test]
    fn transfer_moves_both_balances_atomically() {
        let mut conn = open_in_memory();
        let a = make_wallet(&conn, "Débito", 100_000);
        let b = make_wallet(&conn, "Ahorro", 0);
        insert_transfer(&mut conn, a, b, 30_000, 30_000, None, "2026-06-05").unwrap();
        assert_eq!(balance(&conn, a), 70_000);
        assert_eq!(balance(&conn, b), 30_000);
    }

    #[test]
    fn deleting_one_transfer_leg_removes_the_pair() {
        let mut conn = open_in_memory();
        let a = make_wallet(&conn, "A", 100_000);
        let b = make_wallet(&conn, "B", 0);
        insert_transfer(&mut conn, a, b, 10_000, 10_000, None, "2026-06-05").unwrap();
        let leg_id: i64 = conn
            .query_row(
                "SELECT id FROM transactions WHERE kind = 'transfer_in'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        delete_tx(&conn, leg_id).unwrap();
        let remaining: i64 = conn
            .query_row("SELECT COUNT(*) FROM transactions", [], |r| r.get(0))
            .unwrap();
        assert_eq!(remaining, 0);
        assert_eq!(balance(&conn, a), 100_000);
        assert_eq!(balance(&conn, b), 0);
    }

    #[test]
    fn rejects_invalid_input() {
        let mut conn = open_in_memory();
        let w = make_wallet(&conn, "A", 0);
        assert!(insert_simple(&conn, w, "income", 0, None, None, "2026-06-01").is_err());
        assert!(insert_simple(&conn, w, "income", 100, None, None, "junio 1").is_err());
        assert!(insert_simple(&conn, 999, "income", 100, None, None, "2026-06-01").is_err());
        assert!(insert_transfer(&mut conn, w, w, 100, 100, None, "2026-06-01").is_err());
    }

    #[test]
    fn filters_by_wallet_and_kind() {
        let conn = open_in_memory();
        let a = make_wallet(&conn, "A", 0);
        let b = make_wallet(&conn, "B", 0);
        insert_simple(&conn, a, "income", 100, None, None, "2026-06-01").unwrap();
        insert_simple(&conn, a, "expense", 50, None, None, "2026-06-02").unwrap();
        insert_simple(&conn, b, "income", 200, None, None, "2026-06-03").unwrap();

        let all = query_transactions(&conn, &TxFilter::default()).unwrap();
        assert_eq!(all.len(), 3);

        let only_a = query_transactions(
            &conn,
            &TxFilter {
                wallet_id: Some(a),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(only_a.len(), 2);

        let incomes = query_transactions(
            &conn,
            &TxFilter {
                kind: Some("income".into()),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(incomes.len(), 2);
    }
}
