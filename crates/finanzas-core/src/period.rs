//! Date-window resolution for dashboard flow statistics. Pure logic over
//! `NaiveDate`, tested natively; the worker feeds in `today_mx()` and turns the
//! resolved window into bound SQL parameters. The frontend picks a `Period`;
//! everything that follows (start/end, the comparable previous window, and the
//! daily-vs-monthly bucketing) is decided here so it stays consistent across
//! the totals, the trend arrow and the flow chart.

use chrono::{Datelike, Months, NaiveDate};
use serde::Deserialize;

/// User-selected window. Tagged by `kind` so it round-trips as camelCase JSON,
/// e.g. `{ "kind": "lastMonths", "months": 6 }`.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", tag = "kind")]
pub enum Period {
    /// The calendar month containing `today`.
    #[default]
    CurrentMonth,
    /// The last `months` calendar months, ending with (and including) the
    /// current month.
    LastMonths { months: u32 },
    /// A specific calendar month.
    Month { year: i32, month: u32 },
    /// A single day (`YYYY-MM-DD`).
    Day { date: NaiveDate },
    /// An inclusive range `[from, to]` (`YYYY-MM-DD` each).
    Range { from: NaiveDate, to: NaiveDate },
}

/// How the flow chart groups the window into bars.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Bucket {
    Day,
    Month,
}

impl Bucket {
    /// Serialized hint sent to the frontend so it formats the X axis labels.
    pub fn as_str(self) -> &'static str {
        match self {
            Bucket::Day => "day",
            Bucket::Month => "month",
        }
    }
}

/// A resolved window with its comparable previous window. `end`/`prev_end` are
/// exclusive upper bounds, so the SQL filter is `occurred_at >= start AND
/// occurred_at < end` and the windows never overlap.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ResolvedPeriod {
    pub start: NaiveDate,
    pub end: NaiveDate,
    pub prev_start: NaiveDate,
    pub prev_end: NaiveDate,
    pub bucket: Bucket,
}

/// A range spanning at most this many days is charted by day; beyond it, by
/// month. ~2 months keeps a single specific month (28–31 days) on daily bars.
const DAILY_MAX_SPAN_DAYS: i64 = 62;

fn first_of_month(d: NaiveDate) -> NaiveDate {
    NaiveDate::from_ymd_opt(d.year(), d.month(), 1).expect("day 1 always valid")
}

/// Resolve a `Period` against the current business date. The previous window is
/// always the same shape immediately before the selected one, so the trend
/// arrow compares like with like (month vs prior month, range vs equal-length
/// range right before it, etc.).
pub fn resolve_period(period: &Period, today: NaiveDate) -> ResolvedPeriod {
    match period {
        Period::CurrentMonth => {
            let start = first_of_month(today);
            let end = start + Months::new(1);
            ResolvedPeriod {
                start,
                end,
                prev_start: start - Months::new(1),
                prev_end: start,
                bucket: Bucket::Day,
            }
        }
        Period::LastMonths { months } => {
            // At least one month; `months - 1` back from the current month.
            let span = (*months).max(1);
            let end = first_of_month(today) + Months::new(1);
            let start = end - Months::new(span);
            ResolvedPeriod {
                start,
                end,
                prev_start: start - Months::new(span),
                prev_end: start,
                bucket: Bucket::Month,
            }
        }
        Period::Month { year, month } => {
            let start = NaiveDate::from_ymd_opt(*year, (*month).clamp(1, 12), 1)
                .unwrap_or_else(|| first_of_month(today));
            let end = start + Months::new(1);
            ResolvedPeriod {
                start,
                end,
                prev_start: start - Months::new(1),
                prev_end: start,
                bucket: Bucket::Day,
            }
        }
        Period::Day { date } => {
            let start = *date;
            let end = start.succ_opt().unwrap_or(start);
            let prev_start = start.pred_opt().unwrap_or(start);
            ResolvedPeriod {
                start,
                end,
                prev_start,
                prev_end: start,
                bucket: Bucket::Day,
            }
        }
        Period::Range { from, to } => {
            // Normalize so `from <= to`; `to` is inclusive, so `end = to + 1`.
            let (lo, hi) = if from <= to {
                (*from, *to)
            } else {
                (*to, *from)
            };
            let end = hi.succ_opt().unwrap_or(hi);
            let span_days = (end - lo).num_days().max(1);
            let bucket = if span_days <= DAILY_MAX_SPAN_DAYS {
                Bucket::Day
            } else {
                Bucket::Month
            };
            ResolvedPeriod {
                start: lo,
                end,
                prev_start: lo - chrono::Duration::days(span_days),
                prev_end: lo,
                bucket,
            }
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
    fn current_month_spans_the_calendar_month_with_daily_bars() {
        let r = resolve_period(&Period::CurrentMonth, d(2026, 6, 19));
        assert_eq!(r.start, d(2026, 6, 1));
        assert_eq!(r.end, d(2026, 7, 1));
        assert_eq!(r.prev_start, d(2026, 5, 1));
        assert_eq!(r.prev_end, d(2026, 6, 1));
        assert_eq!(r.bucket, Bucket::Day);
    }

    #[test]
    fn last_months_includes_current_month_and_compares_with_prior_block() {
        // Last 6 months from June 2026 → Jan..Jun 2026; prev block Jul..Dec 2025.
        let r = resolve_period(&Period::LastMonths { months: 6 }, d(2026, 6, 19));
        assert_eq!(r.start, d(2026, 1, 1));
        assert_eq!(r.end, d(2026, 7, 1));
        assert_eq!(r.prev_start, d(2025, 7, 1));
        assert_eq!(r.prev_end, d(2026, 1, 1));
        assert_eq!(r.bucket, Bucket::Month);
    }

    #[test]
    fn last_months_zero_is_clamped_to_one_month() {
        let r = resolve_period(&Period::LastMonths { months: 0 }, d(2026, 6, 19));
        assert_eq!(r.start, d(2026, 6, 1));
        assert_eq!(r.end, d(2026, 7, 1));
    }

    #[test]
    fn specific_month_uses_daily_bars_and_prior_month_baseline() {
        let r = resolve_period(
            &Period::Month {
                year: 2026,
                month: 2,
            },
            d(2026, 6, 19),
        );
        assert_eq!(r.start, d(2026, 2, 1));
        assert_eq!(r.end, d(2026, 3, 1));
        assert_eq!(r.prev_start, d(2026, 1, 1));
        assert_eq!(r.prev_end, d(2026, 2, 1));
        assert_eq!(r.bucket, Bucket::Day);
    }

    #[test]
    fn specific_day_compares_with_the_day_before() {
        let r = resolve_period(
            &Period::Day {
                date: d(2026, 3, 10),
            },
            d(2026, 6, 19),
        );
        assert_eq!(r.start, d(2026, 3, 10));
        assert_eq!(r.end, d(2026, 3, 11));
        assert_eq!(r.prev_start, d(2026, 3, 9));
        assert_eq!(r.prev_end, d(2026, 3, 10));
        assert_eq!(r.bucket, Bucket::Day);
    }

    #[test]
    fn short_range_is_daily_and_compares_with_equal_length_window() {
        // Mar 1..Mar 15 inclusive = 15 days → daily; prev = Feb 14..Mar 1.
        let r = resolve_period(
            &Period::Range {
                from: d(2026, 3, 1),
                to: d(2026, 3, 15),
            },
            d(2026, 6, 19),
        );
        assert_eq!(r.start, d(2026, 3, 1));
        assert_eq!(r.end, d(2026, 3, 16));
        assert_eq!(r.prev_end, d(2026, 3, 1));
        assert_eq!(r.prev_start, d(2026, 2, 14));
        assert_eq!(r.bucket, Bucket::Day);
    }

    #[test]
    fn long_range_switches_to_monthly_bars() {
        // Jan 1..Apr 30 = 120 days > 62 → monthly.
        let r = resolve_period(
            &Period::Range {
                from: d(2026, 1, 1),
                to: d(2026, 4, 30),
            },
            d(2026, 6, 19),
        );
        assert_eq!(r.bucket, Bucket::Month);
    }

    #[test]
    fn range_normalizes_reversed_bounds() {
        let r = resolve_period(
            &Period::Range {
                from: d(2026, 3, 15),
                to: d(2026, 3, 1),
            },
            d(2026, 6, 19),
        );
        assert_eq!(r.start, d(2026, 3, 1));
        assert_eq!(r.end, d(2026, 3, 16));
    }
}
