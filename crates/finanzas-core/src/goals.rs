//! Savings-goal contribution planning. Given a target amount, a deadline and how
//! often you plan to put money in, work out how much to set aside each period to
//! arrive on time — and whether you've fallen behind the steady pace measured
//! from the day the goal started. Pure logic over `NaiveDate`, tested natively;
//! the worker feeds in `today_mx()` and the goal's stored dates. No money is
//! stored from this; it's recomputed on every read.
//!
//! The inverse question is answered too: given a FIXED amount per period,
//! `plan_fixed_contribution` projects the date the target would be reached.

use chrono::{Datelike, Duration, Months, NaiveDate};
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
    /// This period's quota, frozen at what the plan asked when the period
    /// started — contributing during the period doesn't shrink it, so partial
    /// progress reads as "you put 2,000 of 2,400" instead of a new plan.
    pub period_quota_cents: i64,
    /// What's still missing to cover this period's quota; 0 once covered.
    pub period_missing_cents: i64,
    /// Net amount contributed within the current cadence period (floored at 0).
    pub contributed_this_period_cents: i64,
    /// Whole days from today to the deadline; negative once it has passed.
    pub days_left: i64,
    /// True once the deadline has passed with money still owed.
    pub overdue: bool,
    /// How far below the steady pace (the target spread evenly over the whole
    /// span from start to deadline) the saved amount is right now. 0 when on or
    /// ahead of pace. Drives the "behind" badge.
    pub behind_cents: i64,
    /// Fixed-contribution plans only: the day the target would be reached if
    /// the user keeps putting in the same amount every period. None for
    /// deadline plans (the deadline is the date).
    pub projected_date: Option<NaiveDate>,
}

/// First day of the cadence period `today` falls in (weeks start on Monday).
pub fn period_start(cadence: Cadence, today: NaiveDate) -> NaiveDate {
    match cadence {
        Cadence::Daily => today,
        Cadence::Weekly => today - Duration::days(today.weekday().num_days_from_monday() as i64),
        Cadence::Monthly => today.with_day(1).expect("day 1 always valid"),
        Cadence::Yearly => today.with_ordinal(1).expect("ordinal 1 always valid"),
    }
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
/// - `start`: the day the plan began (when the deadline was set), the pace anchor.
/// - `deadline`: the day the target should be reached by.
/// - `today`: the current business day.
/// - `target_cents` / `saved_cents`: the goal's objective and current progress.
/// - `contributed_this_period_cents`: net amount put in during the current
///   cadence period; it freezes this period's quota so partial contributions
///   report "you're 400 short" instead of re-spreading the whole plan.
pub fn plan_contribution(
    start: NaiveDate,
    deadline: NaiveDate,
    today: NaiveDate,
    cadence: Cadence,
    target_cents: i64,
    saved_cents: i64,
    contributed_this_period_cents: i64,
) -> ContributionPlan {
    let remaining = (target_cents - saved_cents).max(0);
    let days_left = (deadline - today).num_days();
    let overdue = remaining > 0 && today > deadline;
    let contributed = contributed_this_period_cents.max(0);

    let (periods_left, per_period_cents) = if remaining == 0 {
        (0, 0)
    } else if overdue {
        // No future periods to spread it over — it's all due now.
        (0, remaining)
    } else {
        let periods = periods_between(today, deadline, cadence).max(1);
        (periods, div_ceil(remaining, periods))
    };

    // The quota spreads what was remaining when the period STARTED (current
    // remaining plus what already came in this period) over the same period
    // count, so it holds steady while the period's money arrives.
    let (period_quota_cents, period_missing_cents) = if remaining == 0 {
        (0, 0)
    } else if overdue {
        (remaining, remaining)
    } else {
        let periods = periods_between(today, deadline, cadence).max(1);
        let quota = div_ceil(remaining + contributed, periods);
        (quota, (quota - contributed).max(0))
    };

    ContributionPlan {
        periods_left,
        per_period_cents,
        period_quota_cents,
        period_missing_cents,
        contributed_this_period_cents: contributed,
        days_left,
        overdue,
        behind_cents: behind_cents(start, deadline, today, target_cents, saved_cents),
        projected_date: None,
    }
}

/// `n` cadence periods after `date` (month/year steps clamp to the month's
/// last day, e.g. Jan 31 + 1 month = Feb 28).
fn add_periods(date: NaiveDate, cadence: Cadence, n: i64) -> NaiveDate {
    let n = n.max(0);
    match cadence {
        Cadence::Daily => date + Duration::days(n),
        Cadence::Weekly => date + Duration::days(n * 7),
        Cadence::Monthly => date
            .checked_add_months(Months::new(n as u32))
            .unwrap_or(NaiveDate::MAX),
        Cadence::Yearly => date
            .checked_add_months(Months::new((n * 12) as u32))
            .unwrap_or(NaiveDate::MAX),
    }
}

/// Plan for a goal with a FIXED contribution per period instead of a
/// deadline: project when the target is reached.
///
/// This period still expects `contribution_cents` minus what already came in
/// (`contributed_this_period_cents`); every later period brings the full
/// amount. The goal completes in the period whose contribution covers the
/// remainder: today if this period's pending money covers it, otherwise that
/// many periods after today. Never behind or overdue — there's no deadline.
pub fn plan_fixed_contribution(
    today: NaiveDate,
    cadence: Cadence,
    target_cents: i64,
    saved_cents: i64,
    contribution_cents: i64,
    contributed_this_period_cents: i64,
) -> ContributionPlan {
    let remaining = (target_cents - saved_cents).max(0);
    let contributed = contributed_this_period_cents.max(0);
    let amount = contribution_cents.max(1);
    if remaining == 0 {
        return ContributionPlan {
            periods_left: 0,
            per_period_cents: 0,
            period_quota_cents: 0,
            period_missing_cents: 0,
            contributed_this_period_cents: contributed,
            days_left: 0,
            overdue: false,
            behind_cents: 0,
            projected_date: Some(today),
        };
    }
    // This period's quota: the fixed amount, but never more than what was left
    // when the period started (the last contribution can be smaller).
    let quota = amount.min(remaining + contributed);
    let missing_now = (quota - contributed).max(0);
    // Periods after this one needed to cover what this period won't.
    let later = div_ceil((remaining - missing_now).max(0), amount);
    let projected = add_periods(today, cadence, later);
    ContributionPlan {
        periods_left: later + i64::from(missing_now > 0),
        per_period_cents: amount,
        period_quota_cents: quota,
        period_missing_cents: missing_now,
        contributed_this_period_cents: contributed,
        days_left: (projected - today).num_days(),
        overdue: false,
        behind_cents: 0,
        projected_date: Some(projected),
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
            0,
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
            0,
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
            0,
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
            0,
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
            0,
        );
        assert_eq!(p.periods_left, 1);
        assert_eq!(p.per_period_cents, 40_000);
    }

    #[test]
    fn partial_contribution_keeps_the_period_quota() {
        // $12,000 by Dec 31, monthly, plan set Jul 3 → 5 periods, $2,400/mo.
        // Contributing $2,000 mid-month must NOT re-spread the plan: the month's
        // quota stays $2,400 and $400 is still missing.
        let p = plan_contribution(
            d("2026-07-03"),
            d("2026-12-31"),
            d("2026-07-03"),
            Cadence::Monthly,
            1_200_000,
            200_000, // saved after the $2,000 contribution
            200_000, // contributed within July
        );
        // quota = ceil((1,000,000 + 200,000) / 5) = 240,000
        assert_eq!(p.period_quota_cents, 240_000);
        assert_eq!(p.period_missing_cents, 40_000);
        assert_eq!(p.contributed_this_period_cents, 200_000);
        // The forward-looking split still relaxes for the remaining months.
        assert_eq!(p.per_period_cents, 200_000);
    }

    #[test]
    fn covered_period_reports_nothing_missing() {
        // Same plan, the full $2,400 already in this month.
        let p = plan_contribution(
            d("2026-07-03"),
            d("2026-12-31"),
            d("2026-07-03"),
            Cadence::Monthly,
            1_200_000,
            240_000,
            240_000,
        );
        assert_eq!(p.period_quota_cents, 240_000);
        assert_eq!(p.period_missing_cents, 0);
    }

    #[test]
    fn untouched_period_quota_equals_the_per_period_split() {
        // With nothing contributed yet this period both numbers agree.
        let p = plan_contribution(
            d("2026-07-03"),
            d("2026-12-31"),
            d("2026-07-03"),
            Cadence::Monthly,
            1_200_000,
            0,
            0,
        );
        assert_eq!(p.per_period_cents, 240_000);
        assert_eq!(p.period_quota_cents, 240_000);
        assert_eq!(p.period_missing_cents, 240_000);
    }

    #[test]
    fn net_releases_never_deflate_the_quota() {
        // Releasing more than was put in this period floors the net at zero, so
        // the quota falls back to the plain forward split.
        let p = plan_contribution(
            d("2026-07-03"),
            d("2026-12-31"),
            d("2026-07-03"),
            Cadence::Monthly,
            1_200_000,
            0,
            -100_000,
        );
        assert_eq!(p.period_quota_cents, 240_000);
        assert_eq!(p.period_missing_cents, 240_000);
        assert_eq!(p.contributed_this_period_cents, 0);
    }

    #[test]
    fn period_start_per_cadence() {
        // 2026-07-02 is a Thursday; its ISO week starts Monday 2026-06-29.
        assert_eq!(
            period_start(Cadence::Daily, d("2026-07-02")),
            d("2026-07-02")
        );
        assert_eq!(
            period_start(Cadence::Weekly, d("2026-07-02")),
            d("2026-06-29")
        );
        assert_eq!(
            period_start(Cadence::Weekly, d("2026-06-29")),
            d("2026-06-29")
        );
        assert_eq!(
            period_start(Cadence::Monthly, d("2026-07-31")),
            d("2026-07-01")
        );
        assert_eq!(
            period_start(Cadence::Yearly, d("2026-07-02")),
            d("2026-01-01")
        );
    }

    #[test]
    fn fixed_monthly_projects_the_completion_date() {
        // Target $36,000, saved $13,968, $1,500/month, nothing in this month.
        // Remaining 2,203,200: this month 150,000 → 2,053,200 left →
        // ceil(2,053,200 / 150,000) = 14 more months → 2026-09-28 + 14 = 2027-11-28.
        let p = plan_fixed_contribution(
            d("2026-09-28"),
            Cadence::Monthly,
            3_600_000,
            1_396_800,
            150_000,
            0,
        );
        assert_eq!(p.projected_date, Some(d("2027-11-28")));
        assert_eq!(p.periods_left, 15);
        assert_eq!(p.per_period_cents, 150_000);
        assert_eq!(p.period_quota_cents, 150_000);
        assert_eq!(p.period_missing_cents, 150_000);
        assert_eq!(p.days_left, 426);
        assert!(!p.overdue);
        assert_eq!(p.behind_cents, 0);
    }

    #[test]
    fn fixed_counts_what_already_came_in_this_period() {
        // $100.00 left, $30/week, $20 already in this week → $10 pending now,
        // then 9,000 left → ceil(9,000 / 3,000) = 3 more weeks.
        let p = plan_fixed_contribution(
            d("2026-06-03"),
            Cadence::Weekly,
            20_000,
            10_000,
            3_000,
            2_000,
        );
        assert_eq!(p.period_quota_cents, 3_000);
        assert_eq!(p.period_missing_cents, 1_000);
        assert_eq!(p.periods_left, 4);
        assert_eq!(p.projected_date, Some(d("2026-06-24")));
    }

    #[test]
    fn fixed_finishes_this_period_when_pending_covers_it() {
        // $50 left and a $100 monthly contribution: the quota shrinks to the
        // remainder and it's done today.
        let p = plan_fixed_contribution(
            d("2026-06-15"),
            Cadence::Monthly,
            100_000,
            95_000,
            10_000,
            0,
        );
        assert_eq!(p.period_quota_cents, 5_000);
        assert_eq!(p.period_missing_cents, 5_000);
        assert_eq!(p.periods_left, 1);
        assert_eq!(p.projected_date, Some(d("2026-06-15")));
        assert_eq!(p.days_left, 0);
    }

    #[test]
    fn fixed_covered_period_projects_from_next_one() {
        // $1,000/month already put in this month; 5,000 left → 5 more months.
        let p = plan_fixed_contribution(
            d("2026-01-31"),
            Cadence::Monthly,
            1_000_000,
            500_000,
            100_000,
            100_000,
        );
        assert_eq!(p.period_missing_cents, 0);
        assert_eq!(p.periods_left, 5);
        // Month steps clamp to month end: Jan 31 + 5 months = Jun 30.
        assert_eq!(p.projected_date, Some(d("2026-06-30")));
    }

    #[test]
    fn fixed_met_goal_is_done_today() {
        let p = plan_fixed_contribution(d("2026-06-15"), Cadence::Yearly, 10_000, 12_000, 1_000, 0);
        assert_eq!(p.periods_left, 0);
        assert_eq!(p.period_missing_cents, 0);
        assert_eq!(p.projected_date, Some(d("2026-06-15")));
    }
}
