//! Notification scheduling: pure date logic for user-configured reminders
//! ("remind me to contribute every X" / "summarize my returns every X") and
//! stable period keys for de-duplicating recurring alerts. The worker's daily
//! cron feeds `today_mx()` and the reminder's stored dates; no money here.

use chrono::{Datelike, Duration, Months, NaiveDate};

use crate::goals::Cadence as GoalCadence;

/// The cadences a per-investment reminder can use.
pub const REMINDER_CADENCES: &[&str] = &["daily", "weekly", "biweekly", "monthly"];

/// How often a reminder recurs.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReminderCadence {
    Daily,
    Weekly,
    Biweekly,
    Monthly,
}

impl ReminderCadence {
    /// Parse the value stored in `investment_reminders.cadence`.
    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "daily" => Some(ReminderCadence::Daily),
            "weekly" => Some(ReminderCadence::Weekly),
            "biweekly" => Some(ReminderCadence::Biweekly),
            "monthly" => Some(ReminderCadence::Monthly),
            _ => None,
        }
    }

    /// The k-th occurrence after `anchor` (k ≥ 1). Monthly counts whole months
    /// from the anchor itself so a month-end anchor clamps per month instead of
    /// drifting (Jan 31 → Feb 28 → Mar 31, not Mar 28).
    fn occurrence(self, anchor: NaiveDate, k: u32) -> Option<NaiveDate> {
        match self {
            ReminderCadence::Daily => Some(anchor + Duration::days(k as i64)),
            ReminderCadence::Weekly => Some(anchor + Duration::days(7 * k as i64)),
            ReminderCadence::Biweekly => Some(anchor + Duration::days(14 * k as i64)),
            ReminderCadence::Monthly => anchor.checked_add_months(Months::new(k)),
        }
    }
}

/// The reminder occurrence that is due as of `today`, if any.
///
/// Occurrences run at `anchor + k` periods for k ≥ 1 — setting a reminder up
/// never fires on the spot, and nothing before the anchor ever fires. A long
/// gap (app not opened, reminder just enabled on an old anchor) collapses to
/// ONE occurrence — the most recent due one — never a backlog flood. Returns
/// `None` when the latest due occurrence was already fired (`last_fired`), so
/// cron re-runs are no-ops.
pub fn due_occurrence(
    cadence: ReminderCadence,
    anchor: NaiveDate,
    last_fired: Option<NaiveDate>,
    today: NaiveDate,
) -> Option<NaiveDate> {
    let mut latest_due = None;
    for k in 1.. {
        match cadence.occurrence(anchor, k) {
            Some(date) if date <= today => latest_due = Some(date),
            _ => break,
        }
    }
    match (latest_due, last_fired) {
        (Some(due), Some(fired)) if fired >= due => None,
        (due, _) => due,
    }
}

/// Stable key for the period `date` falls in, used in notification dedupe keys
/// so one alert per goal-contribution period exists no matter how many times
/// the cron runs within it. Weekly periods key on their ISO Monday.
pub fn period_key(cadence: GoalCadence, date: NaiveDate) -> String {
    match cadence {
        GoalCadence::Daily => date.format("%Y-%m-%d").to_string(),
        GoalCadence::Weekly => {
            let monday = date - Duration::days(date.weekday().num_days_from_monday() as i64);
            monday.format("%Y-%m-%d").to_string()
        }
        GoalCadence::Monthly => date.format("%Y-%m").to_string(),
        GoalCadence::Yearly => date.format("%Y").to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    #[test]
    fn parse_accepts_known_cadences_only() {
        assert_eq!(
            ReminderCadence::parse("daily"),
            Some(ReminderCadence::Daily)
        );
        assert_eq!(
            ReminderCadence::parse("biweekly"),
            Some(ReminderCadence::Biweekly)
        );
        assert_eq!(ReminderCadence::parse("yearly"), None);
        assert_eq!(ReminderCadence::parse(""), None);
    }

    #[test]
    fn nothing_fires_on_or_before_the_anchor() {
        // Set up today → first occurrence is tomorrow, so nothing is due yet.
        assert_eq!(
            due_occurrence(
                ReminderCadence::Daily,
                d("2026-06-10"),
                None,
                d("2026-06-10")
            ),
            None
        );
        // Today before the first weekly occurrence.
        assert_eq!(
            due_occurrence(
                ReminderCadence::Weekly,
                d("2026-06-10"),
                None,
                d("2026-06-16")
            ),
            None
        );
    }

    #[test]
    fn fires_the_exact_occurrence_when_due() {
        assert_eq!(
            due_occurrence(
                ReminderCadence::Weekly,
                d("2026-06-10"),
                None,
                d("2026-06-17")
            ),
            Some(d("2026-06-17"))
        );
        assert_eq!(
            due_occurrence(
                ReminderCadence::Biweekly,
                d("2026-06-01"),
                None,
                d("2026-06-15")
            ),
            Some(d("2026-06-15"))
        );
    }

    #[test]
    fn long_gap_collapses_to_one_occurrence() {
        // Anchor Jan 1, never fired, today Jan 29: occurrences 8/15/22/29 →
        // only the most recent (Jan 29) is due; no backlog of four alerts.
        assert_eq!(
            due_occurrence(
                ReminderCadence::Weekly,
                d("2026-01-01"),
                None,
                d("2026-01-29")
            ),
            Some(d("2026-01-29"))
        );
    }

    #[test]
    fn rerun_after_firing_is_a_noop() {
        assert_eq!(
            due_occurrence(
                ReminderCadence::Weekly,
                d("2026-01-01"),
                Some(d("2026-01-29")),
                d("2026-01-29"),
            ),
            None
        );
        // Fired an older occurrence, a newer one is due → fires the newer one.
        assert_eq!(
            due_occurrence(
                ReminderCadence::Daily,
                d("2026-06-10"),
                Some(d("2026-06-14")),
                d("2026-06-15"),
            ),
            Some(d("2026-06-15"))
        );
    }

    #[test]
    fn monthly_clamps_month_end_without_drifting() {
        // Anchored Jan 31: Feb occurrence clamps to the 28th…
        assert_eq!(
            due_occurrence(
                ReminderCadence::Monthly,
                d("2026-01-31"),
                None,
                d("2026-02-28")
            ),
            Some(d("2026-02-28"))
        );
        // …but March counts from the anchor, so it lands on the 31st again.
        assert_eq!(
            due_occurrence(
                ReminderCadence::Monthly,
                d("2026-01-31"),
                Some(d("2026-02-28")),
                d("2026-03-31"),
            ),
            Some(d("2026-03-31"))
        );
    }

    #[test]
    fn period_keys_are_stable_within_a_period() {
        // 2026-07-02 is a Thursday; its ISO week starts Monday 2026-06-29.
        assert_eq!(
            period_key(GoalCadence::Weekly, d("2026-07-02")),
            "2026-06-29"
        );
        assert_eq!(
            period_key(GoalCadence::Weekly, d("2026-06-29")),
            "2026-06-29"
        );
        assert_eq!(
            period_key(GoalCadence::Daily, d("2026-07-02")),
            "2026-07-02"
        );
        assert_eq!(period_key(GoalCadence::Monthly, d("2026-07-02")), "2026-07");
        assert_eq!(period_key(GoalCadence::Yearly, d("2026-07-02")), "2026");
    }
}
