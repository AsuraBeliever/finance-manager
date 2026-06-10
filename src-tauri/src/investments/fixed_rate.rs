//! Custom fixed-rate investment.
//! params: {"annual_rate_bps": N, "compounding": "daily" | "monthly" | "simple"}
//! - daily:   principal * (1 + r/365)^days            (ACT/365)
//! - monthly: principal * (1 + r/12)^full_months      (full calendar months)
//! - simple:  principal * (1 + r * days/365)

use chrono::{Datelike, NaiveDate};
use rusqlite::Connection;

use super::{param_i64, parse_params, parse_start_date, InvestmentCalculator};
use crate::error::{AppError, AppResult};
use crate::models::Investment;

pub struct FixedRate;

/// Whole calendar months elapsed from `start` to `as_of` (never negative).
fn full_months_between(start: NaiveDate, as_of: NaiveDate) -> i32 {
    if as_of <= start {
        return 0;
    }
    let mut months =
        (as_of.year() - start.year()) * 12 + (as_of.month() as i32 - start.month() as i32);
    if as_of.day() < start.day() {
        months -= 1;
    }
    months.max(0)
}

impl InvestmentCalculator for FixedRate {
    fn id(&self) -> &'static str {
        "fixed_rate"
    }

    fn value_at(&self, inv: &Investment, _conn: &Connection, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;
        let rate_bps = param_i64(&params, "annual_rate_bps")?;
        let compounding = params
            .get("compounding")
            .and_then(|v| v.as_str())
            .unwrap_or("daily")
            .to_string();
        let start = parse_start_date(inv)?;

        let days = (as_of - start).num_days().max(0);
        let r = rate_bps as f64 / 10_000.0;
        let principal = inv.principal_cents as f64;
        let value = match compounding.as_str() {
            "daily" => principal * (1.0 + r / 365.0).powi(days as i32),
            "monthly" => principal * (1.0 + r / 12.0).powi(full_months_between(start, as_of)),
            "simple" => principal * (1.0 + r * days as f64 / 365.0),
            other => {
                return Err(AppError::InvalidInput(format!(
                    "compounding inválido: {other} (válidos: daily, monthly, simple)"
                )))
            }
        };
        Ok(value.round() as i64)
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;
    use crate::investments::test_investment;

    #[test]
    fn simple_interest() {
        let conn = open_in_memory();
        // $10,000.00 at 10% simple for exactly one year = $11,000.00
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1000, "compounding": "simple"}"#,
        );
        let a_year = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(FixedRate.value_at(&inv, &conn, a_year).unwrap(), 1_100_000);
    }

    #[test]
    fn monthly_compounding_uses_full_calendar_months() {
        let conn = open_in_memory();
        // 12% annual monthly-compounded = 1% per month.
        // 3 full months: 1_000_000 * 1.01^3 = 1_030_301
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1200, "compounding": "monthly"}"#,
        );
        let three_months = NaiveDate::from_ymd_opt(2026, 4, 1).unwrap();
        assert_eq!(
            FixedRate.value_at(&inv, &conn, three_months).unwrap(),
            1_030_301
        );
        // one day short of the third month -> only 2 full months
        let almost = NaiveDate::from_ymd_opt(2026, 3, 31).unwrap();
        assert_eq!(FixedRate.value_at(&inv, &conn, almost).unwrap(), 1_020_100);
    }

    #[test]
    fn daily_matches_nu_formula() {
        let conn = open_in_memory();
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1500, "compounding": "daily"}"#,
        );
        let a_year = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(FixedRate.value_at(&inv, &conn, a_year).unwrap(), 1_161_798);
    }

    #[test]
    fn rejects_unknown_compounding() {
        let conn = open_in_memory();
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1000, "compounding": "hourly"}"#,
        );
        let date = NaiveDate::from_ymd_opt(2026, 2, 1).unwrap();
        assert!(FixedRate.value_at(&inv, &conn, date).is_err());
    }
}
