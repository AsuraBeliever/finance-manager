//! BONDDIA (cetesdirecto's daily liquidity fund). The fund reinvests daily at
//! the prevailing overnight government rate, which tracks Banxico's target
//! rate. Using a single "current" rate badly undervalues old deposits (the
//! target rate was ~11% in 2023-2024 vs ~6.5% in 2026), so this calculator
//! compounds day by day over the CACHED HISTORICAL series ('objetivo' in
//! rate_history, refreshed from Banxico on startup).
//!
//! params: {"spread_bps": 53.06, "annual_rate_bps": 650}
//! - spread_bps: optional adjustment over the target rate (fees / tracking
//!   difference); subtracted from every day's rate. May be FRACTIONAL: one
//!   whole bps moves a multi-year position by pesos, so cent-level
//!   calibration against the real account needs sub-bps resolution.
//! - annual_rate_bps: fallback flat rate when the history cache is empty
//!   (e.g. first run offline).

use chrono::{Duration, NaiveDate};
use rusqlite::Connection;

use super::{param_i64_or, parse_params, position_value, InvestmentCalculator};
use crate::error::AppResult;
use crate::models::Investment;

pub struct Bonddia;

/// Latest official price per título in micros (settings key 'bonddia_price').
fn load_official_price(conn: &Connection) -> AppResult<Option<i64>> {
    let raw: Option<String> = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'bonddia_price'",
            [],
            |r| r.get(0),
        )
        .map(Some)
        .or_else(|e| match e {
            rusqlite::Error::QueryReturnedNoRows => Ok(None),
            other => Err(other),
        })?;
    Ok(raw
        .and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok())
        .and_then(|v| v.get("price_micros").and_then(|p| p.as_i64()))
        // plausibility guard: the NAV is a few pesos; a value outside this
        // range means a bad scrape and must not poison valuations
        .filter(|p| (500_000..100_000_000).contains(p)))
}

/// (effective_date, rate_bps) steps, chronological.
fn load_history(conn: &Connection) -> AppResult<Vec<(NaiveDate, i64)>> {
    let mut stmt = conn.prepare(
        "SELECT date, rate_bps FROM rate_history WHERE series = 'objetivo' ORDER BY date",
    )?;
    let rows = stmt
        .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, i64>(1)?)))?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows
        .into_iter()
        .filter_map(|(d, bps)| {
            NaiveDate::parse_from_str(&d, "%Y-%m-%d")
                .ok()
                .map(|d| (d, bps))
        })
        .collect())
}

/// Daily-compounded growth from `from` to `to` over the step-function rate
/// history (ACT/365). Dates before the first record use the first rate;
/// after the last record, the last rate.
fn factor_over_history(
    history: &[(NaiveDate, i64)],
    spread_bps: f64,
    from: NaiveDate,
    to: NaiveDate,
) -> f64 {
    if to <= from || history.is_empty() {
        return 1.0;
    }
    let mut factor = 1.0_f64;
    let mut day = from;
    // index of the step in effect at `day`
    let mut idx = match history.binary_search_by_key(&day, |(d, _)| *d) {
        Ok(i) => i,
        Err(0) => 0,
        Err(i) => i - 1,
    };
    while day < to {
        // end of the current step (next change or `to`)
        let step_end = history
            .get(idx + 1)
            .map(|(d, _)| (*d).min(to))
            .unwrap_or(to)
            .max(day + Duration::days(1));
        let days = (step_end - day).num_days();
        let rate_bps = (history[idx].1 as f64 - spread_bps).max(0.0);
        let r = rate_bps / 10_000.0;
        factor *= (1.0 + r / 365.0).powi(days as i32);
        day = step_end;
        if idx + 1 < history.len() && history[idx + 1].0 <= day {
            idx += 1;
        }
    }
    factor
}

impl InvestmentCalculator for Bonddia {
    fn id(&self) -> &'static str {
        "bonddia"
    }

    fn value_at(&self, inv: &Investment, conn: &Connection, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;

        // Exact mode: anchored to the official daily NAV when the user tracks
        // títulos (copied from their cetesdirecto app). No drift by
        // construction — the price already embeds fees and quantization.
        if let Some(titulos) = params.get("titulos").and_then(|v| v.as_i64()) {
            if titulos > 0 {
                if let Some(price_micros) = load_official_price(conn)? {
                    let remanentes = param_i64_or(&params, "remanentes_cents", 0);
                    let value = (titulos as i128 * price_micros as i128 / 10_000) as i64;
                    return Ok(value + remanentes);
                }
            }
        }

        let spread_bps = params
            .get("spread_bps")
            .and_then(|v| v.as_f64())
            .unwrap_or(0.0);
        let history = load_history(conn)?;

        if history.is_empty() {
            // offline first run: flat fallback rate, same shape as nu_cajita
            let rate_bps = param_i64_or(&params, "annual_rate_bps", 0);
            let r = rate_bps as f64 / 10_000.0;
            return position_value(inv, conn, as_of, |from| {
                let days = (as_of - from).num_days().max(0);
                (1.0 + r / 365.0).powi(days as i32)
            });
        }

        position_value(inv, conn, as_of, |from| {
            factor_over_history(&history, spread_bps, from, as_of)
        })
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None // daily liquidity fund
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;
    use crate::investments::test_investment;

    fn seed_history(conn: &Connection, rows: &[(&str, i64)]) {
        for (date, bps) in rows {
            conn.execute(
                "INSERT INTO rate_history (series, date, rate_bps) VALUES ('objetivo', ?1, ?2)",
                rusqlite::params![date, bps],
            )
            .unwrap();
        }
    }

    fn seed_investment(conn: &Connection, inv: &Investment) {
        conn.execute(
            "INSERT INTO investments (id, name, calculator, principal_cents, start_date, params_json)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            rusqlite::params![inv.id, inv.name, inv.calculator, inv.principal_cents, inv.start_date, inv.params_json],
        )
        .unwrap();
    }

    #[test]
    fn compounds_across_rate_changes() {
        let conn = open_in_memory();
        // 10% until 2026-01-31 (30 days from start), then 20% for 30 more days.
        // factor = (1+0.10/365)^30 * (1+0.20/365)^30
        //        = 1.00825346... * 1.01657... = 1.024963 → 1,024,963
        seed_history(&conn, &[("2025-01-01", 1000), ("2026-01-31", 2000)]);
        let inv = test_investment("bonddia", 1_000_000, "{}");
        seed_investment(&conn, &inv);
        let as_of = NaiveDate::from_ymd_opt(2026, 3, 2).unwrap(); // start +60d
        let value = Bonddia.value_at(&inv, &conn, as_of).unwrap();
        let expected = (1_000_000.0_f64
            * (1.0_f64 + 0.10 / 365.0).powi(30)
            * (1.0_f64 + 0.20 / 365.0).powi(30))
        .round() as i64;
        assert_eq!(value, expected);
        assert!((1_024_000..1_026_000).contains(&value), "value {value}");
    }

    #[test]
    fn spread_reduces_the_daily_rate() {
        let conn = open_in_memory();
        seed_history(&conn, &[("2025-01-01", 1000)]);
        let inv = test_investment("bonddia", 1_000_000, r#"{"spread_bps": 1000}"#);
        seed_investment(&conn, &inv);
        // spread equals the whole rate → factor 1.0
        let as_of = NaiveDate::from_ymd_opt(2026, 12, 31).unwrap();
        assert_eq!(Bonddia.value_at(&inv, &conn, as_of).unwrap(), 1_000_000);
    }

    #[test]
    fn falls_back_to_flat_rate_without_history() {
        let conn = open_in_memory();
        let inv = test_investment("bonddia", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        seed_investment(&conn, &inv);
        let a_year = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        // identical to nu_cajita at 15%: 1,161,798
        assert_eq!(Bonddia.value_at(&inv, &conn, a_year).unwrap(), 1_161_798);
    }

    #[test]
    fn titulos_mode_anchors_to_the_official_price() {
        let conn = open_in_memory();
        // even with history present, títulos × precio wins
        seed_history(&conn, &[("2025-01-01", 1000)]);
        conn.execute(
            "INSERT INTO settings (key, value) VALUES ('bonddia_price',
             '{\"price_micros\": 2334524, \"date\": \"2026-06-11\"}')",
            [],
        )
        .unwrap();
        let inv = test_investment(
            "bonddia",
            600,
            r#"{"titulos": 2923, "remanentes_cents": 206, "spread_bps": 53.06}"#,
        );
        seed_investment(&conn, &inv);
        let as_of = NaiveDate::from_ymd_opt(2026, 6, 11).unwrap();
        // 2923 × 2.334524 = 6,823.81 (truncated to cents) + 2.06 remanentes
        let expected = (2923i128 * 2_334_524 / 10_000) as i64 + 206;
        assert_eq!(Bonddia.value_at(&inv, &conn, as_of).unwrap(), expected);
    }

    #[test]
    fn titulos_mode_falls_back_to_history_without_price() {
        let conn = open_in_memory();
        seed_history(&conn, &[("2025-01-01", 1000)]);
        let inv = test_investment("bonddia", 1_000_000, r#"{"titulos": 2923}"#);
        seed_investment(&conn, &inv);
        let as_of = NaiveDate::from_ymd_opt(2026, 1, 31).unwrap(); // +30d at 10%
        let expected = (1_000_000.0_f64 * (1.0_f64 + 0.10 / 365.0).powi(30)).round() as i64;
        assert_eq!(Bonddia.value_at(&inv, &conn, as_of).unwrap(), expected);
    }

    #[test]
    fn movements_use_historical_rates_from_their_dates() {
        let conn = open_in_memory();
        // constant 10%; deposit half-way through
        seed_history(&conn, &[("2025-01-01", 1000)]);
        let inv = test_investment("bonddia", 1_000_000, "{}");
        seed_investment(&conn, &inv);
        conn.execute(
            "INSERT INTO investment_movements (investment_id, kind, amount_cents, occurred_at)
             VALUES (1, 'deposit', 1000000, '2026-02-01')",
            [],
        )
        .unwrap();
        let as_of = NaiveDate::from_ymd_opt(2026, 3, 3).unwrap(); // +61d / +30d
        let value = Bonddia.value_at(&inv, &conn, as_of).unwrap();
        let expected = (1_000_000.0_f64 * (1.0_f64 + 0.10 / 365.0).powi(61)
            + 1_000_000.0_f64 * (1.0_f64 + 0.10 / 365.0).powi(30))
        .round() as i64;
        assert_eq!(value, expected);
    }
}
