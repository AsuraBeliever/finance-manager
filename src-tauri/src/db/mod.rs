use rusqlite::Connection;
use std::path::Path;
use std::sync::Mutex;

use crate::error::AppResult;

/// Managed state: single connection guarded by a mutex (single-user desktop app).
pub struct Db(pub Mutex<Connection>);

/// Ordered migrations; each runs once, tracked in schema_migrations.
/// NEVER edit an applied migration — append a new one instead, and keep
/// docs/DATA_MODEL.md in sync.
const MIGRATIONS: &[&str] = &[
    // 1: schema
    r#"
    CREATE TABLE currencies (
      code TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      symbol TEXT NOT NULL,
      decimals INTEGER NOT NULL DEFAULT 2
    );

    CREATE TABLE exchange_rates (
      id INTEGER PRIMARY KEY,
      currency_code TEXT NOT NULL REFERENCES currencies(code),
      rate_to_mxn_micros INTEGER NOT NULL,
      as_of TEXT NOT NULL DEFAULT (datetime('now')),
      source TEXT NOT NULL DEFAULT 'manual'
    );

    CREATE TABLE wallet_categories (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      icon TEXT,
      is_system INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE wallets (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      category_id INTEGER NOT NULL REFERENCES wallet_categories(id),
      currency_code TEXT NOT NULL REFERENCES currencies(code),
      initial_balance_cents INTEGER NOT NULL DEFAULT 0,
      color TEXT,
      notes TEXT,
      is_archived INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE transaction_categories (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      kind TEXT NOT NULL CHECK (kind IN ('income','expense')),
      icon TEXT,
      color TEXT,
      is_system INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE transactions (
      id INTEGER PRIMARY KEY,
      wallet_id INTEGER NOT NULL REFERENCES wallets(id),
      kind TEXT NOT NULL CHECK (kind IN ('income','expense','transfer_in','transfer_out')),
      amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
      category_id INTEGER REFERENCES transaction_categories(id),
      transfer_group_id TEXT,
      description TEXT,
      occurred_at TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
    CREATE INDEX idx_tx_wallet ON transactions(wallet_id, occurred_at);
    CREATE INDEX idx_tx_transfer ON transactions(transfer_group_id);

    CREATE TABLE investments (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      calculator TEXT NOT NULL,
      currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
      principal_cents INTEGER NOT NULL,
      start_date TEXT NOT NULL,
      params_json TEXT NOT NULL DEFAULT '{}',
      linked_wallet_id INTEGER REFERENCES wallets(id),
      is_closed INTEGER NOT NULL DEFAULT 0,
      notes TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE investment_snapshots (
      id INTEGER PRIMARY KEY,
      investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
      value_cents INTEGER NOT NULL,
      as_of TEXT NOT NULL,
      source TEXT NOT NULL DEFAULT 'manual'
    );

    CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    "#,
    // 2: seed data
    r#"
    INSERT INTO currencies (code, name, symbol, decimals) VALUES
      ('MXN', 'Peso mexicano', '$', 2),
      ('USD', 'Dólar estadounidense', '$', 2);

    INSERT INTO wallet_categories (name, icon, is_system) VALUES
      ('Efectivo', 'banknote', 1),
      ('Tarjeta de débito', 'credit-card', 1),
      ('Tarjeta de crédito', 'credit-card', 1),
      ('Cuenta de ahorro', 'piggy-bank', 1),
      ('Inversión', 'trending-up', 1),
      ('Otro', 'wallet', 1);

    INSERT INTO transaction_categories (name, kind, icon, is_system) VALUES
      ('Salario', 'income', 'briefcase', 1),
      ('Regalo', 'income', 'gift', 1),
      ('Intereses', 'income', 'percent', 1),
      ('Otro ingreso', 'income', 'plus', 1),
      ('Comida', 'expense', 'utensils', 1),
      ('Transporte', 'expense', 'bus', 1),
      ('Hogar', 'expense', 'home', 1),
      ('Entretenimiento', 'expense', 'gamepad-2', 1),
      ('Salud', 'expense', 'heart-pulse', 1),
      ('Suscripciones', 'expense', 'repeat', 1),
      ('Otro gasto', 'expense', 'minus', 1);
    "#,
    // 3: investment movements (aportaciones y retiros)
    r#"
    CREATE TABLE investment_movements (
      id INTEGER PRIMARY KEY,
      investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
      kind TEXT NOT NULL CHECK (kind IN ('deposit','withdrawal')),
      amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
      occurred_at TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
    CREATE INDEX idx_inv_mov ON investment_movements(investment_id, occurred_at);
    "#,
];

pub fn open(path: &Path) -> AppResult<Connection> {
    let conn = Connection::open(path)?;
    conn.pragma_update(None, "foreign_keys", true)?;
    migrate(&conn)?;
    Ok(conn)
}

fn migrate(conn: &Connection) -> AppResult<()> {
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS schema_migrations (
           version INTEGER PRIMARY KEY,
           applied_at TEXT NOT NULL DEFAULT (datetime('now'))
         );",
    )?;
    let applied: i64 = conn.query_row(
        "SELECT COALESCE(MAX(version), 0) FROM schema_migrations",
        [],
        |r| r.get(0),
    )?;

    for (i, sql) in MIGRATIONS.iter().enumerate() {
        let version = (i + 1) as i64;
        if version <= applied {
            continue;
        }
        conn.execute_batch(&format!("BEGIN;\n{sql}\nCOMMIT;"))?;
        conn.execute(
            "INSERT INTO schema_migrations (version) VALUES (?1)",
            [version],
        )?;
    }
    Ok(())
}

#[cfg(test)]
pub fn open_in_memory() -> Connection {
    let conn = Connection::open_in_memory().expect("open in-memory db");
    conn.pragma_update(None, "foreign_keys", true).unwrap();
    migrate(&conn).expect("migrations apply cleanly");
    conn
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn migrations_apply_and_seed() {
        let conn = open_in_memory();
        let currencies: i64 = conn
            .query_row("SELECT COUNT(*) FROM currencies", [], |r| r.get(0))
            .unwrap();
        assert_eq!(currencies, 2);
        let wallet_cats: i64 = conn
            .query_row("SELECT COUNT(*) FROM wallet_categories", [], |r| r.get(0))
            .unwrap();
        assert_eq!(wallet_cats, 6);
        let tx_cats: i64 = conn
            .query_row("SELECT COUNT(*) FROM transaction_categories", [], |r| {
                r.get(0)
            })
            .unwrap();
        assert_eq!(tx_cats, 11);
    }

    #[test]
    fn migrations_are_idempotent() {
        let conn = open_in_memory();
        migrate(&conn).expect("second run is a no-op");
        let version: i64 = conn
            .query_row("SELECT MAX(version) FROM schema_migrations", [], |r| {
                r.get(0)
            })
            .unwrap();
        assert_eq!(version, MIGRATIONS.len() as i64);
    }
}
