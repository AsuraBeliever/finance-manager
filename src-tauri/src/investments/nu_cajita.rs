//! Nu México "Cajitas": daily-compounding savings box.
//! Nu quotes an annual rate with daily accrual; convention ACT/365.
//! params: {"annual_rate_bps": 1500}  (15.00%, user-editable since Nu changes it)

use chrono::NaiveDate;
use rusqlite::Connection;

use super::{param_i64, parse_params, parse_start_date, InvestmentCalculator};
use crate::error::AppResult;
use crate::models::Investment;

pub struct NuCajita;

impl InvestmentCalculator for NuCajita {
    fn id(&self) -> &'static str {
        "nu_cajita"
    }

    fn value_at(&self, inv: &Investment, _conn: &Connection, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;
        let rate_bps = param_i64(&params, "annual_rate_bps")?;
        let start = parse_start_date(inv)?;

        let days = (as_of - start).num_days().max(0);
        let r = rate_bps as f64 / 10_000.0;
        let value = inv.principal_cents as f64 * (1.0 + r / 365.0).powi(days as i32);
        Ok(value.round() as i64)
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None // open-ended savings
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;
    use crate::investments::test_investment;

    #[test]
    fn compounds_daily_act_365() {
        let conn = open_in_memory();
        // $10,000.00 at 15.00% annual after exactly one year (365 days):
        // 1_000_000 * (1 + 0.15/365)^365 = 1_161_798.4 -> $11,617.98
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let a_year_later = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(NuCajita.value_at(&inv, &conn, a_year_later).unwrap(), 1_161_798);
    }

    #[test]
    fn value_before_start_is_principal() {
        let conn = open_in_memory();
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let before = NaiveDate::from_ymd_opt(2025, 12, 1).unwrap();
        assert_eq!(NuCajita.value_at(&inv, &conn, before).unwrap(), 1_000_000);
    }

    #[test]
    fn day_zero_is_principal() {
        let conn = open_in_memory();
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let start = NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
        assert_eq!(NuCajita.value_at(&inv, &conn, start).unwrap(), 1_000_000);
    }
}
