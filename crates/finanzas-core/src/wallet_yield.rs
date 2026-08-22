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
    accrued_interest_scheduled(
        start_balance,
        txns,
        start,
        end,
        &[(NaiveDate::MIN, annual_rate_bps)],
    )
}

/// The annual rate in effect on `day`, from a history sorted ascending by
/// effective date: the last entry that had already started. `None` when `day`
/// falls before the earliest known rate — the caller must not guess, since
/// applying today's rate to a day that ran at a different one would repaint
/// history (a wallet moved from 6.50% to 13.00% would have its whole past
/// inflated).
pub fn rate_on(rates: &[(NaiveDate, i64)], day: NaiveDate) -> Option<i64> {
    rates
        .iter()
        .rev()
        .find(|(from, _)| *from <= day)
        .map(|(_, bps)| *bps)
}

/// Like [`accrued_interest`], but the annual rate may change mid-window:
/// `rates` is the wallet's rate history (ascending by effective date) and each
/// day accrues at whatever rate was in effect that day. Days before the
/// earliest known rate accrue nothing.
///
/// This is what lets a correction be recomputed over past days without
/// repainting them at the current rate.
pub fn accrued_interest_scheduled(
    start_balance: i64,
    txns: &[(NaiveDate, i64)],
    start: NaiveDate,
    end: NaiveDate,
    rates: &[(NaiveDate, i64)],
) -> i64 {
    if end <= start || rates.is_empty() {
        return 0;
    }

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
        let bps = rate_on(rates, day).unwrap_or(0);
        if balance > 0 && bps > 0 {
            let daily_rate = bps as f64 / 10_000.0 / 360.0;
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

/// What the wallet is still owed (positive) or was overpaid (negative) across
/// the days in `[start, end)`, given `posted` — the interest actually credited
/// for those days.
///
/// The forward accrual can only look at periods it has not paid yet, so a
/// movement typed in after its day was closed leaves the wallet permanently
/// short. Re-deriving the whole stretch and settling the gap is what repairs
/// it, and it works the same for a back-dated deposit, a corrected amount, or a
/// deleted transaction.
///
/// Window alignment is the part that has to be exact: interest for day `d` is
/// credited on `d + 1`, so `posted` must be the sum of the postings **dated in
/// `(start, end]`** while `start_balance` is the closing balance **at `start`**
/// and `txns` are the external movements in `(start, end]`. Feeding in the
/// wrong boundary silently pays a day too much or too little.
pub fn reconciliation_delta(
    start_balance: i64,
    txns: &[(NaiveDate, i64)],
    posted: i64,
    start: NaiveDate,
    end: NaiveDate,
    rates: &[(NaiveDate, i64)],
) -> i64 {
    accrued_interest_scheduled(start_balance, txns, start, end, rates) - posted
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    /// Replays the cron the way it really runs: one posting per day, credited
    /// the following day, compounding on what was already paid. Returns the
    /// postings as `(date_credited, cents)`.
    fn replay(
        start_balance: i64,
        txns: &[(NaiveDate, i64)],
        start: NaiveDate,
        end: NaiveDate,
        rate_bps: i64,
    ) -> Vec<(NaiveDate, i64)> {
        let mut posts = Vec::new();
        let mut day = start;
        let mut balance = start_balance;
        while day < end {
            for (date, amount) in txns {
                if *date == day {
                    balance += *amount;
                }
            }
            let inc = accrued_interest(balance, &[], day, day + Duration::days(1), rate_bps);
            balance += inc;
            posts.push((day + Duration::days(1), inc));
            day += Duration::days(1);
        }
        posts
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
    fn rate_on_resolves_the_day_and_refuses_to_guess() {
        let rates = [(d("2026-08-01"), 650), (d("2026-08-21"), 1300)];
        // before the earliest known rate we must not guess
        assert_eq!(rate_on(&rates, d("2026-07-31")), None);
        // effective_from is inclusive: the change applies on its own day
        assert_eq!(rate_on(&rates, d("2026-08-01")), Some(650));
        assert_eq!(rate_on(&rates, d("2026-08-20")), Some(650));
        assert_eq!(rate_on(&rates, d("2026-08-21")), Some(1300));
        assert_eq!(rate_on(&rates, d("2027-01-01")), Some(1300));
        assert_eq!(rate_on(&[], d("2026-08-21")), None);
    }

    #[test]
    fn rate_change_applies_from_its_own_day() {
        // Real case: a Nu cajita moved to the Cajita Turbo, 6.50% → 13.00% on
        // 2026-08-21. Recomputing days 19, 20 and 21 must pay the first two at
        // the OLD rate and only the last one at the new one:
        //   day 19 @6.50%: 1000000 × 0.065/360 = 180.56¢ → 181¢ (bal 1_000_181)
        //   day 20 @6.50%: 1000181 × 0.065/360 = 180.58¢ → 181¢ (bal 1_000_362)
        //   day 21 @13.00%: 1000362 × 0.13/360 = 361.24¢ → 361¢
        // Total 723¢. Repainting all three at 13% would pay 1083¢ instead.
        let rates = [(d("2026-08-01"), 650), (d("2026-08-21"), 1300)];
        let interest =
            accrued_interest_scheduled(1_000_000, &[], d("2026-08-19"), d("2026-08-22"), &rates);
        assert_eq!(interest, 723);
    }

    #[test]
    fn days_before_the_earliest_known_rate_accrue_nothing() {
        // The window opens two days before the rate history starts; only the
        // days we can price are paid (1 day at 0.1%/day on $10,000 = 1000¢).
        let rates = [(d("2026-08-03"), 3600)];
        let interest =
            accrued_interest_scheduled(1_000_000, &[], d("2026-08-01"), d("2026-08-04"), &rates);
        assert_eq!(interest, 1_000);
    }

    #[test]
    fn back_dated_deposit_is_worth_the_days_it_missed() {
        // The whole point of reconciliation: a $750.00 deposit registered late
        // but dated 2026-08-21 must earn from that day, not from the day it was
        // typed in. Over the window (19, 22] at 13.00%:
        //   without it: 361 + 361 + 361 = 1083¢
        //   with it:    361 + 388 + 388 = 1137¢   (day 21 base is $10,753.61)
        // so the wallet is owed 54¢ it never got paid.
        let rates = [(d("2026-08-01"), 1300)];
        let without =
            accrued_interest_scheduled(1_000_000, &[], d("2026-08-19"), d("2026-08-22"), &rates);
        let with = accrued_interest_scheduled(
            1_000_000,
            &[(d("2026-08-20"), 75_000)],
            d("2026-08-19"),
            d("2026-08-22"),
            &rates,
        );
        assert_eq!(without, 1_083);
        assert_eq!(with, 1_137);
        assert_eq!(with - without, 54);
    }

    #[test]
    fn single_rate_wrapper_matches_the_schedule() {
        // accrued_interest is just a one-entry schedule; both must agree.
        let txns = [(d("2026-08-03"), 50_000)];
        let flat = accrued_interest(1_000_000, &txns, d("2026-08-01"), d("2026-08-08"), 300);
        let sched = accrued_interest_scheduled(
            1_000_000,
            &txns,
            d("2026-08-01"),
            d("2026-08-08"),
            &[(d("2026-01-01"), 300)],
        );
        assert_eq!(flat, sched);
    }

    #[test]
    fn reconciliation_is_zero_when_nothing_was_missed() {
        // A wallet the cron has been paying correctly must never be "corrected".
        // Same window on both sides: postings dated in (10, 22], balance at 10.
        let rates = [(d("2026-08-10"), 1300)];
        let (start, end) = (d("2026-08-10"), d("2026-08-22"));
        let posts = replay(1_000_000, &[], start, end, 1300);
        let posted: i64 = posts.iter().map(|(_, c)| c).sum();
        // the postings really do land inside (start, end]
        assert!(posts.iter().all(|(day, _)| *day > start && *day <= end));
        assert_eq!(
            reconciliation_delta(1_000_000, &[], posted, start, end, &rates),
            0
        );
    }

    #[test]
    fn reconciliation_pays_a_deposit_registered_after_its_day_closed() {
        // The scenario this whole pass exists for. $10,000.00 at 13.00%, the
        // cron pays days 10..21 with no deposit in sight. Then a $750.00
        // deposit dated the 20th is typed in late — its two days of yield were
        // never paid and the cursor will never look back.
        let rates = [(d("2026-08-10"), 1300)];
        let (start, end) = (d("2026-08-10"), d("2026-08-22"));
        let posted: i64 = replay(1_000_000, &[], start, end, 1300)
            .iter()
            .map(|(_, c)| c)
            .sum();
        assert_eq!(posted, 4_342);

        let late = [(d("2026-08-20"), 75_000)];
        // Days 20 and 21 should have accrued on $10,753.xx instead of $10,003.xx:
        //   owed 4397¢ vs the 4342¢ actually paid = 55¢ still due.
        let delta = reconciliation_delta(1_000_000, &late, posted, start, end, &rates);
        assert_eq!(delta, 55);

        // And once paid, a second run must settle on zero — the pass has to be
        // idempotent across the three daily cron runs.
        assert_eq!(
            reconciliation_delta(1_000_000, &late, posted + delta, start, end, &rates),
            0
        );
    }

    #[test]
    fn reconciliation_claws_back_a_deleted_deposit() {
        // Mirror image: the deposit was paid yield and then deleted, so the
        // wallet was overpaid and the delta must come back negative.
        let rates = [(d("2026-08-10"), 1300)];
        let (start, end) = (d("2026-08-10"), d("2026-08-22"));
        let with = [(d("2026-08-20"), 75_000)];
        let posted: i64 = replay(1_000_000, &with, start, end, 1300)
            .iter()
            .map(|(_, c)| c)
            .sum();
        assert_eq!(
            reconciliation_delta(1_000_000, &[], posted, start, end, &rates),
            -55
        );
    }

    #[test]
    fn reconciliation_prices_past_days_at_their_own_rate() {
        // A late deposit landing before a rate change must be paid at the OLD
        // rate for those days. Deposit on the 19th, rate jumps 6.50% → 13.00%
        // on the 21st; days 19 and 20 are worth 6.50% and only day 21 is worth
        // 13.00%. Repainting the window at 13% would overpay.
        let rates = [(d("2026-08-10"), 650), (d("2026-08-21"), 1300)];
        let (start, end) = (d("2026-08-19"), d("2026-08-22"));
        let late = [(d("2026-08-19"), 75_000)];

        let honest = accrued_interest_scheduled(1_000_000, &late, start, end, &rates);
        let repainted =
            accrued_interest_scheduled(1_000_000, &late, start, end, &[(d("2026-08-10"), 1300)]);
        // On $10,750.00: 194¢ + 194¢ at 6.50%, then 388¢ at 13.00%.
        assert_eq!(honest, 776);
        // Repainting all three days at 13% would pay 388 × 3 instead.
        assert_eq!(repainted, 1_164);
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
