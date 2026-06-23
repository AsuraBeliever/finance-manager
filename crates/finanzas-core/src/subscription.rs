//! Subscription charge scheduling. A subscription recurs every month or year on
//! the day of its `next_charge_date`; this counts how many charges fall inside a
//! window `[lo, hi)` (already intersected with the active span by the caller).
//! Used to decide whether a subscription belongs to the selected dashboard
//! period — it appears only if it was actually charged then. Pure logic, tested
//! natively. Occurrences are computed from the original anchor (one calendar
//! clamp per step, never cumulative), so they stay strictly monotonic.

use chrono::{Datelike, Months, NaiveDate};

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
}
