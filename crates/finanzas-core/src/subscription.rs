//! Subscription charge scheduling. A subscription recurs every month or year on
//! the day of its `next_charge_date`; this counts how many charges fall inside a
//! window `[lo, hi)` (already intersected with the active span by the caller).
//! Used to decide whether a subscription belongs to the selected dashboard
//! period — it appears only if it was actually charged then. Pure logic, tested
//! natively. Occurrences are computed from the original anchor (one calendar
//! clamp per step, never cumulative), so they stay strictly monotonic.

use chrono::{Datelike, Months, NaiveDate, NaiveTime};

fn add_months(d: NaiveDate, months: i32) -> NaiveDate {
    let r = if months >= 0 {
        d.checked_add_months(Months::new(months as u32))
    } else {
        d.checked_sub_months(Months::new((-months) as u32))
    };
    r.unwrap_or(d)
}

/// Number of charges in `[lo, hi)` for a subscription anchored at `next_charge`
/// recurring every `cadence_months` (1 = monthly, 12 = yearly).
pub fn count_charges(
    next_charge: NaiveDate,
    cadence_months: i32,
    lo: NaiveDate,
    hi: NaiveDate,
) -> i64 {
    if lo >= hi || cadence_months <= 0 {
        return 0;
    }
    let occ = |k: i32| add_months(next_charge, k * cadence_months);
    // Start near `lo`, then walk to the exact first occurrence >= lo.
    let mut k = ((lo.year() - next_charge.year()) * 12 + lo.month() as i32
        - next_charge.month() as i32)
        / cadence_months;
    while occ(k) >= lo {
        k -= 1;
    }
    while occ(k) < lo {
        k += 1;
    }
    let first = k;
    // Walk to the last occurrence < hi.
    while occ(k) < hi {
        k += 1;
    }
    while occ(k) >= hi {
        k -= 1;
    }
    let last = k;
    if last >= first {
        (last - first + 1) as i64
    } else {
        0
    }
}

/// Charge dates owed by an auto-charging subscription as of `today`: every
/// occurrence from `next_charge` (inclusive) up to `today` (inclusive), plus the
/// new `next_charge_date` (first occurrence after `today`). Occurrences come
/// from the anchor, so a Jan 31 anchor goes Feb 28 → Mar 31, never drifting.
/// Capped at `max` dates so a long-dormant subscription can't flood the ledger;
/// when capped, the new next date is the occurrence right after the last one.
pub fn due_charges(
    next_charge: NaiveDate,
    cadence_months: i32,
    today: NaiveDate,
    max: usize,
) -> (Vec<NaiveDate>, NaiveDate) {
    let step = cadence_months.max(1);
    let occ = |k: i32| add_months(next_charge, k * step);
    let mut due = Vec::new();
    let mut k = 0;
    while occ(k) <= today && due.len() < max {
        due.push(occ(k));
        k += 1;
    }
    (due, occ(k))
}

/// Last calendar day whose charge is already owed at wall-clock `now` (Mexico
/// City). A charge is owed on its date once `now` reaches the subscription's
/// `charge_time`; without one (`None`) it is owed from the start of the day.
/// Feed the result to [`due_charges`] as its `today`.
pub fn charge_cutoff(
    today: NaiveDate,
    now: NaiveTime,
    charge_time: Option<NaiveTime>,
) -> NaiveDate {
    match charge_time {
        Some(t) if now < t => today.pred_opt().unwrap_or(today),
        _ => today,
    }
}

/// The charge date after `current` (one cadence step), used when a payment is
/// registered by hand.
pub fn next_after(current: NaiveDate, cadence_months: i32) -> NaiveDate {
    add_months(current, cadence_months.max(1))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(y: i32, m: u32, day: u32) -> NaiveDate {
        NaiveDate::from_ymd_opt(y, m, day).unwrap()
    }

    #[test]
    fn monthly_charged_once_in_its_month() {
        // Next charge Jun 25; June period → one charge.
        assert_eq!(
            count_charges(d(2026, 6, 25), 1, d(2026, 6, 1), d(2026, 7, 1)),
            1
        );
    }

    #[test]
    fn monthly_counts_a_past_occurrence_before_next_charge() {
        // Next charge Jul 2 → it was charged Jun 2 too; June period → one charge.
        assert_eq!(
            count_charges(d(2026, 7, 2), 1, d(2026, 6, 1), d(2026, 7, 1)),
            1
        );
    }

    #[test]
    fn yearly_not_charged_outside_its_month() {
        // Yearly on Nov 1 → no charge in June.
        assert_eq!(
            count_charges(d(2026, 11, 1), 12, d(2026, 6, 1), d(2026, 7, 1)),
            0
        );
    }

    #[test]
    fn yearly_charged_in_its_month() {
        assert_eq!(
            count_charges(d(2026, 11, 1), 12, d(2026, 11, 1), d(2026, 12, 1)),
            1
        );
    }

    #[test]
    fn monthly_over_six_months_counts_six() {
        // Anchored on the 15th; Jan..Jun = 6 charges.
        assert_eq!(
            count_charges(d(2026, 4, 15), 1, d(2026, 1, 1), d(2026, 7, 1)),
            6
        );
    }

    #[test]
    fn end_of_month_anchor_stays_monotonic() {
        // Jan 31 anchor: Feb clamps to 28 but each month still lands once.
        assert_eq!(
            count_charges(d(2026, 1, 31), 1, d(2026, 2, 1), d(2026, 3, 1)),
            1
        );
    }

    #[test]
    fn empty_window_has_no_charges() {
        assert_eq!(
            count_charges(d(2026, 6, 25), 1, d(2026, 6, 10), d(2026, 6, 10)),
            0
        );
    }

    #[test]
    fn not_due_yet_charges_nothing() {
        let (due, next) = due_charges(d(2026, 10, 16), 1, d(2026, 10, 10), 24);
        assert!(due.is_empty());
        assert_eq!(next, d(2026, 10, 16));
    }

    #[test]
    fn due_today_charges_once_and_advances() {
        let (due, next) = due_charges(d(2026, 10, 16), 1, d(2026, 10, 16), 24);
        assert_eq!(due, vec![d(2026, 10, 16)]);
        assert_eq!(next, d(2026, 11, 16));
    }

    #[test]
    fn missed_months_are_caught_up_on_their_own_dates() {
        // Due Aug 25, cron next ran Oct 10 → Aug 25 and Sep 25 owed; next Oct 25.
        let (due, next) = due_charges(d(2026, 8, 25), 1, d(2026, 10, 10), 24);
        assert_eq!(due, vec![d(2026, 8, 25), d(2026, 9, 25)]);
        assert_eq!(next, d(2026, 10, 25));
    }

    #[test]
    fn end_of_month_anchor_does_not_drift() {
        // Jan 31 → Feb 28 → Mar 31 (not Mar 28, not SQLite's Mar 3).
        let (due, next) = due_charges(d(2026, 1, 31), 1, d(2026, 3, 1), 24);
        assert_eq!(due, vec![d(2026, 1, 31), d(2026, 2, 28)]);
        assert_eq!(next, d(2026, 3, 31));
    }

    #[test]
    fn yearly_advances_a_year() {
        let (due, next) = due_charges(d(2026, 3, 1), 12, d(2026, 10, 10), 24);
        assert_eq!(due, vec![d(2026, 3, 1)]);
        assert_eq!(next, d(2027, 3, 1));
    }

    #[test]
    fn catch_up_is_capped() {
        let (due, next) = due_charges(d(2026, 1, 5), 1, d(2026, 10, 10), 3);
        assert_eq!(due, vec![d(2026, 1, 5), d(2026, 2, 5), d(2026, 3, 5)]);
        assert_eq!(next, d(2026, 4, 5));
    }

    fn t(h: u32, m: u32) -> NaiveTime {
        NaiveTime::from_hms_opt(h, m, 0).unwrap()
    }

    #[test]
    fn no_charge_time_is_owed_from_midnight() {
        assert_eq!(
            charge_cutoff(d(2026, 10, 16), t(0, 5), None),
            d(2026, 10, 16)
        );
    }

    #[test]
    fn before_charge_time_today_is_not_owed_yet() {
        // 08:55 with a 09:00 charge: today's charge waits, yesterday's is owed.
        assert_eq!(
            charge_cutoff(d(2026, 10, 16), t(8, 55), Some(t(9, 0))),
            d(2026, 10, 15)
        );
        let (due, next) = due_charges(
            d(2026, 10, 16),
            1,
            charge_cutoff(d(2026, 10, 16), t(8, 55), Some(t(9, 0))),
            12,
        );
        assert!(due.is_empty());
        assert_eq!(next, d(2026, 10, 16));
    }

    #[test]
    fn at_or_after_charge_time_today_is_owed() {
        assert_eq!(
            charge_cutoff(d(2026, 10, 16), t(9, 0), Some(t(9, 0))),
            d(2026, 10, 16)
        );
        assert_eq!(
            charge_cutoff(d(2026, 10, 16), t(23, 59), Some(t(9, 0))),
            d(2026, 10, 16)
        );
    }

    #[test]
    fn late_charge_time_still_catches_up_past_days() {
        // Due Oct 15 at 22:00, cron at Oct 16 08:00 → Oct 15 owed, next Nov 15.
        let cutoff = charge_cutoff(d(2026, 10, 16), t(8, 0), Some(t(22, 0)));
        let (due, next) = due_charges(d(2026, 10, 15), 1, cutoff, 12);
        assert_eq!(due, vec![d(2026, 10, 15)]);
        assert_eq!(next, d(2026, 11, 15));
    }

    #[test]
    fn manual_payment_steps_one_cadence() {
        assert_eq!(next_after(d(2026, 10, 16), 1), d(2026, 11, 16));
        assert_eq!(next_after(d(2026, 1, 31), 1), d(2026, 2, 28));
        assert_eq!(next_after(d(2026, 3, 1), 12), d(2027, 3, 1));
    }
}
