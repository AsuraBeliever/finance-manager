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
// Provider: https://open.er-api.com (exchangerate-api.com free tier, no key).
// One request returns every currency relative to MXN; rates refresh daily.
// `rates[CUR]` = units of CUR per 1 MXN, so rate_to_mxn = 1 / rates[CUR].

const RATES_URL: &str = "https://open.er-api.com/v6/latest/MXN";
/// The provider refreshes daily; don't re-fetch more often than this.
const FRESH_HOURS: i64 = 6;

/// Convert the provider's "CUR per MXN" quote into micros of MXN per CUR.
/// Returns None for absurd quotes (zero, negative, NaN).
fn quote_to_micros(cur_per_mxn: f64) -> Option<i64> {
    if !cur_per_mxn.is_finite() || cur_per_mxn <= 0.0 {
        return None;
    }
    let micros = (1_000_000.0 / cur_per_mxn).round();
    (micros >= 1.0 && micros <= i64::MAX as f64).then_some(micros as i64)
}

/// Extract (code, micros) pairs for the given currencies from the provider's
/// JSON body. Pure function so the parsing is unit-testable offline.
fn parse_rates_body(body: &serde_json::Value, wanted: &[String]) -> Vec<(String, i64)> {
    let Some(rates) = body.get("rates").and_then(|r| r.as_object()) else {
        return Vec::new();
    };
    wanted
        .iter()
        .filter_map(|code| {
            let quote = rates.get(code)?.as_f64()?;
            Some((code.clone(), quote_to_micros(quote)?))
        })
        .collect()
}

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
    fn quote_inversion_to_micros() {
        // 1 MXN = 0.054 USD  ->  1 USD = 18.518518 MXN -> 18_518_519 micros
        assert_eq!(quote_to_micros(0.054), Some(18_518_519));
        assert_eq!(quote_to_micros(0.0), None);
        assert_eq!(quote_to_micros(-1.0), None);
        assert_eq!(quote_to_micros(f64::NAN), None);
    }

    #[test]
    fn parses_only_wanted_currencies() {
        let body: serde_json::Value = serde_json::from_str(
            r#"{"result":"success","rates":{"MXN":1.0,"USD":0.054,"EUR":0.05,"JPY":8.0}}"#,
        )
        .unwrap();
        let wanted = vec!["USD".to_string(), "EUR".to_string(), "GBP".to_string()];
        let parsed = parse_rates_body(&body, &wanted);
        assert_eq!(parsed.len(), 2); // GBP missing from the response
        assert!(parsed.contains(&("USD".to_string(), 18_518_519)));
        assert!(parsed.contains(&("EUR".to_string(), 20_000_000)));
    }

    #[test]
    fn malformed_body_yields_nothing() {
        let body: serde_json::Value = serde_json::from_str(r#"{"result":"error"}"#).unwrap();
        assert!(parse_rates_body(&body, &["USD".to_string()]).is_empty());
    }

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
// Series: SF43936/SF43939/SF43942/SF43945 = tasa de rendimiento de la subasta
// semanal de CETES a 28/91/182/364 días; SF61745 = tasa objetivo de Banxico
// (referencia para BONDDIA, que sigue la tasa de fondeo gubernamental).
//
// Primary source needs NO token: SieInternet's own chart endpoint
// (consultaSerieGrafica.do) returns {titulo, valores: [[date, value]]} with
// -989898.0 as the missing-value sentinel. The series id must be paired with
// the cuadro context it appears in (e.g. "SF43936,CF107,5").
// Fallback: official SIE API with the user's free token (settings key
// 'banxico_token'), in case the public endpoint changes.

/// (SIE API series id, SieInternet chart context)
fn banxico_series(kind: &str) -> AppResult<(&'static str, &'static str)> {
    Ok(match kind {
        "cetes_28" => ("SF43936", "SF43936,CF107,5"),
        "cetes_91" => ("SF43939", "SF43939,CF107,9"),
        "cetes_182" => ("SF43942", "SF43942,CF107,13"),
        "cetes_364" => ("SF43945", "SF43945,CF107,17"),
        "objetivo" => ("SF61745", "SF61745,CF101,2"),
        other => {
            return Err(AppError::InvalidInput(format!(
                "serie desconocida: {other} (válidas: cetes_28/91/182/364, objetivo)"
            )))
        }
    })
}

const SIE_SENTINEL: f64 = -989898.0;

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BanxicoRate {
    pub rate_bps: i64,
    pub date: String,
}

/// Parse SIE "oportuno" payload: bmx.series[0].datos[last] = {fecha, dato}.
/// Pure function for offline tests.
fn parse_banxico_body(body: &serde_json::Value) -> AppResult<BanxicoRate> {
    let dato = body
        .pointer("/bmx/series/0/datos")
        .and_then(|d| d.as_array())
        .and_then(|d| d.last())
        .ok_or_else(|| AppError::Internal("Banxico no regresó datos para la serie".into()))?;
    let rate: f64 = dato
        .get("dato")
        .and_then(|v| v.as_str())
        .and_then(|s| s.replace(',', "").parse().ok())
        .ok_or_else(|| {
            AppError::Internal("dato de tasa inválido en la respuesta de Banxico".into())
        })?;
    let date = dato
        .get("fecha")
        .and_then(|v| v.as_str())
        .unwrap_or_default()
        .to_string();
    Ok(BanxicoRate {
        rate_bps: (rate * 100.0).round() as i64,
        date,
    })
}

/// Parse SieInternet chart payload: {valores: [[iso_date, value]]} where
/// SIE_SENTINEL marks missing values. The latest real value wins.
fn parse_sie_internet_body(body: &serde_json::Value) -> AppResult<BanxicoRate> {
    let last = body
        .get("valores")
        .and_then(|v| v.as_array())
        .into_iter()
        .flatten()
        .filter_map(|pair| {
            let date = pair.get(0)?.as_str()?;
            let value = pair.get(1)?.as_f64()?;
            (value != SIE_SENTINEL && value > 0.0).then(|| (date.to_string(), value))
        })
        .next_back()
        .ok_or_else(|| AppError::Internal("Banxico no regresó datos para la serie".into()))?;
    Ok(BanxicoRate {
        rate_bps: (last.1 * 100.0).round() as i64,
        date: last.0,
    })
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

#[tauri::command]
pub async fn fetch_banxico_rate(db: State<'_, Db>, kind: String) -> AppResult<BanxicoRate> {
    let (series, chart_context) = banxico_series(&kind)?;

    // Tokenless public endpoint first.
    let url = format!(
        "https://www.banxico.org.mx/SieInternet/consultaSerieGrafica.do?s={chart_context}&versionSerie=LA-MAS-RECIENTE&l=es"
    );
    let primary = match get_json(&url).await {
        Ok(body) => parse_sie_internet_body(&body),
        Err(e) => Err(e),
    };
    let primary_err = match primary {
        Ok(rate) => return Ok(rate),
        Err(e) => e,
    };

    // Fallback: official SIE API if the user configured a token.
    let token = {
        let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
        conn.query_row(
            "SELECT value FROM settings WHERE key = 'banxico_token'",
            [],
            |r| r.get::<_, String>(0),
        )
        .ok()
        .filter(|t| !t.trim().is_empty())
    };
    let Some(token) = token else {
        return Err(primary_err);
    };
    let url = format!(
        "https://www.banxico.org.mx/SieAPIRest/service/v1/series/{series}/datos/oportuno?token={}",
        token.trim()
    );
    parse_banxico_body(&get_json(&url).await?)
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

#[cfg(test)]
mod banxico_tests {
    use super::*;

    #[test]
    fn parses_sie_oportuno_payload() {
        let body: serde_json::Value = serde_json::from_str(
            r#"{"bmx":{"series":[{"idSerie":"SF43939","titulo":"Cetes 91 días",
                "datos":[{"fecha":"05/06/2026","dato":"7.89"}]}]}}"#,
        )
        .unwrap();
        let rate = parse_banxico_body(&body).unwrap();
        assert_eq!(rate.rate_bps, 789);
        assert_eq!(rate.date, "05/06/2026");
    }

    #[test]
    fn rejects_empty_or_malformed_payload() {
        let body: serde_json::Value =
            serde_json::from_str(r#"{"bmx":{"series":[{"datos":[]}]}}"#).unwrap();
        assert!(parse_banxico_body(&body).is_err());
        let body: serde_json::Value =
            serde_json::from_str(r#"{"error":"token inválido"}"#).unwrap();
        assert!(parse_banxico_body(&body).is_err());
        assert!(banxico_series("cetes_90").is_err());
    }

    #[test]
    fn parses_sie_internet_chart_payload_skipping_sentinels() {
        // Latest real value wins; -989898.0 sentinel rows are ignored.
        let body: serde_json::Value = serde_json::from_str(
            r#"{"titulo":"Tasa de rendimiento","serie":"SF43936","valores":
                [["2026-05-28",6.30],["2026-06-04",6.27],
                 ["2026-06-11",6.25],["2026-06-18",-989898.0]]}"#,
        )
        .unwrap();
        let rate = parse_sie_internet_body(&body).unwrap();
        assert_eq!(rate.rate_bps, 625);
        assert_eq!(rate.date, "2026-06-11");
    }

    #[test]
    fn sie_internet_rejects_empty_or_all_sentinel() {
        let body: serde_json::Value =
            serde_json::from_str(r#"{"valores":[["2026-06-11",-989898.0]]}"#).unwrap();
        assert!(parse_sie_internet_body(&body).is_err());
        let body: serde_json::Value = serde_json::from_str(r#"{}"#).unwrap();
        assert!(parse_sie_internet_body(&body).is_err());
    }
}
