use tauri::State;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::models::{Currency, ExchangeRate, WalletCategory};

#[tauri::command]
pub fn list_currencies(db: State<Db>) -> AppResult<Vec<Currency>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let mut stmt =
        conn.prepare("SELECT code, name, symbol, decimals FROM currencies ORDER BY code")?;
    let rows = stmt
        .query_map([], |r| {
            Ok(Currency {
                code: r.get(0)?,
                name: r.get(1)?,
                symbol: r.get(2)?,
                decimals: r.get(3)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

#[tauri::command]
pub fn list_wallet_categories(db: State<Db>) -> AppResult<Vec<WalletCategory>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let mut stmt =
        conn.prepare("SELECT id, name, icon, is_system FROM wallet_categories ORDER BY id")?;
    let rows = stmt
        .query_map([], |r| {
            Ok(WalletCategory {
                id: r.get(0)?,
                name: r.get(1)?,
                icon: r.get(2)?,
                is_system: r.get::<_, i64>(3)? != 0,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

/// Latest rate per currency (excluding MXN, which is always 1.0 by definition).
#[tauri::command]
pub fn get_exchange_rates(db: State<Db>) -> AppResult<Vec<ExchangeRate>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let mut stmt = conn.prepare(
        "SELECT currency_code, rate_to_mxn_micros, as_of, source
         FROM exchange_rates
         WHERE id IN (SELECT MAX(id) FROM exchange_rates GROUP BY currency_code)
         ORDER BY currency_code",
    )?;
    let rows = stmt
        .query_map([], |r| {
            Ok(ExchangeRate {
                currency_code: r.get(0)?,
                rate_to_mxn_micros: r.get(1)?,
                as_of: r.get(2)?,
                source: r.get(3)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

#[tauri::command]
pub fn set_exchange_rate(
    db: State<Db>,
    currency_code: String,
    rate_to_mxn_micros: i64,
) -> AppResult<()> {
    if rate_to_mxn_micros <= 0 {
        return Err(AppError::InvalidInput("el tipo de cambio debe ser positivo".into()));
    }
    if currency_code == "MXN" {
        return Err(AppError::InvalidInput("MXN siempre vale 1.0; no se puede editar".into()));
    }
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let exists: bool = conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM currencies WHERE code = ?1)",
        [&currency_code],
        |r| r.get(0),
    )?;
    if !exists {
        return Err(AppError::NotFound("moneda"));
    }
    conn.execute(
        "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros, source)
         VALUES (?1, ?2, 'manual')",
        rusqlite::params![currency_code, rate_to_mxn_micros],
    )?;
    Ok(())
}

#[tauri::command]
pub fn add_currency(
    db: State<Db>,
    code: String,
    name: String,
    symbol: String,
) -> AppResult<Currency> {
    let code = code.trim().to_uppercase();
    if code.len() != 3 || !code.chars().all(|c| c.is_ascii_alphabetic()) {
        return Err(AppError::InvalidInput(
            "el código debe ser ISO 4217 de 3 letras (ej. EUR)".into(),
        ));
    }
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    conn.execute(
        "INSERT INTO currencies (code, name, symbol, decimals) VALUES (?1, ?2, ?3, 2)",
        rusqlite::params![code, name.trim(), symbol.trim()],
    )?;
    Ok(Currency { code, name: name.trim().into(), symbol: symbol.trim().into(), decimals: 2 })
}
