//! Custom fixed-rate investment.
//! params: {"annual_rate_bps": N, "compounding": "daily" | "monthly" | "simple"}
//! - daily:   principal * (1 + r/365)^days            (ACT/365)
//! - monthly: principal * (1 + r/12)^full_months      (full calendar months)
//! - simple:  principal * (1 + r * days/365)

use chrono::{Datelike, NaiveDate};

use super::{param_i64, parse_params, CalcContext, InvestmentCalculator};
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

    fn value_at(&self, inv: &Investment, ctx: &CalcContext, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;
        let rate_bps = param_i64(&params, "annual_rate_bps")?;
        let compounding = params
            .get("compounding")
            .and_then(|v| v.as_str())
            .unwrap_or("daily")
            .to_string();
        if !matches!(compounding.as_str(), "daily" | "monthly" | "simple") {
            return Err(AppError::InvalidInput(format!(
                "compounding inválido: {compounding} (válidos: daily, monthly, simple)"
            )));
        }
        let r = rate_bps as f64 / 10_000.0;

        super::position_value(inv, ctx, as_of, |from| {
            let days = (as_of - from).num_days().max(0);
            match compounding.as_str() {
                "daily" => (1.0 + r / 365.0).powi(days as i32),
                "monthly" => (1.0 + r / 12.0).powi(full_months_between(from, as_of)),
                _ => 1.0 + r * days as f64 / 365.0,
            }
        })
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::investments::{test_ctx, test_investment};

    #[test]
    fn simple_interest() {
        // $10,000.00 at 10% simple for exactly one year = $11,000.00
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1000, "compounding": "simple"}"#,
        );
        let ctx = test_ctx(&[]);
        let a_year = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(FixedRate.value_at(&inv, &ctx, a_year).unwrap(), 1_100_000);
    }

    #[test]
    fn monthly_compounding_uses_full_calendar_months() {
        // 12% annual monthly-compounded = 1% per month.
        // 3 full months: 1_000_000 * 1.01^3 = 1_030_301
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1200, "compounding": "monthly"}"#,
        );
        let ctx = test_ctx(&[]);
        let three_months = NaiveDate::from_ymd_opt(2026, 4, 1).unwrap();
        assert_eq!(
            FixedRate.value_at(&inv, &ctx, three_months).unwrap(),
            1_030_301
        );
        // one day short of the third month -> only 2 full months
        let almost = NaiveDate::from_ymd_opt(2026, 3, 31).unwrap();
        assert_eq!(FixedRate.value_at(&inv, &ctx, almost).unwrap(), 1_020_100);
    }

    #[test]
    fn daily_matches_nu_formula() {
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1500, "compounding": "daily"}"#,
        );
        let ctx = test_ctx(&[]);
        let a_year = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(FixedRate.value_at(&inv, &ctx, a_year).unwrap(), 1_161_798);
    }

    #[test]
    fn rejects_unknown_compounding() {
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 1000, "compounding": "hourly"}"#,
        );
        let ctx = test_ctx(&[]);
        let date = NaiveDate::from_ymd_opt(2026, 2, 1).unwrap();
        assert!(FixedRate.value_at(&inv, &ctx, date).is_err());
    }
}
