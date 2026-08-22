//! Yield-bearing wallets: plain wallets (not investments) whose balance grows
//! on its own, mirroring debit accounts like Klar or Nu that pay interest with
//! daily accrual (ACT/360 compounding) and a periodic payout. The worker's
//! daily cron calls these pure helpers, then posts one income transaction per
//! due period so the wallet's computed balance keeps matching the bank.
//!
//! Same compounding convention as the Nu cajita calculator; here it's applied
//! to a wallet's running balance instead of an investment position.

use chrono::{Duration, Months, NaiveDate};

/// The payout cadences a yield-bearing wallet can use. 'daily' is what the Nu
/// cajitas do: the interest earned each day is credited that same day.
pub const FREQUENCIES: &[&str] = &["daily", "weekly", "biweekly", "monthly"];

/// True when `frequency` is one we know how to schedule.
pub fn is_valid_frequency(frequency: &str) -> bool {
    FREQUENCIES.contains(&frequency)
}

/// End date of the next payout period after `last_paid`, or `None` for an
/// unknown cadence. The recurrence walks from the last paid cut, so the anchor
/// only sets where the very first period starts.
pub fn next_period_end(frequency: &str, last_paid: NaiveDate) -> Option<NaiveDate> {
    match frequency {
        "daily" => Some(last_paid + Duration::days(1)),
        "weekly" => Some(last_paid + Duration::days(7)),
        "biweekly" => Some(last_paid + Duration::days(14)),
        "monthly" => last_paid.checked_add_months(Months::new(1)),
        _ => None,
    }
}

/// Interest in cents accrued over `(start, end]`, daily-compounding **ACT/360**
/// with the interest **rounded to the cent each day** — exactly how debit
/// accounts like Klar or Nu credit daily interest. Rounding once per day (not
/// once per period) matters at small balances: e.g. $334.73 at 3% accrues
/// 2.79¢/day, which the bank rounds up to 3¢ and pays 21¢/week, whereas a
/// single end-of-week rounding would land at only 20¢.
///
/// The 360-day base is not a rounding shortcut: Mexican banks quote the annual
/// rate over a 360-day year by regulation, so the daily accrual is
/// `saldo × tasa / 360`. Using 365 silently underpays by 1.39% of the interest
/// every day — invisible on small balances, but it drifts away from the bank's
/// own number as the balance grows (see docs/DECISIONS.md).
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
    let daily_rate = annual_rate_bps as f64 / 10_000.0 / 360.0;

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
        // On a 360-day base, 36.00% annual is exactly 0.1%/day.
        // $10,000.00 for one day → 1000000 × 0.36/360 = 1000¢ = $10.00.
        assert_eq!(
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-02"), 3600),
            1_000
        );
    }

    #[test]
    fn deposit_accrues_from_its_own_date() {
        // 0.1%/day (36.00% ACT/360), rounded to the cent daily.
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
                3600,
            ),
            3_001
        );
    }

    #[test]
    fn no_interest_on_zero_or_negative_inputs() {
        // empty window
        assert_eq!(
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-01"), 3600),
            0
        );
        // no rate
        assert_eq!(
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-08"), 0),
            0
        );
        // overdrawn balance never accrues a charge
        assert_eq!(
            accrued_interest(-500_000, &[], d("2026-01-01"), d("2026-01-08"), 3600),
            0
        );
    }

    #[test]
    fn weekly_klar_payout_is_realistic() {
        // Klar's 3% on $10,000 for one week: 1000000 × 0.03/360 = 83.33¢/day,
        // rounded to 83¢. Compounding is too small to bump any day to 84¢
        // (day 7 base is only $10,004.98), so 7 × 83 = 581 cents ($5.81).
        let interest = accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-08"), 300);
        assert_eq!(interest, 581);
    }

    #[test]
    fn small_balance_daily_rounding_matches_bank() {
        // Real Klar case: $334.73 at 3% pays 0.21/week because the bank rounds
        // 2.79¢/day up to 3¢ (3 × 7 = 21), not 0.20 from a single weekly round.
        // This observed value holds on either day-count base, so it stays a
        // valid anchor for the daily-rounding rule itself.
        let interest = accrued_interest(33_473, &[], d("2026-06-17"), d("2026-06-24"), 300);
        assert_eq!(interest, 21);
    }

    #[test]
    fn matches_nu_cajita_turbo_daily_payout() {
        // Observed on a real Nu Cajita Turbo (13.00% annual): a $6,768.56
        // balance pays $2.44 for one day.
        //   676856 × 0.13/360 = 244.42¢ → 244¢
        // Under the old ACT/365 base this returned 241¢ — the 1.39%/day
        // shortfall that drifted the wallet away from Nu's own balance.
        let interest = accrued_interest(676_856, &[], d("2026-08-21"), d("2026-08-22"), 1300);
        assert_eq!(interest, 244);
    }

    #[test]
    fn period_schedule_advances_per_cadence() {
        assert_eq!(
            next_period_end("daily", d("2026-01-01")),
            Some(d("2026-01-02"))
        );
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
        assert_eq!(next_period_end("yearly", d("2026-01-01")), None);
    }

    #[test]
    fn daily_payout_matches_a_week_of_daily_ones() {
        // A Nu cajita pays what it earned each day, that same day. Paying
        // day by day must total the same as one weekly payout over the same
        // stretch: both compound on the balance already credited.
        // $10,000.00 at 3%: 83.33¢/day → 83¢ the first day, and the payout
        // makes the next day's base a little bigger each time.
        let mut balance = 1_000_000;
        let mut day = d("2026-01-01");
        let mut total = 0;
        for _ in 0..7 {
            let paid = accrued_interest(balance, &[], day, day + Duration::days(1), 300);
            total += paid;
            balance += paid;
            day += Duration::days(1);
        }
        assert_eq!(total, 581);
        assert_eq!(
            total,
            accrued_interest(1_000_000, &[], d("2026-01-01"), d("2026-01-08"), 300)
        );
    }
}
