//! BONDDIA (cetesdirecto's daily liquidity fund). The fund reinvests daily at
//! the prevailing overnight government rate, which tracks Banxico's target
//! rate. Using a single "current" rate badly undervalues old deposits (the
//! target rate was ~11% in 2023-2024 vs ~6.5% in 2026), so this calculator
//! compounds day by day over the CACHED HISTORICAL series ('objetivo' in
//! rate_history, refreshed from Banxico), provided via `CalcContext`.
//!
//! params: {"spread_bps": 53.06, "annual_rate_bps": 650}
//! - spread_bps: optional adjustment over the target rate (fees / tracking
//!   difference); subtracted from every day's rate. May be FRACTIONAL: one
//!   whole bps moves a multi-year position by pesos, so cent-level
//!   calibration against the real account needs sub-bps resolution.
//! - annual_rate_bps: fallback flat rate when the history cache is empty
//!   (e.g. first run offline).

use chrono::{Duration, NaiveDate};

use super::{
    param_i64_or, parse_params, parse_start_date, position_value, CalcContext, InvestmentCalculator,
};
use crate::error::AppResult;
use crate::models::Investment;

pub struct Bonddia;

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

/// Spread of an estimated-mode position, as `value_at` reads it.
fn spread_bps(inv: &Investment) -> AppResult<f64> {
    Ok(parse_params(inv)?
        .get("spread_bps")
        .and_then(|v| v.as_f64())
        .unwrap_or(0.0))
}

/// Growth of the single day `[day, day + 1)` — the one-day step of
/// `factor_over_history`, with the same rate lookup.
fn day_growth(history: &[(NaiveDate, i64)], spread_bps: f64, day: NaiveDate) -> f64 {
    let idx = match history.binary_search_by_key(&day, |(d, _)| *d) {
        Ok(i) => i,
        Err(0) => 0,
        Err(i) => i - 1,
    };
    let rate_bps = (history[idx].1 as f64 - spread_bps).max(0.0);
    1.0 + rate_bps / 10_000.0 / 365.0
}

impl InvestmentCalculator for Bonddia {
    fn id(&self) -> &'static str {
        "bonddia"
    }

    fn value_at(&self, inv: &Investment, ctx: &CalcContext, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;

        // Exact mode: anchored to the official daily NAV when the user tracks
        // títulos (copied from their cetesdirecto app). No drift by
        // construction — the price already embeds fees and quantization.
        if let Some(titulos) = params.get("titulos").and_then(|v| v.as_i64()) {
            if titulos > 0 {
                if let Some(price_micros) = ctx.bonddia_price_micros {
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
        let history = &ctx.rate_history;

        if history.is_empty() {
            // offline first run: flat fallback rate, same shape as nu_cajita
            let rate_bps = param_i64_or(&params, "annual_rate_bps", 0);
            let r = rate_bps as f64 / 10_000.0;
            return position_value(inv, ctx, as_of, |from| {
                let days = (as_of - from).num_days().max(0);
                (1.0 + r / 365.0).powi(days as i32)
            });
        }

        position_value(inv, ctx, as_of, |from| {
            factor_over_history(history, spread_bps, from, as_of)
        })
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None // daily liquidity fund
    }

    /// The estimated mode is a rate model over the Banxico history and can
    /// value any day; the títulos ("exact") mode rides the official NAV, of
    /// which only the latest is cached.
    /// Estimated mode over a rate history, in ONE pass: yesterday's value
    /// grown by one day, plus whatever enters today. Valuing each day from
    /// scratch walks every past day per movement — days² × movements, ~0.4 s
    /// for a 3-year position with 40 deposits. Same lots, dates and rates as
    /// `value_at` (checked against it to the cent in the tests).
    fn values_over(
        &self,
        inv: &Investment,
        ctx: &CalcContext,
        from: NaiveDate,
        to: NaiveDate,
    ) -> AppResult<Vec<i64>> {
        let history = &ctx.rate_history;
        let exact = !self.values_past_days(inv) && ctx.bonddia_price_micros.is_some();
        if history.is_empty() || exact || to < from {
            // Flat fallback or títulos: no daily walk to save.
            let mut out = Vec::new();
            let mut day = from;
            while day <= to {
                out.push(self.value_at(inv, ctx, day)?);
                day += Duration::days(1);
            }
            return Ok(out);
        }
        let spread = spread_bps(inv)?;
        let start = parse_start_date(inv)?;
        // Every amount that starts accruing on a day, as value_at counts it:
        // the principal on the start date, a movement on its own date (or the
        // start, if dated before it), a withdrawal negative.
        let entering = |day: NaiveDate| -> f64 {
            let mut v = if day == start {
                inv.principal_cents as f64
            } else {
                0.0
            };
            for m in &ctx.movements {
                if m.occurred_at.max(start) == day {
                    let sign = if m.kind == "withdrawal" { -1.0 } else { 1.0 };
                    v += sign * m.amount_cents as f64;
                }
            }
            v
        };
        let mut out = Vec::new();
        // Before the start value_at is the bare principal (no growth yet).
        let mut day = from;
        while day < start && day <= to {
            out.push(self.value_at(inv, ctx, day)?);
            day += Duration::days(1);
        }
        if day > to {
            return Ok(out);
        }
        let mut walk = start;
        let mut value = entering(start);
        while walk < day {
            value = value * day_growth(history, spread, walk) + entering(walk + Duration::days(1));
            walk += Duration::days(1);
        }
        loop {
            out.push(value.round() as i64);
            if walk >= to {
                break;
            }
            value = value * day_growth(history, spread, walk) + entering(walk + Duration::days(1));
            walk += Duration::days(1);
        }
        // The walk multiplies day by day where value_at raises whole steps to
        // a power: the same number up to float noise, which can still tip a
        // half cent. The ends come from value_at itself, so a history built
        // from these values adds up to exactly the gain value_at reports, and
        // the next run continues from the very total this one stored.
        if let Some(first) = out.first_mut() {
            *first = self.value_at(inv, ctx, from)?;
        }
        if let Some(last) = out.last_mut() {
            *last = self.value_at(inv, ctx, to)?;
        }
        Ok(out)
    }

    fn values_past_days(&self, inv: &Investment) -> bool {
        let titulos = parse_params(inv)
            .ok()
            .and_then(|p| p.get("titulos").and_then(|v| v.as_i64()))
            .unwrap_or(0);
        titulos <= 0
    }

    fn effective_annual_rate_bps(
        &self,
        inv: &Investment,
        ctx: &CalcContext,
    ) -> AppResult<Option<i64>> {
        let params = parse_params(inv)?;
        let spread_bps = params
            .get("spread_bps")
            .and_then(|v| v.as_f64())
            .unwrap_or(0.0);
        // Latest target rate minus the tracking spread; fall back to the flat
        // param rate when the history cache is empty.
        let rate = match ctx.rate_history.last() {
            Some((_, bps)) => (*bps as f64 - spread_bps).max(0.0).round() as i64,
            None => param_i64_or(&params, "annual_rate_bps", 0),
        };
        Ok(Some(rate))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::investments::{test_ctx, test_investment, Movement};

    fn history(rows: &[(&str, i64)]) -> Vec<(NaiveDate, i64)> {
        rows.iter()
            .map(|(date, bps)| (NaiveDate::parse_from_str(date, "%Y-%m-%d").unwrap(), *bps))
            .collect()
    }

    /// A daily series like the real 'objetivo' cache (one row per day, the
    /// rate stepping down over time) from 2023-01-01 for `days` days.
    fn daily_series(days: i64) -> Vec<(NaiveDate, i64)> {
        let start = NaiveDate::from_ymd_opt(2023, 1, 1).unwrap();
        (0..days)
            .map(|i| (start + Duration::days(i), 1125 - (i / 90) * 25))
            .collect()
    }

    #[test]
    fn values_over_matches_value_at_every_day() {
        // Estimated mode, 3 years of daily rates, a fractional spread, a
        // movement before the start and 30 later deposits/withdrawals: the
        // one-pass walk must land on value_at's cent every single day.
        let ctx_movements: Vec<(&str, i64, String)> = (0..30)
            .map(|k| {
                let kind = if k % 4 == 3 { "withdrawal" } else { "deposit" };
                let date = NaiveDate::from_ymd_opt(2023, 3, 1).unwrap() + Duration::days(k * 31);
                (kind, 123_457 + k * 1_001, date.to_string())
            })
            .collect();
        let mut rows: Vec<(&str, i64, &str)> = ctx_movements
            .iter()
            .map(|(k, a, d)| (*k, *a, d.as_str()))
            .collect();
        rows.push(("deposit", 50_000, "2022-12-15"));
        let mut ctx = test_ctx(&rows);
        ctx.movements.sort_by_key(|m| m.occurred_at);
        ctx.rate_history = daily_series(1_100);
        let mut inv = test_investment("bonddia", 2_500_000, r#"{"spread_bps": 53.06}"#);
        inv.start_date = "2023-01-10".into();
        let from = NaiveDate::from_ymd_opt(2023, 1, 5).unwrap(); // before the start too
        let to = NaiveDate::from_ymd_opt(2025, 12, 31).unwrap();
        let walked = Bonddia.values_over(&inv, &ctx, from, to).unwrap();
        let mut day = from;
        for v in walked {
            assert_eq!(v, Bonddia.value_at(&inv, &ctx, day).unwrap(), "on {day}");
            day += Duration::days(1);
        }
        assert_eq!(day, to + Duration::days(1));
    }

    #[test]
    fn compounds_across_rate_changes() {
        // 10% until 2026-01-31 (30 days from start), then 20% for 30 more days.
        // factor = (1+0.10/365)^30 * (1+0.20/365)^30
        //        = 1.00825346... * 1.01657... = 1.024963 → 1,024,963
        let ctx = CalcContext {
            rate_history: history(&[("2025-01-01", 1000), ("2026-01-31", 2000)]),
            ..Default::default()
        };
        let inv = test_investment("bonddia", 1_000_000, "{}");
        let as_of = NaiveDate::from_ymd_opt(2026, 3, 2).unwrap(); // start +60d
        let value = Bonddia.value_at(&inv, &ctx, as_of).unwrap();
        let expected = (1_000_000.0_f64
            * (1.0_f64 + 0.10 / 365.0).powi(30)
            * (1.0_f64 + 0.20 / 365.0).powi(30))
        .round() as i64;
        assert_eq!(value, expected);
        assert!((1_024_000..1_026_000).contains(&value), "value {value}");
    }

    #[test]
    fn spread_reduces_the_daily_rate() {
        let ctx = CalcContext {
            rate_history: history(&[("2025-01-01", 1000)]),
            ..Default::default()
        };
        let inv = test_investment("bonddia", 1_000_000, r#"{"spread_bps": 1000}"#);
        // spread equals the whole rate → factor 1.0
        let as_of = NaiveDate::from_ymd_opt(2026, 12, 31).unwrap();
        assert_eq!(Bonddia.value_at(&inv, &ctx, as_of).unwrap(), 1_000_000);
    }

    #[test]
    fn falls_back_to_flat_rate_without_history() {
        let ctx = CalcContext::default();
        let inv = test_investment("bonddia", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let a_year = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        // identical to nu_cajita at 15%: 1,161,798
        assert_eq!(Bonddia.value_at(&inv, &ctx, a_year).unwrap(), 1_161_798);
    }

    #[test]
    fn titulos_mode_anchors_to_the_official_price() {
        // even with history present, títulos × precio wins
        let ctx = CalcContext {
            rate_history: history(&[("2025-01-01", 1000)]),
            bonddia_price_micros: Some(2_334_524),
            ..Default::default()
        };
        let inv = test_investment(
            "bonddia",
            600,
            r#"{"titulos": 2923, "remanentes_cents": 206, "spread_bps": 53.06}"#,
        );
        let as_of = NaiveDate::from_ymd_opt(2026, 6, 11).unwrap();
        // 2923 × 2.334524 = 6,823.81 (truncated to cents) + 2.06 remanentes
        let expected = (2923i128 * 2_334_524 / 10_000) as i64 + 206;
        assert_eq!(Bonddia.value_at(&inv, &ctx, as_of).unwrap(), expected);
    }

    #[test]
    fn titulos_mode_cash_deposit_raises_value_one_for_one() {
        // The fix for exact-mode deposits folds cash into remanentes so the total
        // moves like cetesdirecto. A $3,000 deposit (remanentes 206 → 300_206)
        // must lift the value by exactly 300_000 over the same títulos × price.
        let ctx = CalcContext {
            bonddia_price_micros: Some(2_349_862),
            ..Default::default()
        };
        let as_of = NaiveDate::from_ymd_opt(2026, 7, 24).unwrap();
        let base = Bonddia
            .value_at(
                &test_investment(
                    "bonddia",
                    600,
                    r#"{"titulos": 2923, "remanentes_cents": 206}"#,
                ),
                &ctx,
                as_of,
            )
            .unwrap();
        let after = Bonddia
            .value_at(
                &test_investment(
                    "bonddia",
                    600,
                    r#"{"titulos": 2923, "remanentes_cents": 300206}"#,
                ),
                &ctx,
                as_of,
            )
            .unwrap();
        assert_eq!(base, (2923i128 * 2_349_862 / 10_000) as i64 + 206);
        assert_eq!(after - base, 300_000);
    }

    #[test]
    fn titulos_mode_falls_back_to_history_without_price() {
        let ctx = CalcContext {
            rate_history: history(&[("2025-01-01", 1000)]),
            ..Default::default()
        };
        let inv = test_investment("bonddia", 1_000_000, r#"{"titulos": 2923}"#);
        let as_of = NaiveDate::from_ymd_opt(2026, 1, 31).unwrap(); // +30d at 10%
        let expected = (1_000_000.0_f64 * (1.0_f64 + 0.10 / 365.0).powi(30)).round() as i64;
        assert_eq!(Bonddia.value_at(&inv, &ctx, as_of).unwrap(), expected);
    }

    #[test]
    fn movements_use_historical_rates_from_their_dates() {
        // constant 10%; deposit half-way through
        let mut ctx = test_ctx(&[("deposit", 1_000_000, "2026-02-01")]);
        ctx.rate_history = history(&[("2025-01-01", 1000)]);
        // type sanity: Movement built through the helper
        assert!(matches!(ctx.movements.first(), Some(Movement { .. })));
        let inv = test_investment("bonddia", 1_000_000, "{}");
        let as_of = NaiveDate::from_ymd_opt(2026, 3, 3).unwrap(); // +61d / +30d
        let value = Bonddia.value_at(&inv, &ctx, as_of).unwrap();
        let expected = (1_000_000.0_f64 * (1.0_f64 + 0.10 / 365.0).powi(61)
            + 1_000_000.0_f64 * (1.0_f64 + 0.10 / 365.0).powi(30))
        .round() as i64;
        assert_eq!(value, expected);
    }
}
