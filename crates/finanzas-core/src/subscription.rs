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

/// `client_id` that tags a subscription charge with the billing period it
/// pays: 'sub:<subscription id>:<period date>'. The cron has always used it
/// (it doubles as its idempotency key); manual payments use it too, so a
/// charge belongs to its period no matter which day it was booked.
pub fn charge_client_id(subscription_id: i64, period: NaiveDate) -> String {
    format!("sub:{subscription_id}:{period}")
}

/// The period a charge's `client_id` tags for this subscription, if any —
/// the inverse of [`charge_client_id`]. Older manual charges carry no tag.
pub fn charge_period(client_id: Option<&str>, subscription_id: i64) -> Option<NaiveDate> {
    let rest = client_id?.strip_prefix(&format!("sub:{subscription_id}:"))?;
    NaiveDate::parse_from_str(rest, "%Y-%m-%d").ok()
}

/// One expense already booked for a subscription.
#[derive(Debug, Clone, Copy)]
pub struct BookedCharge {
    pub date: NaiveDate,
    /// Period tagged in its `client_id`; None for untagged (older) charges.
    pub period: Option<NaiveDate>,
}

/// What "register payment" does for a subscription.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ManualPayment {
    /// The period before `next_charge_date` is due but has no charge (e.g. it
    /// was deleted): book it for `period` and leave the next date alone.
    FillPrevious { period: NaiveDate },
    /// Pay the upcoming period `period` (= the current next date) and move the
    /// next date to `next`.
    Advance { period: NaiveDate, next: NaiveDate },
    /// Auto-charging subscription with nothing owed: its next charge (`next`)
    /// is booked by the cron, so a manual one would only skip a period.
    AlreadyCharged { next: NaiveDate },
}

/// Decides what a manual "register payment" books. Before this, it always
/// booked today and stepped the next date — so an auto charge deleted and then
/// re-registered by hand moved the date twice in one period and silently
/// skipped the next one.
///
/// The previous period (`next_charge - cadence`) is owed when it is already due
/// (`<= today`), began after the subscription did (`> started_at`, so a fresh
/// subscription isn't billed for before it existed), and no charge covers it.
/// A charge covers it when its tag names that period, or — untagged older
/// charges — when its date falls in `[previous, next_charge)`.
pub fn plan_manual_payment(
    next_charge: NaiveDate,
    cadence_months: i32,
    started_at: Option<NaiveDate>,
    auto_charge: bool,
    charges: &[BookedCharge],
    today: NaiveDate,
) -> ManualPayment {
    let step = cadence_months.max(1);
    let previous = add_months(next_charge, -step);
    let covered = charges.iter().any(|c| match c.period {
        Some(p) => p == previous,
        None => c.date >= previous && c.date < next_charge,
    });
    let owed = previous <= today && started_at.is_none_or(|s| previous > s) && !covered;
    if owed {
        ManualPayment::FillPrevious { period: previous }
    } else if auto_charge {
        ManualPayment::AlreadyCharged { next: next_charge }
    } else {
        ManualPayment::Advance {
            period: next_charge,
            next: next_after(next_charge, step),
        }
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

    // ---- plan_manual_payment ----

    /// An untagged (older) charge booked on `date`.
    fn legacy(date: NaiveDate) -> BookedCharge {
        BookedCharge { date, period: None }
    }

    /// A charge tagged with the period it pays, booked on `date`.
    fn tagged(date: NaiveDate, period: NaiveDate) -> BookedCharge {
        BookedCharge {
            date,
            period: Some(period),
        }
    }

    /// Claude's history before Oct 17: monthly on the 17th since Jun 17.
    fn claude_history() -> Vec<BookedCharge> {
        vec![
            legacy(d(2026, 6, 16)),
            legacy(d(2026, 7, 17)),
            legacy(d(2026, 8, 17)),
            legacy(d(2026, 9, 17)),
        ]
    }

    #[test]
    fn charge_tag_round_trips() {
        assert_eq!(charge_client_id(2, d(2026, 11, 17)), "sub:2:2026-11-17");
        assert_eq!(
            charge_period(Some("sub:2:2026-11-17"), 2),
            Some(d(2026, 11, 17))
        );
        // Another subscription's tag, a different prefix, no tag at all.
        assert_eq!(charge_period(Some("sub:21:2026-11-17"), 2), None);
        assert_eq!(charge_period(Some("yield:2:2026-11-17"), 2), None);
        assert_eq!(charge_period(Some("a7624e6a-9f2a"), 2), None);
        assert_eq!(charge_period(None, 2), None);
    }

    #[test]
    fn deleted_auto_charge_is_refilled_without_moving_the_date() {
        // The Oct 17 bug: the cron booked Oct 17 and moved next to Nov 17, the
        // charge was deleted, then "register payment" ran at 16:31. Previous
        // period = Nov 17 − 1 month = Oct 17: due (≤ Oct 17), after the Jun 17
        // start, and nothing covers [Oct 17, Nov 17) → fill it, next stays Nov 17.
        assert_eq!(
            plan_manual_payment(
                d(2026, 11, 17),
                1,
                Some(d(2026, 6, 17)),
                true,
                &claude_history(),
                d(2026, 10, 17),
            ),
            ManualPayment::FillPrevious {
                period: d(2026, 10, 17)
            }
        );
    }

    #[test]
    fn auto_subscription_already_charged_is_rejected() {
        // Same, but the cron's Oct 17 charge is still there → nothing owed; the
        // Nov 17 charge belongs to the cron.
        let mut charges = claude_history();
        charges.push(tagged(d(2026, 10, 17), d(2026, 10, 17)));
        assert_eq!(
            plan_manual_payment(
                d(2026, 11, 17),
                1,
                Some(d(2026, 6, 17)),
                true,
                &charges,
                d(2026, 10, 17),
            ),
            ManualPayment::AlreadyCharged {
                next: d(2026, 11, 17)
            }
        );
    }

    #[test]
    fn manual_subscription_paid_up_advances_one_period() {
        // Manual, Oct paid on Oct 18 (untagged, inside [Oct 17, Nov 17)), paying
        // on Nov 17 → books period Nov 17, next = Nov 17 + 1 month = Dec 17.
        let mut charges = claude_history();
        charges.push(legacy(d(2026, 10, 18)));
        assert_eq!(
            plan_manual_payment(
                d(2026, 11, 17),
                1,
                Some(d(2026, 6, 17)),
                false,
                &charges,
                d(2026, 11, 17),
            ),
            ManualPayment::Advance {
                period: d(2026, 11, 17),
                next: d(2026, 12, 17)
            }
        );
    }

    #[test]
    fn tagged_early_payment_covers_its_own_period() {
        // Nov paid early on Nov 15 (tagged Nov 17), so next is Dec 17. On Dec 20
        // the previous period Nov 17 is covered by the tag even though Nov 15 is
        // outside [Nov 17, Dec 17) → advance to Jan 17, never re-fill November.
        let charges = vec![
            tagged(d(2026, 10, 17), d(2026, 10, 17)),
            tagged(d(2026, 11, 15), d(2026, 11, 17)),
        ];
        assert_eq!(
            plan_manual_payment(
                d(2026, 12, 17),
                1,
                Some(d(2026, 6, 17)),
                false,
                &charges,
                d(2026, 12, 20),
            ),
            ManualPayment::Advance {
                period: d(2026, 12, 17),
                next: d(2027, 1, 17)
            }
        );
    }

    #[test]
    fn new_subscription_is_not_billed_for_before_it_existed() {
        // Created Nov 10 with the first charge on Nov 25: previous period
        // Oct 25 is before the start → manual advances to Dec 25…
        assert_eq!(
            plan_manual_payment(
                d(2026, 11, 25),
                1,
                Some(d(2026, 11, 10)),
                false,
                &[],
                d(2026, 11, 25)
            ),
            ManualPayment::Advance {
                period: d(2026, 11, 25),
                next: d(2026, 12, 25)
            }
        );
        // …and an automatic one leaves Nov 25 to the cron.
        assert_eq!(
            plan_manual_payment(
                d(2026, 11, 25),
                1,
                Some(d(2026, 11, 10)),
                true,
                &[],
                d(2026, 11, 20)
            ),
            ManualPayment::AlreadyCharged {
                next: d(2026, 11, 25)
            }
        );
    }

    #[test]
    fn previous_period_not_due_yet_is_not_owed() {
        // Next Dec 17 on Nov 10: previous period Nov 17 hasn't arrived, so it
        // isn't owed even without a charge → manual pays Dec 17 → Jan 17 2027.
        assert_eq!(
            plan_manual_payment(
                d(2026, 12, 17),
                1,
                Some(d(2026, 6, 17)),
                false,
                &claude_history(),
                d(2026, 11, 10),
            ),
            ManualPayment::Advance {
                period: d(2026, 12, 17),
                next: d(2027, 1, 17)
            }
        );
    }

    #[test]
    fn late_by_more_than_a_period_fills_then_advances() {
        // Manual, next Nov 17, nothing for Oct, paying on Dec 20: first fill
        // the Oct 17 period (next stays Nov 17)…
        let history = claude_history();
        let first = plan_manual_payment(
            d(2026, 11, 17),
            1,
            Some(d(2026, 6, 17)),
            false,
            &history,
            d(2026, 12, 20),
        );
        assert_eq!(
            first,
            ManualPayment::FillPrevious {
                period: d(2026, 10, 17)
            }
        );
        // …then, with that charge booked on Dec 20 but tagged Oct 17, the next
        // press pays Nov 17 and steps to Dec 17.
        let mut charges = history;
        charges.push(tagged(d(2026, 12, 20), d(2026, 10, 17)));
        assert_eq!(
            plan_manual_payment(
                d(2026, 11, 17),
                1,
                Some(d(2026, 6, 17)),
                false,
                &charges,
                d(2026, 12, 20),
            ),
            ManualPayment::Advance {
                period: d(2026, 11, 17),
                next: d(2026, 12, 17)
            }
        );
    }

    #[test]
    fn yearly_missing_period_is_filled() {
        // Yearly on Mar 1 since Jan 1 2025, paid Mar 1 2025, next Mar 1 2027.
        // Previous period = Mar 1 2026 ≤ Mar 5 2026, nothing in
        // [Mar 1 2026, Mar 1 2027) → fill Mar 1 2026.
        assert_eq!(
            plan_manual_payment(
                d(2027, 3, 1),
                12,
                Some(d(2025, 1, 1)),
                false,
                &[legacy(d(2025, 3, 1))],
                d(2026, 3, 5),
            ),
            ManualPayment::FillPrevious {
                period: d(2026, 3, 1)
            }
        );
    }

    #[test]
    fn end_of_month_anchor_window() {
        // Anchored Jan 31 → next Feb 28; previous = Feb 28 − 1 month = Jan 28.
        // A Jan 31 charge lies in [Jan 28, Feb 28) → covered → manual pays
        // Feb 28 and steps to Mar 28 (one clamp, as next_after always did).
        assert_eq!(
            plan_manual_payment(
                d(2026, 2, 28),
                1,
                Some(d(2025, 12, 1)),
                false,
                &[legacy(d(2026, 1, 31))],
                d(2026, 2, 10),
            ),
            ManualPayment::Advance {
                period: d(2026, 2, 28),
                next: d(2026, 3, 28)
            }
        );
        // Without it, the Jan 28 period is owed.
        assert_eq!(
            plan_manual_payment(
                d(2026, 2, 28),
                1,
                Some(d(2025, 12, 1)),
                false,
                &[],
                d(2026, 2, 10)
            ),
            ManualPayment::FillPrevious {
                period: d(2026, 1, 28)
            }
        );
    }
}
