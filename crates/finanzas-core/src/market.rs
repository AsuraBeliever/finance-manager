//! Pure parsing for external market-data sources. The HTTP fetching lives in
//! each backend (reqwest on desktop, worker::Fetch in the Cloudflare Worker);
//! everything here is offline-testable.

use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};

// ---- open.er-api.com exchange rates ----
//
// One request returns every currency relative to MXN; rates refresh daily.
// `rates[CUR]` = units of CUR per 1 MXN, so rate_to_mxn = 1 / rates[CUR].

pub const RATES_URL: &str = "https://open.er-api.com/v6/latest/MXN";

/// Convert the provider's "CUR per MXN" quote into micros of MXN per CUR.
/// Returns None for absurd quotes (zero, negative, NaN).
pub fn quote_to_micros(cur_per_mxn: f64) -> Option<i64> {
    if !cur_per_mxn.is_finite() || cur_per_mxn <= 0.0 {
        return None;
    }
    let micros = (1_000_000.0 / cur_per_mxn).round();
    (micros >= 1.0 && micros <= i64::MAX as f64).then_some(micros as i64)
}

/// Extract (code, micros) pairs for the given currencies from the provider's
/// JSON body.
pub fn parse_rates_body(body: &serde_json::Value, wanted: &[String]) -> Vec<(String, i64)> {
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

// ---- Banxico (tasas de CETES y tasa objetivo) ----
//
// Series: SF43936/SF43939/SF43942/SF43945 = tasa de rendimiento de la subasta
// semanal de CETES a 28/91/182/364 días; SF61745 = tasa objetivo de Banxico
// (referencia para BONDDIA, que sigue la tasa de fondeo gubernamental).
//
// Source needs NO token: SieInternet's own chart endpoint
// (consultaSerieGrafica.do) returns {titulo, valores: [[date, value]]} with
// -989898.0 as the missing-value sentinel. The series id must be paired with
// the cuadro context it appears in (e.g. "SF43936,CF107,5").

/// SieInternet chart context per series kind.
pub fn banxico_series(kind: &str) -> AppResult<&'static str> {
    Ok(match kind {
        "cetes_28" => "SF43936,CF107,5",
        "cetes_91" => "SF43939,CF107,9",
        "cetes_182" => "SF43942,CF107,13",
        "cetes_364" => "SF43945,CF107,17",
        "objetivo" => "SF61745,CF101,2",
        // Tipo de cambio FIX: the official MXN/USD reference Banxico publishes
        // in the DOF and banks settle against. Used to value USD wallets.
        "usd_fix" => "SF43718,CF86,2",
        other => {
            return Err(AppError::InvalidInput(format!(
                "serie desconocida: {other} (válidas: cetes_28/91/182/364, objetivo, usd_fix)"
            )))
        }
    })
}

pub fn banxico_series_url(kind: &str) -> AppResult<String> {
    let chart_context = banxico_series(kind)?;
    Ok(format!(
        "https://www.banxico.org.mx/SieInternet/consultaSerieGrafica.do?s={chart_context}&versionSerie=LA-MAS-RECIENTE&l=es"
    ))
}

const SIE_SENTINEL: f64 = -989898.0;

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BanxicoRate {
    pub rate_bps: i64,
    pub date: String,
}

/// All (date, rate_bps) points of a SieInternet chart payload, sentinel rows
/// skipped, in chronological order. Used to cache full rate histories.
pub fn parse_sie_internet_history(body: &serde_json::Value) -> Vec<(String, i64)> {
    body.get("valores")
        .and_then(|v| v.as_array())
        .into_iter()
        .flatten()
        .filter_map(|pair| {
            let date = pair.get(0)?.as_str()?;
            let value = pair.get(1)?.as_f64()?;
            (value != SIE_SENTINEL && value > 0.0)
                .then(|| (date.to_string(), (value * 100.0).round() as i64))
        })
        .collect()
}

/// Latest (date, value) of a SieInternet chart payload, skipping the missing
/// sentinel. Shared by the interest-rate and exchange-rate parsers.
fn sie_last_value(body: &serde_json::Value) -> AppResult<(String, f64)> {
    body.get("valores")
        .and_then(|v| v.as_array())
        .into_iter()
        .flatten()
        .filter_map(|pair| {
            let date = pair.get(0)?.as_str()?;
            let value = pair.get(1)?.as_f64()?;
            (value != SIE_SENTINEL && value > 0.0).then(|| (date.to_string(), value))
        })
        .next_back()
        .ok_or_else(|| AppError::Internal("Banxico no regresó datos para la serie".into()))
}

/// Parse SieInternet chart payload: {valores: [[iso_date, value]]} where
/// SIE_SENTINEL marks missing values. The latest real value wins.
pub fn parse_sie_internet_body(body: &serde_json::Value) -> AppResult<BanxicoRate> {
    let (date, value) = sie_last_value(body)?;
    Ok(BanxicoRate {
        rate_bps: (value * 100.0).round() as i64,
        date,
    })
}

/// Latest value of a SieInternet FX series (MXN per foreign unit, e.g. the
/// USD/MXN FIX) as (date, rate_to_mxn_micros). Unlike the interest-rate parser
/// this keeps full precision instead of rounding to whole basis points.
pub fn parse_sie_internet_fx_micros(body: &serde_json::Value) -> AppResult<(String, i64)> {
    let (date, value) = sie_last_value(body)?;
    Ok((date, (value * 1_000_000.0).round() as i64))
}

// ---- BONDDIA official daily price (cetesdirecto) ----
//
// https://www.cetesdirecto.com/tablas/valores_gubernamentales/bonddia.html
// publishes the fund's official price per título ("Precio día anterior").
// Anchoring a position to títulos × precio reproduces cetesdirecto exactly,
// with no drift — the NAV already embeds fees and whole-título quantization.
// The page blocks non-browser user agents, hence the UA header used by both
// backends.

pub const BONDDIA_URL: &str =
    "https://www.cetesdirecto.com/tablas/valores_gubernamentales/bonddia.html";
pub const BROWSER_UA: &str =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

const SPANISH_MONTHS: [(&str, u32); 12] = [
    ("enero", 1),
    ("febrero", 2),
    ("marzo", 3),
    ("abril", 4),
    ("mayo", 5),
    ("junio", 6),
    ("julio", 7),
    ("agosto", 8),
    ("septiembre", 9),
    ("octubre", 10),
    ("noviembre", 11),
    ("diciembre", 12),
];

/// Drop everything inside <...> so markup attributes (e.g. `<TD WIDTH=6>`)
/// can never be mistaken for data. Tags become spaces to keep tokens apart.
fn strip_tags(html: &str) -> String {
    let mut out = String::with_capacity(html.len());
    let mut in_tag = false;
    for c in html.chars() {
        match c {
            '<' => {
                in_tag = true;
                out.push(' ');
            }
            '>' => in_tag = false,
            c if !in_tag => out.push(c),
            _ => {}
        }
    }
    out
}

/// Extract (price_micros, iso_date) from the BONDDIA page HTML.
/// The price is the first token after "Precio" that looks like a fund NAV:
/// a decimal with 4+ fraction digits in a plausible range. The raw page is
/// 90s-style HTML where naive "next number" scans pick up attribute values.
pub fn parse_bonddia_page(html: &str) -> Option<(i64, String)> {
    let text = strip_tags(&html.replace(['\n', '\r', '\t'], " "));
    let tokens: Vec<&str> = text.split_whitespace().collect();

    let precio_idx = tokens.iter().position(|t| t.starts_with("Precio"))?;
    let price = tokens[precio_idx..].iter().take(10).find_map(|t| {
        let (int_part, frac_part) = t.split_once('.')?;
        if frac_part.len() < 4
            || !int_part.chars().all(|c| c.is_ascii_digit())
            || !frac_part.chars().all(|c| c.is_ascii_digit())
        {
            return None;
        }
        let p: f64 = t.parse().ok()?;
        (0.5..100.0).contains(&p).then_some(p)
    })?;

    // date: "11 Junio 2026" — day token, month-name token, year token
    let mut iso_date = String::new();
    'outer: for (i, token) in tokens.iter().enumerate() {
        for (name, month) in SPANISH_MONTHS {
            if token.eq_ignore_ascii_case(name) && i > 0 && i + 1 < tokens.len() {
                if let (Ok(d), Ok(y)) = (tokens[i - 1].parse::<u32>(), tokens[i + 1].parse::<i32>())
                {
                    if (1..=31).contains(&d) && (2000..2100).contains(&y) {
                        iso_date = format!("{y:04}-{month:02}-{d:02}");
                        break 'outer;
                    }
                }
            }
        }
    }
    Some(((price * 1_000_000.0).round() as i64, iso_date))
}

#[cfg(test)]
mod tests {
    use super::*;

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
    fn rejects_unknown_series() {
        assert!(banxico_series("cetes_90").is_err());
    }

    #[test]
    fn parses_full_history() {
        let body: serde_json::Value = serde_json::from_str(
            r#"{"valores":[["2023-01-01",11.25],["2024-06-01",-989898.0],["2025-06-01",8.50]]}"#,
        )
        .unwrap();
        let h = parse_sie_internet_history(&body);
        assert_eq!(
            h,
            vec![("2023-01-01".into(), 1125), ("2025-06-01".into(), 850)]
        );
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
    fn parses_usd_fix_to_micros_keeping_precision() {
        // FIX is MXN per USD; 17.2023 -> 17_202_300 micros (no bps rounding).
        let body: serde_json::Value = serde_json::from_str(
            r#"{"serie":"SF43718","valores":
                [["2026-06-12",17.2067],["2026-06-15",17.2008],
                 ["2026-06-16",17.2023],["2026-06-17",-989898.0]]}"#,
        )
        .unwrap();
        let (date, micros) = parse_sie_internet_fx_micros(&body).unwrap();
        assert_eq!(micros, 17_202_300);
        assert_eq!(date, "2026-06-16");
    }

    #[test]
    fn sie_internet_rejects_empty_or_all_sentinel() {
        let body: serde_json::Value =
            serde_json::from_str(r#"{"valores":[["2026-06-11",-989898.0]]}"#).unwrap();
        assert!(parse_sie_internet_body(&body).is_err());
        let body: serde_json::Value = serde_json::from_str(r#"{}"#).unwrap();
        assert!(parse_sie_internet_body(&body).is_err());
    }

    #[test]
    fn parses_price_and_date_from_real_markup() {
        // mirrors the live page: attribute numbers (WIDTH=6) sit between
        // "Precio" and the actual value — they must never win
        let html = "<FONT SIZE=2>11 Junio 2026</FONT> \
            <TD>Precio d&iacute;a anterior</FONT></TD>\t<TD WIDTH=6 BGCOLOR=#235B4E></TD>\
            <TD WIDTH=4></TD><TD ALIGN=LEFT BGCOLOR=#F2F2F1><FONT COLOR=747474>2.334524</FONT></TD>";
        let (micros, date) = parse_bonddia_page(html).unwrap();
        assert_eq!(micros, 2_334_524);
        assert_eq!(date, "2026-06-11");
    }

    #[test]
    fn rejects_pages_without_a_plausible_price() {
        assert!(parse_bonddia_page("<html>mantenimiento</html>").is_none());
        assert!(parse_bonddia_page("Precio 45000.0").is_none());
        // attribute-like integers and short decimals are not NAVs
        assert!(parse_bonddia_page("Precio <TD WIDTH=6></TD> 2.33").is_none());
    }
}
