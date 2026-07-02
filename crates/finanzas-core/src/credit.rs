//! Credit-card cycle math: cut dates, payment due dates and MSI (meses sin
//! intereses) installment schedules. A credit-card wallet stores only its
//! configuration (cut day, days to pay, limit); everything date- or
//! money-derived comes from these pure helpers so the worker and any future
//! caller agree on the same calendar.
//!
//! Convention: the statement "closes" at the end of the cut day, so a
//! transaction dated on the cut day belongs to the statement that closes that
//! same day.

use chrono::{Datelike, Duration, NaiveDate};

/// `cut_day` clamped into `(year, month)`: day 31 in February becomes the
/// 28th/29th, mirroring how banks slide month-end cut days.
fn cut_in_month(year: i32, month: u32, cut_day: u32) -> NaiveDate {
    let day = cut_day.clamp(1, 31);
    NaiveDate::from_ymd_opt(year, month, day).unwrap_or_else(|| {
        // Roll back from the 1st of the next month to its last day.
        let (ny, nm) = if month == 12 {
            (year + 1, 1)
        } else {
            (year, month + 1)
        };
        NaiveDate::from_ymd_opt(ny, nm, 1).expect("valid month") - Duration::days(1)
    })
}

/// The most recent cut date on or before `today` — the day the last statement
/// closed.
pub fn last_cut_date(today: NaiveDate, cut_day: u32) -> NaiveDate {
    let this_month = cut_in_month(today.year(), today.month(), cut_day);
    if this_month <= today {
        this_month
    } else {
        let (py, pm) = if today.month() == 1 {
            (today.year() - 1, 12)
        } else {
            (today.year(), today.month() - 1)
        };
        cut_in_month(py, pm, cut_day)
    }
}

/// The next cut date strictly after `today` — the day the current cycle closes.
pub fn next_cut_date(today: NaiveDate, cut_day: u32) -> NaiveDate {
    let this_month = cut_in_month(today.year(), today.month(), cut_day);
    if this_month > today {
        this_month
    } else {
        let (ny, nm) = if today.month() == 12 {
            (today.year() + 1, 1)
        } else {
            (today.year(), today.month() + 1)
        };
        cut_in_month(ny, nm, cut_day)
    }
}

/// Payment due date for a statement that closed on `cut`: the last day to pay
/// the statement balance without generating interest.
pub fn due_date(cut: NaiveDate, due_days: i64) -> NaiveDate {
    cut + Duration::days(due_days.max(0))
}

/// Date of the `n`-th (1-based) MSI installment: the `n`-th cut date strictly
/// after the purchase, which is when the bank bills each monthly charge.
pub fn msi_installment_date(purchased_at: NaiveDate, cut_day: u32, n: u32) -> NaiveDate {
    let mut date = purchased_at;
    for _ in 0..n.max(1) {
        date = next_cut_date(date, cut_day);
    }
    date
}

/// Cents billed on the `n`-th (1-based) of `months` installments. Plain
/// integer split; the remainder rides on the first installment so the sum is
/// exactly `total_cents`.
pub fn msi_installment_cents(total_cents: i64, months: i64, n: i64) -> i64 {
    if months <= 0 || n < 1 || n > months {
        return 0;
    }
    let base = total_cents / months;
    if n == 1 {
        base + total_cents % months
    } else {
        base
    }
}

/// How many installments of an MSI plan have been billed on or before `today`.
pub fn msi_installments_due(
    purchased_at: NaiveDate,
    cut_day: u32,
    months: u32,
    today: NaiveDate,
) -> u32 {
    let mut due = 0;
    let mut date = purchased_at;
    while due < months {
        date = next_cut_date(date, cut_day);
        if date > today {
            break;
        }
        due += 1;
    }
    due
}

/// Next occurrence of an `MM-DD` anniversary (annual fee) strictly after
/// `today`. Feb 29 falls back to Feb 28 in non-leap years. `None` when the
/// stored string is malformed.
pub fn next_anniversary(today: NaiveDate, month_day: &str) -> Option<NaiveDate> {
    let (m, d) = month_day.split_once('-')?;
    let month: u32 = m.parse().ok()?;
    let day: u32 = d.parse().ok()?;
    if !(1..=12).contains(&month) || !(1..=31).contains(&day) {
        return None;
    }
    let in_year = |year: i32| {
        NaiveDate::from_ymd_opt(year, month, day)
            .or_else(|| NaiveDate::from_ymd_opt(year, month, 28))
    };
    let this_year = in_year(today.year())?;
    if this_year > today {
        Some(this_year)
    } else {
        in_year(today.year() + 1)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    #[test]
    fn cut_dates_around_a_mid_month_cut() {
        // Cut day 15: on June 20 the last statement closed June 15 and the
        // current cycle closes July 15. On the cut day itself the statement
        // closes that day.
        assert_eq!(last_cut_date(d("2026-06-20"), 15), d("2026-06-15"));
        assert_eq!(next_cut_date(d("2026-06-20"), 15), d("2026-07-15"));
        assert_eq!(last_cut_date(d("2026-06-15"), 15), d("2026-06-15"));
        assert_eq!(next_cut_date(d("2026-06-15"), 15), d("2026-07-15"));
        assert_eq!(last_cut_date(d("2026-06-10"), 15), d("2026-05-15"));
    }

    #[test]
    fn cut_dates_clamp_to_short_months() {
        // Cut day 31 slides to each month's last day.
        assert_eq!(last_cut_date(d("2026-03-15"), 31), d("2026-02-28"));
        assert_eq!(next_cut_date(d("2026-02-28"), 31), d("2026-03-31"));
        assert_eq!(next_cut_date(d("2026-04-30"), 31), d("2026-05-31"));
        // Leap year February.
        assert_eq!(last_cut_date(d("2028-03-01"), 31), d("2028-02-29"));
    }

    #[test]
    fn cut_dates_cross_year_boundary() {
        assert_eq!(last_cut_date(d("2026-01-05"), 15), d("2025-12-15"));
        assert_eq!(next_cut_date(d("2026-12-20"), 15), d("2027-01-15"));
    }

    #[test]
    fn due_date_is_cut_plus_grace() {
        // MX standard: 20 days after the cut. June 15 + 20 = July 5.
        assert_eq!(due_date(d("2026-06-15"), 20), d("2026-07-05"));
        assert_eq!(due_date(d("2026-06-15"), 0), d("2026-06-15"));
    }

    #[test]
    fn msi_schedule_bills_on_each_cut() {
        // Bought June 20, cut day 15 → billed July 15, Aug 15, Sep 15.
        assert_eq!(
            msi_installment_date(d("2026-06-20"), 15, 1),
            d("2026-07-15")
        );
        assert_eq!(
            msi_installment_date(d("2026-06-20"), 15, 2),
            d("2026-08-15")
        );
        assert_eq!(
            msi_installment_date(d("2026-06-20"), 15, 3),
            d("2026-09-15")
        );
        // Bought ON the cut day: first bill lands on the next cycle's cut.
        assert_eq!(
            msi_installment_date(d("2026-06-15"), 15, 1),
            d("2026-07-15")
        );
    }

    #[test]
    fn msi_split_puts_remainder_on_first_installment() {
        // $1,000.00 in 3 → 333.34 + 333.33 + 333.33 = exactly 1000.00.
        assert_eq!(msi_installment_cents(100_000, 3, 1), 33_334);
        assert_eq!(msi_installment_cents(100_000, 3, 2), 33_333);
        assert_eq!(msi_installment_cents(100_000, 3, 3), 33_333);
        let total: i64 = (1..=3).map(|n| msi_installment_cents(100_000, 3, n)).sum();
        assert_eq!(total, 100_000);
        // Out of range is never billed.
        assert_eq!(msi_installment_cents(100_000, 3, 4), 0);
        assert_eq!(msi_installment_cents(100_000, 0, 1), 0);
    }

    #[test]
    fn msi_installments_due_counts_billed_months() {
        // Bought June 20, cut day 15, 6 months: by Aug 20 two cuts passed.
        assert_eq!(
            msi_installments_due(d("2026-06-20"), 15, 6, d("2026-08-20")),
            2
        );
        // The day before the first cut nothing is billed yet.
        assert_eq!(
            msi_installments_due(d("2026-06-20"), 15, 6, d("2026-07-14")),
            0
        );
        // Far in the future it caps at the plan length.
        assert_eq!(
            msi_installments_due(d("2026-06-20"), 15, 6, d("2027-12-31")),
            6
        );
    }

    #[test]
    fn anniversary_finds_next_occurrence() {
        assert_eq!(
            next_anniversary(d("2026-06-20"), "09-10"),
            Some(d("2026-09-10"))
        );
        assert_eq!(
            next_anniversary(d("2026-10-01"), "09-10"),
            Some(d("2027-09-10"))
        );
        // On the day itself, the next one is a year out.
        assert_eq!(
            next_anniversary(d("2026-09-10"), "09-10"),
            Some(d("2027-09-10"))
        );
        // Feb 29 falls back to Feb 28 off-leap-years.
        assert_eq!(
            next_anniversary(d("2026-01-01"), "02-29"),
            Some(d("2026-02-28"))
        );
        assert_eq!(next_anniversary(d("2026-01-01"), "13-01"), None);
        assert_eq!(next_anniversary(d("2026-01-01"), "garbage"), None);
    }
}
