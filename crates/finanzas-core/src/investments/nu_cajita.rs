//! Nu México "Cajitas": daily-compounding savings box.
//! Nu quotes an annual rate with daily accrual over a 360-day year (Mexican
//! banking convention), so the daily factor is `1 + r/360` — ACT/360.
//! params: {"annual_rate_bps": 1500}  (15.00%, user-editable since Nu changes it)

use chrono::NaiveDate;

use super::{param_i64, parse_params, position_value, CalcContext, InvestmentCalculator};
use crate::error::AppResult;
use crate::models::Investment;

pub struct NuCajita;

impl InvestmentCalculator for NuCajita {
    fn id(&self) -> &'static str {
        "nu_cajita"
    }

    fn value_at(&self, inv: &Investment, ctx: &CalcContext, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;
        let rate_bps = param_i64(&params, "annual_rate_bps")?;
        let r = rate_bps as f64 / 10_000.0;

        // Each contributed amount compounds daily from its own date.
        position_value(inv, ctx, as_of, |from| {
            let days = (as_of - from).num_days().max(0);
            (1.0 + r / 360.0).powi(days as i32)
        })
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None // open-ended savings
    }

    fn effective_annual_rate_bps(
        &self,
        inv: &Investment,
        _ctx: &CalcContext,
    ) -> AppResult<Option<i64>> {
        Ok(Some(param_i64(&parse_params(inv)?, "annual_rate_bps")?))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::investments::{test_ctx, test_investment};

    #[test]
    fn compounds_daily_act_360() {
        // $10,000.00 at 15.00% annual after exactly one calendar year (365
        // days accruing on a 360-day base — that gap is why the effective
        // yield beats the nominal rate):
        // 1_000_000 * (1 + 0.15/360)^365 = 1_164_220.4 -> $11,642.20
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let ctx = test_ctx(&[]);
        let a_year_later = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(
            NuCajita.value_at(&inv, &ctx, a_year_later).unwrap(),
            1_164_220
        );
    }

    #[test]
    fn cajita_turbo_full_360_day_cycle() {
        // Nu's Cajita Turbo at 13.00%: $10,000.00 compounded daily over 360
        // days is 1_000_000 * (1 + 0.13/360)^360 = 1_138_801.66 -> $11,388.02,
        // i.e. a 13.88% effective yield on the 13.00% nominal rate.
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1300}"#);
        let ctx = test_ctx(&[]);
        // start_date in test_investment is 2026-01-01; +360 days = 2026-12-27.
        let cycle_end = NaiveDate::from_ymd_opt(2026, 12, 27).unwrap();
        assert_eq!(NuCajita.value_at(&inv, &ctx, cycle_end).unwrap(), 1_138_802);
    }

    #[test]
    fn value_before_start_is_principal() {
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let ctx = test_ctx(&[]);
        let before = NaiveDate::from_ymd_opt(2025, 12, 1).unwrap();
        assert_eq!(NuCajita.value_at(&inv, &ctx, before).unwrap(), 1_000_000);
    }

    #[test]
    fn day_zero_is_principal() {
        let inv = test_investment("nu_cajita", 1_000_000, r#"{"annual_rate_bps": 1500}"#);
        let ctx = test_ctx(&[]);
        let start = NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
        assert_eq!(NuCajita.value_at(&inv, &ctx, start).unwrap(), 1_000_000);
    }
}
