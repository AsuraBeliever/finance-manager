//! CETES (cetesdirecto): zero-coupon Mexican treasury bills bought at a
//! discount, paying face value ($10.00 per título) at maturity.
//! Money-market convention ACT/360 for yield; ISR retention prorates ACT/365
//! over invested capital.
//! params: {"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 50}
//! plazo_days ∈ {28, 91, 182, 364}; isr_rate_bps = 0 disables retention.

use chrono::{Duration, NaiveDate};
use rusqlite::Connection;

use super::{param_i64, param_i64_or, parse_params, parse_start_date, InvestmentCalculator};
use crate::error::{AppError, AppResult};
use crate::models::Investment;

pub const VALID_PLAZOS: [i64; 4] = [28, 91, 182, 364];

pub struct Cetes;

impl InvestmentCalculator for Cetes {
    fn id(&self) -> &'static str {
        "cetes"
    }

    fn value_at(&self, inv: &Investment, _conn: &Connection, as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;
        let rate_bps = param_i64(&params, "annual_rate_bps")?;
        let plazo_days = param_i64(&params, "plazo_days")?;
        let isr_bps = param_i64_or(&params, "isr_rate_bps", 0);
        if !VALID_PLAZOS.contains(&plazo_days) {
            return Err(AppError::InvalidInput(format!(
                "plazo inválido: {plazo_days} (válidos: 28, 91, 182, 364)"
            )));
        }
        let start = parse_start_date(inv)?;

        // Accrual stops at maturity: value is flat afterwards.
        let d = (as_of - start).num_days().clamp(0, plazo_days) as f64;
        let r = rate_bps as f64 / 10_000.0;
        let gross = inv.principal_cents as f64 * (1.0 + r * d / 360.0);
        let isr = inv.principal_cents as f64 * (isr_bps as f64 / 10_000.0) * d / 365.0;
        Ok((gross - isr).round() as i64)
    }

    fn maturity_date(&self, inv: &Investment) -> Option<NaiveDate> {
        let params = parse_params(inv).ok()?;
        let plazo_days = param_i64(&params, "plazo_days").ok()?;
        Some(parse_start_date(inv).ok()? + Duration::days(plazo_days))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;
    use crate::investments::test_investment;

    #[test]
    fn gross_value_at_maturity_act_360() {
        let conn = open_in_memory();
        // $10,000.00 at 10.80% for 91 days, no ISR:
        // 1_000_000 * (1 + 0.108 * 91/360) = 1_027_300 -> $10,273.00
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 0}"#,
        );
        let maturity = NaiveDate::from_ymd_opt(2026, 4, 2).unwrap(); // start + 91d
        assert_eq!(Cetes.value_at(&inv, &conn, maturity).unwrap(), 1_027_300);
        assert_eq!(Cetes.maturity_date(&inv), Some(maturity));
    }

    #[test]
    fn isr_retention_prorates_act_365() {
        let conn = open_in_memory();
        // Same as above with ISR 0.50% annual on capital:
        // isr = 1_000_000 * 0.005 * 91/365 = 1_246.58
        // 1_027_300 - 1_246.58 = 1_026_053.4 -> $10,260.53
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 50}"#,
        );
        let maturity = NaiveDate::from_ymd_opt(2026, 4, 2).unwrap();
        assert_eq!(Cetes.value_at(&inv, &conn, maturity).unwrap(), 1_026_053);
    }

    #[test]
    fn value_is_flat_after_maturity_and_principal_before_start() {
        let conn = open_in_memory();
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 0}"#,
        );
        let way_later = NaiveDate::from_ymd_opt(2027, 1, 1).unwrap();
        assert_eq!(Cetes.value_at(&inv, &conn, way_later).unwrap(), 1_027_300);
        let before = NaiveDate::from_ymd_opt(2025, 12, 1).unwrap();
        assert_eq!(Cetes.value_at(&inv, &conn, before).unwrap(), 1_000_000);
    }

    #[test]
    fn rejects_invalid_plazo() {
        let conn = open_in_memory();
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 90}"#,
        );
        let date = NaiveDate::from_ymd_opt(2026, 2, 1).unwrap();
        assert!(Cetes.value_at(&inv, &conn, date).is_err());
    }
}
