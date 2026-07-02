//! Savings-goal contribution planning. Given a target amount, a deadline and how
//! often you plan to put money in, work out how much to set aside each period to
//! arrive on time — and whether you've fallen behind the steady pace measured
//! from the day the goal started. Pure logic over `NaiveDate`, tested natively;
//! the worker feeds in `today_mx()` and the goal's stored dates. No money is
//! stored from this; it's recomputed on every read.

use chrono::{Datelike, NaiveDate};
use serde::Serialize;

/// How often the user plans to contribute. Drives how the remaining amount is
/// split into per-period suggestions.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Cadence {
    Daily,
    Weekly,
    Monthly,
    Yearly,
}

impl Cadence {
    /// Parse the value stored in `savings_goals.contribution_cadence`.
    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "daily" => Some(Cadence::Daily),
            "weekly" => Some(Cadence::Weekly),
            "monthly" => Some(Cadence::Monthly),
            "yearly" => Some(Cadence::Yearly),
            _ => None,
        }
    }
}

/// The plan for a goal with a deadline. All amounts in cents.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ContributionPlan {
    /// Cadence periods left until the deadline; at least 1 while money is still
    /// owed and the deadline hasn't passed. 0 once the goal is met or overdue.
    pub periods_left: i64,
    /// Suggested amount to set aside each period to reach the target on time.
    /// When overdue, this is everything still owed (you need it all now).
    pub per_period_cents: i64,
    /// Whole days from today to the deadline; negative once it has passed.
    pub days_left: i64,
    /// True once the deadline has passed with money still owed.
    pub overdue: bool,
    /// How far below the steady pace (the target spread evenly over the whole
    /// span from start to deadline) the saved amount is right now. 0 when on or
    /// ahead of pace. Drives the "behind" badge.
    pub behind_cents: i64,
}

/// Number of cadence periods from `today` to `deadline`, before clamping. May be
/// 0 or negative when the deadline is today or in the past.
fn periods_between(today: NaiveDate, deadline: NaiveDate, cadence: Cadence) -> i64 {
    let days = (deadline - today).num_days();
    match cadence {
        Cadence::Daily => days,
        // Round up so a partial week still counts as one contribution slot.
        Cadence::Weekly => (days + 6).div_euclid(7),
        Cadence::Monthly => months_between(today, deadline),
        Cadence::Yearly => deadline.year() as i64 - today.year() as i64,
    }
}

/// Whole calendar months from `a` to `b` (e.g. Jun→Nov = 5), ignoring the day.
fn months_between(a: NaiveDate, b: NaiveDate) -> i64 {
    (b.year() as i64 - a.year() as i64) * 12 + (b.month() as i64 - a.month() as i64)
}

/// Divide `n` by `d` rounding up, for non-negative `n` and positive `d`.
fn div_ceil(n: i64, d: i64) -> i64 {
    (n + d - 1) / d
}

/// Work out the contribution plan for a goal.
///
/// - `start`: the day the goal began (its `created_at`), used as the pace anchor.
/// - `deadline`: the day the target should be reached by.
/// - `today`: the current business day.
/// - `target_cents` / `saved_cents`: the goal's objective and current progress.
pub fn plan_contribution(
    start: NaiveDate,
    deadline: NaiveDate,
    today: NaiveDate,
    cadence: Cadence,
    target_cents: i64,
    saved_cents: i64,
) -> ContributionPlan {
    let remaining = (target_cents - saved_cents).max(0);
    let days_left = (deadline - today).num_days();
    let overdue = remaining > 0 && today > deadline;

    let (periods_left, per_period_cents) = if remaining == 0 {
        (0, 0)
    } else if overdue {
        // No future periods to spread it over — it's all due now.
        (0, remaining)
    } else {
        let periods = periods_between(today, deadline, cadence).max(1);
        (periods, div_ceil(remaining, periods))
    };

    ContributionPlan {
        periods_left,
        per_period_cents,
        days_left,
        overdue,
        behind_cents: behind_cents(start, deadline, today, target_cents, saved_cents),
    }
}

/// How far below the steady pace the goal is. The steady pace spreads the whole
/// target evenly across the span from `start` to `deadline`; we compare today's
/// expected amount against what's actually saved. i128 intermediate so a large
/// target times the elapsed days can't overflow.
fn behind_cents(
    start: NaiveDate,
    deadline: NaiveDate,
    today: NaiveDate,
    target_cents: i64,
    saved_cents: i64,
) -> i64 {
    let span = (deadline - start).num_days();
    if span <= 0 {
        // Degenerate span: everything is due, so anything unsaved is "behind".
        return (target_cents - saved_cents).max(0);
    }
    let elapsed = (today - start).num_days().clamp(0, span);
    let expected = ((target_cents as i128) * elapsed as i128 / span as i128) as i64;
    (expected - saved_cents).max(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    #[test]
    fn monthly_on_pace_suggests_even_split_and_no_behind() {
        // Goal of $1,200.00 over the year, halfway saved at mid-year.
        let p = plan_contribution(
            d("2026-01-01"),
            d("2026-12-31"),
            d("2026-07-01"),
            Cadence::Monthly,
            120_000,
            60_000,
        );
        // Jul→Dec = 5 months. Remaining 60,000 / 5 = 12,000 per month.
        assert_eq!(p.periods_left, 5);
        assert_eq!(p.per_period_cents, 12_000);
        assert!(!p.overdue);
        // Expected by Jul 1: 120,000 * 181/364 = 59,670 < saved 60,000 → on pace.
        assert_eq!(p.behind_cents, 0);
    }

    #[test]
    fn monthly_behind_pace_reports_gap_and_higher_split() {
        let p = plan_contribution(
            d("2026-01-01"),
            d("2026-12-31"),
            d("2026-07-01"),
            Cadence::Monthly,
            120_000,
            40_000,
        );
        // Remaining 80,000 / 5 = 16,000 per month (higher than the original pace).
        assert_eq!(p.per_period_cents, 16_000);
        // Expected 59,670 - saved 40,000 = 19,670 behind.
        assert_eq!(p.behind_cents, 19_670);
    }

    #[test]
    fn daily_rounds_the_per_period_up() {
        // $100.00 over 3 days, nothing saved → 10,000 / 3 = 3,334 (rounded up).
        let p = plan_contribution(
            d("2026-06-28"),
            d("2026-07-01"),
            d("2026-06-28"),
            Cadence::Daily,
            10_000,
            0,
        );
        assert_eq!(p.periods_left, 3);
        assert_eq!(p.per_period_cents, 3_334);
    }

    #[test]
    fn weekly_rounds_partial_week_up() {
        // 10 days left → 2 weekly slots.
        let p = plan_contribution(
            d("2026-06-01"),
            d("2026-06-11"),
            d("2026-06-01"),
            Cadence::Weekly,
            20_000,
            0,
        );
        assert_eq!(p.periods_left, 2);
        assert_eq!(p.per_period_cents, 10_000);
    }

    #[test]
    fn overdue_owes_everything_now() {
        let p = plan_contribution(
            d("2026-01-01"),
            d("2026-06-01"),
            d("2026-06-28"),
            Cadence::Monthly,
            100_000,
            30_000,
        );
        assert!(p.overdue);
        assert_eq!(p.periods_left, 0);
        assert_eq!(p.per_period_cents, 70_000); // all the remaining
        assert!(p.days_left < 0);
        // Past the deadline with money owed → fully behind.
        assert_eq!(p.behind_cents, 70_000);
    }

    #[test]
    fn met_goal_has_empty_plan() {
        let p = plan_contribution(
            d("2026-01-01"),
            d("2026-12-31"),
            d("2026-07-01"),
            Cadence::Monthly,
            100_000,
            100_000,
        );
        assert_eq!(p.periods_left, 0);
        assert_eq!(p.per_period_cents, 0);
        assert_eq!(p.behind_cents, 0);
        assert!(!p.overdue);
    }

    #[test]
    fn last_period_floors_at_one() {
        // Deadline this month, monthly cadence → still at least one slot.
        let p = plan_contribution(
            d("2026-06-01"),
            d("2026-06-20"),
            d("2026-06-10"),
            Cadence::Monthly,
            50_000,
            10_000,
        );
        assert_eq!(p.periods_left, 1);
        assert_eq!(p.per_period_cents, 40_000);
    }
}
