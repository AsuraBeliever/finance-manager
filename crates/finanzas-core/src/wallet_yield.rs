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

/// Interest in cents accrued over `(start, end]`, daily-compounding ACT/365
/// with the interest **rounded to the cent each day** — exactly how debit
/// accounts like Klar or Nu credit daily interest. Rounding once per day (not
/// once per period) matters at small balances: e.g. $334.73 at 3% accrues
/// 2.75¢/day, which the bank rounds up to 3¢ and pays 21¢/week, whereas a
/// single end-of-week rounding would land at only 19¢.
///
/// `start_balance` is the wallet's closing balance at `start` (it already
/// includes any previously paid interest, so payouts compound on themselves
/// just like the bank does). `txns` are the signed amounts (income/transfer-in
/// positive, expense/transfer-out negative) that occurred within `(start, end]`;
/// each one lands on its own date and starts earning that same day. Returns 0
/// for a non-positive rate, an empty window, or an overdrawn balance — a debit
/// account never charges.
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
    let daily_rate = annual_rate_bps as f64 / 10_000.0 / 365.0;

    // Walk each day in (start, end]: today's deposits/withdrawals land first,
    // then interest is computed on the running balance, rounded to the cent,
    // and credited so it compounds into tomorrow — mirroring a bank that pays
    // and rounds interest daily.
    let mut balance = start_balance;
    let mut interest_total: i64 = 0;
    let mut day = start;
    while day < end {
        for (date, amount) in txns {
            if *date == day {
                balance += *amount;
            }
        }
        if balance > 0 {
            let inc = (balance as f64 * daily_rate).round() as i64;
            if inc > 0 {
                balance += inc;
                interest_total += inc;
            }
        }
        day += Duration::days(1);
    }
    interest_total
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
        // 0.1%/day, rounded to the cent daily.
        // Day Jan 1 on $10,000.00: round(1000000 * 0.001) = 1000¢ → bal 1_001_000.
        // Day Jan 2 the $10,000 deposit lands first (bal 2_001_000), then
        //   round(2001000 * 0.001) = 2001¢.
        // Total interest = 1000 + 2001 = 3001 cents.
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
        // Klar's 3% on $10,000 for one week, rounding 82.19¢/day down to 82¢
        // each of the 7 days = 574 cents ($5.74).
        let interest = accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-08"), 300);
        assert_eq!(interest, 574);
    }

    #[test]
    fn small_balance_daily_rounding_matches_bank() {
        // Real Klar case: $334.73 at 3% pays 0.21/week because the bank rounds
        // 2.75¢/day up to 3¢ (3 × 7 = 21), not 0.19 from a single weekly round.
        let interest = accrued_interest(33_473, &[], d("2026-06-17"), d("2026-06-24"), 300);
        assert_eq!(interest, 21);
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
