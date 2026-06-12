//! Port of src-tauri/src/commands/settings.rs (DB side; HTTP fetching lives
//! in crate::market). Currencies and exchange rates are global; the settings
//! KV is per-user, with the system user (id 0) holding global market cache
//! entries like 'bonddia_price'.

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::models::{Currency, ExchangeRate, WalletCategory};
use serde::Deserialize;
use worker::D1Database;

use crate::db::{all, exec, first, CountRow, ValueRow};
use crate::jsv;

pub async fn list_currencies(db: &D1Database) -> AppResult<Vec<Currency>> {
    #[derive(Deserialize)]
    struct Row {
        code: String,
        name: String,
        symbol: String,
        decimals: i64,
    }
    let rows: Vec<Row> = all(
        db,
        "SELECT code, name, symbol, decimals FROM currencies ORDER BY code",
        vec![],
    )
    .await?;
    Ok(rows
        .into_iter()
        .map(|r| Currency {
            code: r.code,
            name: r.name,
            symbol: r.symbol,
            decimals: r.decimals,
        })
        .collect())
}

pub async fn list_wallet_categories(db: &D1Database) -> AppResult<Vec<WalletCategory>> {
    #[derive(Deserialize)]
    struct Row {
        id: i64,
        name: String,
        icon: Option<String>,
        is_system: i64,
    }
    let rows: Vec<Row> = all(
        db,
        "SELECT id, name, icon, is_system FROM wallet_categories ORDER BY id",
        vec![],
    )
    .await?;
    Ok(rows
        .into_iter()
        .map(|r| WalletCategory {
            id: r.id,
            name: r.name,
            icon: r.icon,
            is_system: r.is_system != 0,
        })
        .collect())
}

/// Latest rate per currency (excluding MXN, which is always 1.0 by definition).
pub async fn get_exchange_rates(db: &D1Database) -> AppResult<Vec<ExchangeRate>> {
    #[derive(Deserialize)]
    struct Row {
        currency_code: String,
        rate_to_mxn_micros: i64,
        as_of: String,
        source: String,
    }
    let rows: Vec<Row> = all(
        db,
        "SELECT currency_code, rate_to_mxn_micros, as_of, source
         FROM exchange_rates
         WHERE id IN (SELECT MAX(id) FROM exchange_rates GROUP BY currency_code)
         ORDER BY currency_code",
        vec![],
    )
    .await?;
    Ok(rows
        .into_iter()
        .map(|r| ExchangeRate {
            currency_code: r.currency_code,
            rate_to_mxn_micros: r.rate_to_mxn_micros,
            as_of: r.as_of,
            source: r.source,
        })
        .collect())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SetExchangeRateArgs {
    pub currency_code: String,
    pub rate_to_mxn_micros: i64,
}

pub async fn set_exchange_rate(db: &D1Database, a: SetExchangeRateArgs) -> AppResult<()> {
    if a.rate_to_mxn_micros <= 0 {
        return Err(AppError::InvalidInput(
            "el tipo de cambio debe ser positivo".into(),
        ));
    }
    if a.currency_code == "MXN" {
        return Err(AppError::InvalidInput(
            "MXN siempre vale 1.0; no se puede editar".into(),
        ));
    }
    let row: Option<CountRow> = first(
        db,
        "SELECT COUNT(*) AS n FROM currencies WHERE code = ?1",
        jsv![a.currency_code],
    )
    .await?;
    if row.map(|r| r.n).unwrap_or(0) == 0 {
        return Err(AppError::NotFound("moneda"));
    }
    exec(
        db,
        "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros, source)
         VALUES (?1, ?2, 'manual')",
        jsv![a.currency_code, a.rate_to_mxn_micros],
    )
    .await?;
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AddCurrencyArgs {
    pub code: String,
    pub name: String,
    pub symbol: String,
}

pub async fn add_currency(db: &D1Database, a: AddCurrencyArgs) -> AppResult<Currency> {
    let code = a.code.trim().to_uppercase();
    if code.len() != 3 || !code.chars().all(|c| c.is_ascii_alphabetic()) {
        return Err(AppError::InvalidInput(
            "el código debe ser ISO 4217 de 3 letras (ej. EUR)".into(),
        ));
    }
    if a.name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    exec(
        db,
        "INSERT INTO currencies (code, name, symbol, decimals) VALUES (?1, ?2, ?3, 2)",
        jsv![code, a.name.trim(), a.symbol.trim()],
    )
    .await?;
    Ok(Currency {
        code,
        name: a.name.trim().into(),
        symbol: a.symbol.trim().into(),
        decimals: 2,
    })
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetSettingArgs {
    pub key: String,
}

/// User row wins; falls back to the system user (id 0), which holds global
/// market cache entries like 'bonddia_price'.
pub async fn get_setting(db: &D1Database, uid: i64, a: GetSettingArgs) -> AppResult<Option<String>> {
    let row: Option<ValueRow> = first(
        db,
        "SELECT value FROM settings WHERE key = ?1 AND user_id IN (?2, 0)
         ORDER BY user_id DESC LIMIT 1",
        jsv![a.key, uid],
    )
    .await?;
    Ok(row.map(|r| r.value))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SetSettingArgs {
    pub key: String,
    pub value: String,
}

pub async fn set_setting(db: &D1Database, uid: i64, a: SetSettingArgs) -> AppResult<()> {
    exec(
        db,
        "INSERT INTO settings (user_id, key, value) VALUES (?1, ?2, ?3)
         ON CONFLICT(user_id, key) DO UPDATE SET value = excluded.value",
        jsv![uid, a.key, a.value],
    )
    .await?;
    Ok(())
}
