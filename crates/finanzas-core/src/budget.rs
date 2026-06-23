//! Budget limit proration. A budget limit is a per-month amount, but a dashboard
//! period can be a day, a month, or a range spanning several months — and the
//! monthly limit may have changed over time. This computes the limit that
//! applies to an arbitrary window: each calendar month contributes its full
//! monthly limit, a partial month contributes a day-prorated share, and the
//! limit in effect is whichever change was active that month. Pure logic, tested
//! natively; the worker loads the history rows from D1 and calls this.

use chrono::{Datelike, Months, NaiveDate};

fn first_of_month(d: NaiveDate) -> NaiveDate {
    NaiveDate::from_ymd_opt(d.year(), d.month(), 1).expect("day 1 always valid")
}

/// `history` is `(effective_from, limit_cents)` sorted ascending by date. Returns
/// the limit in effect on the first day of `month` (the latest change at or
/// before it), or 0 if none applies yet.
fn limit_for_month(history: &[(NaiveDate, i64)], month: NaiveDate) -> i64 {
    history
        .iter()
        .rev()
        .find(|(ef, _)| *ef <= month)
        .map(|(_, l)| *l)
        .unwrap_or(0)
}

/// Sum of the monthly limit prorated by day over `[start, end)` (end exclusive),
/// honoring limit changes. A full calendar month contributes its full limit; a
/// partial month contributes `limit * overlap_days / days_in_month`.
pub fn prorated_limit(history: &[(NaiveDate, i64)], start: NaiveDate, end: NaiveDate) -> i64 {
    let mut total: i128 = 0;
    let mut month = first_of_month(start);
    while month < end {
        let next = month + Months::new(1);
        let lo = month.max(start);
        let hi = next.min(end);
        if lo < hi {
            let overlap = (hi - lo).num_days() as i128;
            let days_in_month = (next - month).num_days() as i128;
            total += limit_for_month(history, month) as i128 * overlap / days_in_month;
        }
        month = next;
    }
    total as i64
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(y: i32, m: u32, day: u32) -> NaiveDate {
        NaiveDate::from_ymd_opt(y, m, day).unwrap()
    }

    // A single constant limit from the epoch.
    fn flat(limit: i64) -> Vec<(NaiveDate, i64)> {
        vec![(d(1970, 1, 1), limit)]
    }

    #[test]
    fn full_month_is_the_monthly_limit() {
        let h = flat(500_000);
        assert_eq!(prorated_limit(&h, d(2026, 4, 1), d(2026, 5, 1)), 500_000);
    }

    #[test]
    fn six_months_is_six_times_the_limit() {
        let h = flat(500_000);
        // Jan..Jun 2026 = 6 full months.
        assert_eq!(prorated_limit(&h, d(2026, 1, 1), d(2026, 7, 1)), 3_000_000);
    }

    #[test]
    fn single_day_is_a_daily_share() {
        let h = flat(300_000);
        // 1 day of April (30 days): 300000 * 1 / 30 = 10000.
        assert_eq!(prorated_limit(&h, d(2026, 4, 10), d(2026, 4, 11)), 10_000);
    }

    #[test]
    fn partial_range_prorates_each_month() {
        let h = flat(300_000);
        // Apr 16..May 16: 15 days of April (30) + 15 days of May (31).
        // 300000*15/30 + 300000*15/31 = 150000 + 145161 = 295161.
        assert_eq!(prorated_limit(&h, d(2026, 4, 16), d(2026, 5, 16)), 295_161);
    }

    #[test]
    fn honors_a_limit_change_mid_range() {
        // 4,000 until May, then 5,000 from May 1.
        let h = vec![(d(1970, 1, 1), 400_000), (d(2026, 5, 1), 500_000)];
        // Mar..Jun (3 full months): Mar 4000 + Apr 4000 + May 5000 = 13,000.
        assert_eq!(prorated_limit(&h, d(2026, 3, 1), d(2026, 6, 1)), 1_300_000);
    }
}
