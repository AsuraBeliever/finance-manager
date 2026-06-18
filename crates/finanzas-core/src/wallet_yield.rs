//! Yield-bearing wallets: plain wallets (not investments) whose balance grows
//! on its own, mirroring debit accounts like Klar or Nu that pay interest with
//! daily accrual (ACT/365 compounding) and a periodic payout. The worker's
//! daily cron calls these pure helpers, then posts one income transaction per
//! due period so the wallet's computed balance keeps matching the bank.
//!
//! Same compounding convention as the Nu cajita calculator; here it's applied
//! to a wallet's running balance instead of an investment position.

use chrono::{Duration, Months, NaiveDate};

/// The payout cadences a yield-bearing wallet can use.
pub const FREQUENCIES: &[&str] = &["weekly", "biweekly", "monthly"];

/// True when `frequency` is one we know how to schedule.
pub fn is_valid_frequency(frequency: &str) -> bool {
    FREQUENCIES.contains(&frequency)
}

/// End date of the next payout period after `last_paid`, or `None` for an
/// unknown cadence. The recurrence walks from the last paid cut, so the anchor
/// only sets where the very first period starts.
pub fn next_period_end(frequency: &str, last_paid: NaiveDate) -> Option<NaiveDate> {
    match frequency {
        "weekly" => Some(last_paid + Duration::days(7)),
        "biweekly" => Some(last_paid + Duration::days(14)),
        "monthly" => last_paid.checked_add_months(Months::new(1)),
        _ => None,
    }
}

/// Interest in cents accrued over `(start, end]`, daily-compounding ACT/365.
///
/// `start_balance` is the wallet's closing balance at `start` (it already
/// includes any previously paid interest, so payouts compound on themselves
/// just like the bank does). `txns` are the signed amounts (income/transfer-in
/// positive, expense/transfer-out negative) that occurred within `(start, end]`;
/// each one accrues only from its own date. Returns 0 for a non-positive rate,
/// an empty window, or an overdrawn balance — a debit account never charges.
pub fn accrued_interest(
    start_balance: i64,
    txns: &[(NaiveDate, i64)],
    start: NaiveDate,
    end: NaiveDate,
    annual_rate_bps: i64,
) -> i64 {
    if end <= start || annual_rate_bps <= 0 {
        return 0;
    }
    let r = annual_rate_bps as f64 / 10_000.0;
    let factor = |from: NaiveDate| {
        let days = (end - from).num_days().max(0);
        (1.0 + r / 365.0).powi(days as i32)
    };

    // Compounded value vs. plain end-of-period balance: the gap is the interest.
    let mut grown = start_balance as f64 * factor(start);
    let mut principal = start_balance as f64;
    for (date, amount) in txns {
        let from = (*date).clamp(start, end);
        grown += *amount as f64 * factor(from);
        principal += *amount as f64;
    }
    let interest = grown - principal;
    if interest <= 0.0 {
        0
    } else {
        interest.round() as i64
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    #[test]
    fn one_day_simple_interest() {
        // 36.50% annual is exactly 0.1%/day. $10,000.00 for one day → $10.00.
        assert_eq!(
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-02"), 3650),
            1_000
        );
    }

    #[test]
    fn deposit_accrues_from_its_own_date() {
        // 0.1%/day. Start $10,000 on Jan 1, valued Jan 3 (2 days, compounded):
        //   10000 * 1.001^2          = 10,020.01  → 20.01 interest
        // Deposit $10,000 on Jan 2 earns one day:
        //   10000 * 1.001            = 10,010.00  → 10.00 interest
        // Total interest = 30.01 → 3001 cents.
        assert_eq!(
            accrued_interest(
                1_000_000,
                &[(d("2026-01-02"), 1_000_000)],
                d("2026-01-01"),
                d("2026-01-03"),
                3650,
            ),
            3_001
        );
    }

    #[test]
    fn no_interest_on_zero_or_negative_inputs() {
        // empty window
        assert_eq!(
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-01"), 3650),
            0
        );
        // no rate
        assert_eq!(
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-08"), 0),
            0
        );
        // overdrawn balance never accrues a charge
        assert_eq!(
            accrued_interest(-500_000, &[], d("2026-01-01"), d("2026-01-08"), 3650),
            0
        );
    }

    #[test]
    fn weekly_klar_payout_is_realistic() {
        // Klar's ~3% on $10,000 for one week:
        // 1_000_000 * ((1 + 0.03/365)^7 - 1) ≈ 575 cents ($5.75).
        let interest = accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-08"), 300);
        assert_eq!(interest, 575);
    }

    #[test]
    fn period_schedule_advances_per_cadence() {
        assert_eq!(
            next_period_end("weekly", d("2026-01-01")),
            Some(d("2026-01-08"))
        );
        assert_eq!(
            next_period_end("biweekly", d("2026-01-01")),
            Some(d("2026-01-15"))
        );
        assert_eq!(
            next_period_end("monthly", d("2026-01-31")),
            Some(d("2026-02-28"))
        );
        assert_eq!(next_period_end("daily", d("2026-01-01")), None);
    }
}
