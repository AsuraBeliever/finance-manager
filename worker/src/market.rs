//! Market-data fetching via the Workers Fetch API (reqwest replacement).
//! Parsing is shared with the desktop app in finanzas_core::market.
//! Free tier allows 50 subrequests per invocation — the catalog (5 Banxico
//! calls) plus the daily refresh (3 sources) sit well below that.

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::market::{
    self, parse_bonddia_page, parse_rates_body, parse_sie_internet_body,
    parse_sie_internet_history, BanxicoRate,
};
use worker::{D1Database, Fetch, Headers, Method, Request, RequestInit};

use crate::db::{all, batch_chunks, exec, first, stmt, CountRow};
use crate::jsv;

async fn get_json(url: &str) -> AppResult<serde_json::Value> {
    let mut resp = Fetch::Url(
        url.parse()
            .map_err(|e| AppError::Internal(format!("URL inválida: {e}")))?,
    )
    .send()
    .await
    .map_err(|e| AppError::Internal(format!("no se pudo consultar {url}: {e}")))?;
    resp.json()
        .await
        .map_err(|e| AppError::Internal(format!("respuesta inválida de {url}: {e}")))
}

async fn get_text_with_ua(url: &str, ua: &str) -> AppResult<String> {
    let headers = Headers::new();
    headers
        .set("User-Agent", ua)
        .map_err(|e| AppError::Internal(e.to_string()))?;
    let mut init = RequestInit::new();
    init.with_method(Method::Get).with_headers(headers);
    let req = Request::new_with_init(url, &init)
        .map_err(|e| AppError::Internal(format!("petición inválida: {e}")))?;
    let mut resp = Fetch::Request(req)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("no se pudo consultar {url}: {e}")))?;
    resp.text()
        .await
        .map_err(|e| AppError::Internal(format!("respuesta inválida de {url}: {e}")))
}

// ---- Banxico ----

/// Tokenless fetch from the public SieInternet chart endpoint.
pub async fn fetch_rate_tokenless(kind: &str) -> AppResult<BanxicoRate> {
    parse_sie_internet_body(&get_json(&market::banxico_series_url(kind)?).await?)
}

/// Fetch the full history of a Banxico series (tokenless).
pub async fn fetch_series_history(kind: &str) -> AppResult<Vec<(String, i64)>> {
    let history = parse_sie_internet_history(&get_json(&market::banxico_series_url(kind)?).await?);
    if history.is_empty() {
        return Err(AppError::Internal(
            "Banxico no regresó datos para la serie".into(),
        ));
    }
    Ok(history)
}

// ---- exchange rates (open.er-api.com) ----

/// The provider refreshes daily; don't re-fetch more often than this.
const FRESH_HOURS: i64 = 6;

async fn non_mxn_currencies(db: &D1Database) -> AppResult<Vec<String>> {
    #[derive(serde::Deserialize)]
    struct Row {
        code: String,
    }
    let rows: Vec<Row> = all(db, "SELECT code FROM currencies WHERE code != 'MXN'", vec![]).await?;
    Ok(rows.into_iter().map(|r| r.code).collect())
}

/// True when every non-MXN currency already has an API rate newer than
/// FRESH_HOURS, in which case fetching again is pointless.
async fn rates_are_fresh(db: &D1Database) -> AppResult<bool> {
    let row: Option<CountRow> = first(
        db,
        "SELECT COUNT(*) AS n FROM currencies c
         WHERE c.code != 'MXN' AND NOT EXISTS (
           SELECT 1 FROM exchange_rates r
           WHERE r.currency_code = c.code AND r.source = 'api'
             AND r.as_of > datetime('now', ?1)
         )",
        jsv![format!("-{FRESH_HOURS} hours")],
    )
    .await?;
    Ok(row.map(|r| r.n).unwrap_or(0) == 0)
}

/// Fetch current rates and store one 'api' row per known currency.
pub async fn fetch_and_store_rates(db: &D1Database, force: bool) -> AppResult<usize> {
    if !force && rates_are_fresh(db).await? {
        return Ok(0);
    }
    let wanted = non_mxn_currencies(db).await?;
    if wanted.is_empty() {
        return Ok(0);
    }

    let body = get_json(market::RATES_URL).await?;
    let parsed = parse_rates_body(&body, &wanted);
    if parsed.is_empty() {
        return Err(AppError::Internal(
            "el proveedor no regresó tasas para tus monedas".into(),
        ));
    }
    for (code, micros) in &parsed {
        exec(
            db,
            "INSERT INTO exchange_rates (currency_code, rate_to_mxn_micros, source)
             VALUES (?1, ?2, 'api')",
            jsv![code, micros],
        )
        .await?;
    }
    Ok(parsed.len())
}

// ---- market data cache (rate history + bonddia price + crypto prices) ----

pub async fn fetch_bonddia_price() -> AppResult<(i64, String)> {
    let html = get_text_with_ua(market::BONDDIA_URL, market::BROWSER_UA).await?;
    parse_bonddia_page(&html).ok_or_else(|| {
        AppError::Internal("no se encontró el precio de BONDDIA en la página".into())
    })
}

/// Upsert the full 'objetivo' history used by the bonddia calculator, refresh
/// the official BONDDIA price, and refresh prices for every crypto symbol
/// held in investments. Silent-fail friendly: callers decide whether errors
/// matter.
pub async fn refresh_market_data(db: &D1Database) -> AppResult<()> {
    // 1) Banxico target-rate history (~6,700 daily rows). Skip the fetch when
    // the cache is already current; write in chunked batches (D1 caps bound
    // parameters at 100 per statement).
    let history_fresh = first::<CountRow>(
        db,
        "SELECT COUNT(*) AS n FROM rate_history
         WHERE series = 'objetivo' AND date >= date('now', '-3 days')",
        vec![],
    )
    .await?
    .map(|r| r.n)
    .unwrap_or(0)
        > 0;
    if !history_fresh {
        let history = fetch_series_history("objetivo").await?;
        upsert_rate_history(db, "objetivo", &history).await?;
    }

    // 2) Official BONDDIA price (exact valuation for positions tracking
    // títulos). Best effort: a scrape failure must not block the rest.
    if let Ok((price_micros, date)) = fetch_bonddia_price().await {
        exec(
            db,
            "INSERT INTO settings (user_id, key, value) VALUES (0, 'bonddia_price', ?1)
             ON CONFLICT(user_id, key) DO UPDATE SET value = excluded.value",
            jsv![serde_json::json!({ "price_micros": price_micros, "date": date }).to_string()],
        )
        .await?;
    }

    // 3) Crypto prices for held symbols (any user's holdings).
    #[derive(serde::Deserialize)]
    struct SymbolRow {
        symbol: String,
    }
    let symbols: Vec<String> = all::<SymbolRow>(
        db,
        "SELECT DISTINCT json_extract(params_json, '$.symbol') AS symbol FROM investments
         WHERE calculator = 'crypto' AND json_extract(params_json, '$.symbol') IS NOT NULL",
        vec![],
    )
    .await?
    .into_iter()
    .map(|r| r.symbol)
    .collect();
    if symbols.is_empty() {
        return Ok(());
    }
    let ids: Vec<&str> = symbols
        .iter()
        .filter_map(|s| finanzas_core::investments::crypto::coingecko_id(s))
        .collect();
    if ids.is_empty() {
        return Ok(());
    }
    let url = format!(
        "https://api.coingecko.com/api/v3/simple/price?ids={}&vs_currencies=mxn,usd",
        ids.join(",")
    );
    let body = get_json(&url).await?;
    for symbol in &symbols {
        let Some(id) = finanzas_core::investments::crypto::coingecko_id(symbol) else {
            continue;
        };
        let Some(mxn) = body.pointer(&format!("/{id}/mxn")).and_then(|v| v.as_f64()) else {
            continue;
        };
        let usd = body.pointer(&format!("/{id}/usd")).and_then(|v| v.as_f64());
        exec(
            db,
            "INSERT INTO crypto_prices (symbol, price_mxn_cents, price_usd_cents, as_of)
             VALUES (?1, ?2, ?3, datetime('now'))
             ON CONFLICT(symbol) DO UPDATE SET price_mxn_cents = excluded.price_mxn_cents,
               price_usd_cents = excluded.price_usd_cents, as_of = excluded.as_of",
            jsv![
                symbol,
                (mxn * 100.0).round() as i64,
                usd.map(|u| (u * 100.0).round() as i64)
            ],
        )
        .await?;
    }
    Ok(())
}

/// Multi-row upserts: 30 rows (60 params) per statement, 40 statements per
/// batch — comfortably inside D1's 100-params-per-statement cap.
async fn upsert_rate_history(
    db: &D1Database,
    series: &str,
    history: &[(String, i64)],
) -> AppResult<()> {
    let mut stmts = Vec::new();
    for chunk in history.chunks(30) {
        let placeholders: Vec<String> = (0..chunk.len())
            .map(|i| format!("(?{}, ?{}, ?{})", 3 * i + 1, 3 * i + 2, 3 * i + 3))
            .collect();
        let sql = format!(
            "INSERT INTO rate_history (series, date, rate_bps) VALUES {}
             ON CONFLICT(series, date) DO UPDATE SET rate_bps = excluded.rate_bps",
            placeholders.join(", ")
        );
        let mut params = Vec::with_capacity(chunk.len() * 3);
        for (date, rate_bps) in chunk {
            params.push(crate::db::ToJs::to_js(&series));
            params.push(crate::db::ToJs::to_js(date));
            params.push(crate::db::ToJs::to_js(rate_bps));
        }
        stmts.push(stmt(db, &sql, params)?);
    }
    batch_chunks(db, stmts, 40).await
}
