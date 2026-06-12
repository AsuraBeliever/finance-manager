use tauri::State;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::models::{Currency, ExchangeRate, WalletCategory};
// Pure parsing for market-data sources is shared with the Cloudflare Worker.
use finanzas_core::market::{
    banxico_series_url, parse_bonddia_page, parse_rates_body, parse_sie_internet_body,
    parse_sie_internet_history, BanxicoRate, BONDDIA_URL, BROWSER_UA, RATES_URL,
};

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
        return Err(AppError::InvalidInput(
            "el tipo de cambio debe ser positivo".into(),
        ));
    }
    if currency_code == "MXN" {
        return Err(AppError::InvalidInput(
            "MXN siempre vale 1.0; no se puede editar".into(),
        ));
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

// ---- automatic rate fetching ----
//
// Provider: open.er-api.com (see finanzas_core::market for the parsing).

/// The provider refreshes daily; don't re-fetch more often than this.
const FRESH_HOURS: i64 = 6;

fn non_mxn_currencies(conn: &rusqlite::Connection) -> AppResult<Vec<String>> {
    let mut stmt = conn.prepare("SELECT code FROM currencies WHERE code != 'MXN'")?;
    let rows = stmt
        .query_map([], |r| r.get::<_, String>(0))?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

/// True when every non-MXN currency already has an API rate newer than
/// FRESH_HOURS, in which case fetching again is pointless.
fn rates_are_fresh(conn: &rusqlite::Connection) -> AppResult<bool> {
    let stale: i64 = conn.query_row(
        "SELECT COUNT(*) FROM currencies c
         WHERE c.code != 'MXN' AND NOT EXISTS (
           SELECT 1 FROM exchange_rates r
           WHERE r.currency_code = c.code AND r.source = 'api'
             AND r.as_of > datetime('now', ?1)
         )",
        [format!("-{FRESH_HOURS} hours")],
        |r| r.get(0),
    )?;
    Ok(stale == 0)
}

/// Fetch current rates and store one 'api' row per known currency.
/// Holding the DB lock across the HTTP await is forbidden: read the currency
/// list, drop the lock, fetch, then re-lock to write.
pub async fn fetch_and_store_rates(db: &Db, force: bool) -> AppResult<usize> {
    let wanted = {
        let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
        if !force && rates_are_fresh(&conn)? {
            return Ok(0);
        }
        non_mxn_currencies(&conn)?
    };
    if wanted.is_empty() {
        return Ok(0);
    }

    let body: serde_json::Value = reqwest::Client::new()
        .get(RATES_URL)
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("no se pudo consultar el tipo de cambio: {e}")))?
        .json()
        .await
        .map_err(|e| AppError::Internal(format!("respuesta inválida del proveedor: {e}")))?;

    let parsed = parse_rates_body(&body, &wanted);
    if parsed.is_empty() {
        return Err(AppError::Internal(
            "el proveedor no regresó tasas para tus monedas".into(),
        ));
    }

    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    for (code, micros) in &parsed {
        conn.execute(
            "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros, source)
             VALUES (?1, ?2, 'api')",
            rusqlite::params![code, micros],
        )?;
    }
    Ok(parsed.len())
}

/// Manual refresh from Ajustes; always hits the provider.
#[tauri::command]
pub async fn fetch_exchange_rates(db: State<'_, Db>) -> AppResult<usize> {
    fetch_and_store_rates(&db, true).await
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
    Ok(Currency {
        code,
        name: name.trim().into(),
        symbol: symbol.trim().into(),
        decimals: 2,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;

    #[test]
    fn freshness_check_tracks_api_rows() {
        let conn = open_in_memory();
        // USD seeded with no rate at all -> stale
        assert!(!rates_are_fresh(&conn).unwrap());
        conn.execute(
            "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros, source)
             VALUES ('USD', 18500000, 'api')",
            [],
        )
        .unwrap();
        assert!(rates_are_fresh(&conn).unwrap());
        // manual rows don't count as fresh API data
        conn.execute("UPDATE exchange_rates SET source = 'manual'", [])
            .unwrap();
        assert!(!rates_are_fresh(&conn).unwrap());
    }
}

// ---- Banxico (tasas de CETES y tasa objetivo) ----
//
// Parsing and series catalog live in finanzas_core::market; this side only
// does the reqwest fetch.

/// Fetch the full history of a Banxico series (tokenless).
pub(crate) async fn fetch_series_history(kind: &str) -> AppResult<Vec<(String, i64)>> {
    let url = banxico_series_url(kind)?;
    let history = parse_sie_internet_history(&get_json(&url).await?);
    if history.is_empty() {
        return Err(AppError::Internal(
            "Banxico no regresó datos para la serie".into(),
        ));
    }
    Ok(history)
}

async fn get_json(url: &str) -> AppResult<serde_json::Value> {
    reqwest::Client::new()
        .get(url)
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("no se pudo consultar Banxico: {e}")))?
        .json()
        .await
        .map_err(|e| AppError::Internal(format!("respuesta inválida de Banxico: {e}")))
}

/// Tokenless fetch from the public SieInternet chart endpoint.
pub(crate) async fn fetch_rate_tokenless(kind: &str) -> AppResult<BanxicoRate> {
    parse_sie_internet_body(&get_json(&banxico_series_url(kind)?).await?)
}

#[tauri::command]
pub async fn fetch_banxico_rate(kind: String) -> AppResult<BanxicoRate> {
    fetch_rate_tokenless(&kind).await
}

#[tauri::command]
pub fn get_setting(db: State<Db>, key: String) -> AppResult<Option<String>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let value = conn
        .query_row("SELECT value FROM settings WHERE key = ?1", [&key], |r| {
            r.get::<_, String>(0)
        })
        .map(Some)
        .or_else(|e| match e {
            rusqlite::Error::QueryReturnedNoRows => Ok(None),
            other => Err(other),
        })?;
    Ok(value)
}

#[tauri::command]
pub fn set_setting(db: State<Db>, key: String, value: String) -> AppResult<()> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    conn.execute(
        "INSERT INTO settings (key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        rusqlite::params![key, value],
    )?;
    Ok(())
}

// ---- market data cache (rate history + crypto prices) ----

/// Upsert the full 'objetivo' history used by the bonddia calculator, and
/// refresh prices for every crypto symbol held in investments. Silent-fail
/// friendly: callers decide whether errors matter.
pub async fn refresh_market_data(db: &Db) -> AppResult<()> {
    // 1) Banxico target-rate history. The series has ~6,700 daily rows, so
    // skip the fetch when the cache is already current and write everything
    // in ONE transaction (row-by-row autocommit locked the DB for seconds).
    let history_fresh = {
        let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
        conn.query_row(
            "SELECT EXISTS(SELECT 1 FROM rate_history
             WHERE series = 'objetivo' AND date >= date('now', '-3 days'))",
            [],
            |r| r.get::<_, bool>(0),
        )?
    };
    if !history_fresh {
        let history = fetch_series_history("objetivo").await?;
        let mut conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
        let tx = conn.transaction()?;
        for (date, rate_bps) in &history {
            tx.execute(
                "INSERT INTO rate_history (series, date, rate_bps) VALUES ('objetivo', ?1, ?2)
                 ON CONFLICT(series, date) DO UPDATE SET rate_bps = excluded.rate_bps",
                rusqlite::params![date, rate_bps],
            )?;
        }
        tx.commit()?;
    }

    // 2) Official BONDDIA price (exact valuation for positions tracking
    // títulos). Best effort: a scrape failure must not block the rest.
    if let Ok((price_micros, date)) = fetch_bonddia_price().await {
        let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
        conn.execute(
            "INSERT INTO settings (key, value) VALUES ('bonddia_price', ?1)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            [serde_json::json!({ "price_micros": price_micros, "date": date }).to_string()],
        )?;
    }

    // 3) Crypto prices for held symbols.
    let symbols: Vec<String> = {
        let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
        let mut stmt = conn.prepare(
            "SELECT DISTINCT json_extract(params_json, '$.symbol') FROM investments
             WHERE calculator = 'crypto' AND json_extract(params_json, '$.symbol') IS NOT NULL",
        )?;
        let rows = stmt
            .query_map([], |r| r.get::<_, String>(0))?
            .collect::<Result<Vec<_>, _>>()?;
        rows
    };
    if symbols.is_empty() {
        return Ok(());
    }
    let ids: Vec<&str> = symbols
        .iter()
        .filter_map(|s| crate::investments::crypto::coingecko_id(s))
        .collect();
    if ids.is_empty() {
        return Ok(());
    }
    let url = format!(
        "https://api.coingecko.com/api/v3/simple/price?ids={}&vs_currencies=mxn,usd",
        ids.join(",")
    );
    let body = get_json(&url).await?;
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    for symbol in &symbols {
        let Some(id) = crate::investments::crypto::coingecko_id(symbol) else {
            continue;
        };
        let Some(mxn) = body.pointer(&format!("/{id}/mxn")).and_then(|v| v.as_f64()) else {
            continue;
        };
        let usd = body.pointer(&format!("/{id}/usd")).and_then(|v| v.as_f64());
        conn.execute(
            "INSERT INTO crypto_prices (symbol, price_mxn_cents, price_usd_cents, as_of)
             VALUES (?1, ?2, ?3, datetime('now'))
             ON CONFLICT(symbol) DO UPDATE SET price_mxn_cents = excluded.price_mxn_cents,
               price_usd_cents = excluded.price_usd_cents, as_of = excluded.as_of",
            rusqlite::params![
                symbol,
                (mxn * 100.0).round() as i64,
                usd.map(|u| (u * 100.0).round() as i64)
            ],
        )?;
    }
    Ok(())
}

/// On-demand refresh (e.g. right after creating a crypto investment).
#[tauri::command]
pub async fn refresh_market_data_cmd(db: State<'_, Db>) -> AppResult<()> {
    refresh_market_data(&db).await
}

// ---- BONDDIA official daily price (cetesdirecto) ----
//
// Parsing lives in finanzas_core::market; the page blocks non-browser user
// agents, hence the UA header.

pub(crate) async fn fetch_bonddia_price() -> AppResult<(i64, String)> {
    let html = reqwest::Client::new()
        .get(BONDDIA_URL)
        .header(reqwest::header::USER_AGENT, BROWSER_UA)
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("no se pudo consultar cetesdirecto: {e}")))?
        .text()
        .await
        .map_err(|e| AppError::Internal(format!("respuesta inválida de cetesdirecto: {e}")))?;
    parse_bonddia_page(&html).ok_or_else(|| {
        AppError::Internal("no se encontró el precio de BONDDIA en la página".into())
    })
}
