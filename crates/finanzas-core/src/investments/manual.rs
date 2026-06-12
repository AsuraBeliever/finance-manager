//! Manual valuation: the user records the current value as snapshots.
//! value_at returns the latest snapshot on or before `as_of`, falling back to
//! the principal when there are none. params: {}

use chrono::NaiveDate;

use super::{CalcContext, InvestmentCalculator};
use crate::error::AppResult;
use crate::models::Investment;

pub struct Manual;

impl InvestmentCalculator for Manual {
    fn id(&self) -> &'static str {
        "manual"
    }

    fn value_at(&self, inv: &Investment, ctx: &CalcContext, as_of: NaiveDate) -> AppResult<i64> {
        // snapshots are chronological; the last one on or before `as_of` wins
        let value = ctx
            .snapshots
            .iter()
            .rev()
            .find(|s| s.as_of <= as_of)
            .map(|s| s.value_cents);
        Ok(value.unwrap_or(inv.principal_cents))
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::investments::{test_investment, Snapshot};

    fn snapshots(rows: &[(i64, &str)]) -> Vec<Snapshot> {
        rows.iter()
            .map(|(value_cents, as_of)| Snapshot {
                value_cents: *value_cents,
                as_of: NaiveDate::parse_from_str(as_of, "%Y-%m-%d").unwrap(),
            })
            .collect()
    }

    #[test]
    fn falls_back_to_principal_without_snapshots() {
        let ctx = CalcContext::default();
        let inv = test_investment("manual", 500_000, "{}");
        let date = NaiveDate::from_ymd_opt(2026, 6, 1).unwrap();
        assert_eq!(Manual.value_at(&inv, &ctx, date).unwrap(), 500_000);
    }

    #[test]
    fn returns_latest_snapshot_on_or_before_date() {
        let ctx = CalcContext {
            snapshots: snapshots(&[
                (510_000, "2026-02-01"),
                (530_000, "2026-04-01"),
                (550_000, "2026-08-01"), // future relative to as_of
            ]),
            ..Default::default()
        };
        let inv = test_investment("manual", 500_000, "{}");
        let date = NaiveDate::from_ymd_opt(2026, 6, 1).unwrap();
        assert_eq!(Manual.value_at(&inv, &ctx, date).unwrap(), 530_000);
    }
}
