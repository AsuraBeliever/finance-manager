//! Daily yield of an investment: what it earned each day, so the money an
//! investment generates shows up day by day (and month by month) even when
//! nothing is withdrawn.
//!
//! The gain of a position on a day is `value_at(day) − net_invested(day)`
//! (`gain_at`); what it earned ON that day is the change of that gain from the
//! day before. Deposits and withdrawals move value and net invested by the
//! same amount, so they never count as yield. Because each day's gain is a
//! whole number of cents, the days telescope exactly: the history from the
//! start up to a day adds up to the gain on that day, to the cent.
//!
//! Two ways to build the history, picked by
//! `InvestmentCalculator::values_past_days`:
//! - **Modeled** (CETES, tasa fija, cajita Nu, BONDDIA estimated, manual): any
//!   day can be valued, so the whole history is derived again from the start
//!   (`daily_gains`) — after an edit nothing is left stale.
//! - **Observed** (crypto, BONDDIA títulos): only today's price is known, so
//!   each day is recorded as it happens (`observed_day`). The first record is
//!   a baseline worth 0: the gain from before the app was watching has no
//!   date to land on, and dumping it all on one day would fake a huge "income".

use chrono::{Duration, NaiveDate};

use super::{net_invested, parse_start_date, CalcContext, InvestmentCalculator};
use crate::error::AppResult;
use crate::models::Investment;

/// One day of history. `total_gain_cents` is the position's gain on `day`
/// (the running sum of every `gain_cents` so far), kept so an observed
/// history can continue from it without valuing the past.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct YieldDay {
    pub day: NaiveDate,
    pub gain_cents: i64,
    pub total_gain_cents: i64,
}

/// Gain of the position on `day`: value minus net contributed capital.
pub fn gain_at(
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    ctx: &CalcContext,
    day: NaiveDate,
) -> AppResult<i64> {
    Ok(calc.value_at(inv, ctx, day)? - net_invested(inv, ctx, day))
}

/// Last day a modeled history can change: today, or the maturity date when it
/// comes first (past it the gain is flat, so walking further only finds zeros).
pub fn history_end(
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    today: NaiveDate,
) -> NaiveDate {
    calc.maturity_date(inv)
        .map_or(today, |maturity| maturity.min(today))
}

/// The modeled history over `(after, through]`: one entry per day whose gain
/// moved, in date order. Days that earned exactly nothing are left out (a
/// matured CETES would otherwise grow a zero row every day forever).
/// `after` = the day before the start date gives the whole history.
pub fn daily_gains(
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    ctx: &CalcContext,
    after: NaiveDate,
    through: NaiveDate,
) -> AppResult<Vec<YieldDay>> {
    let mut out = Vec::new();
    let mut prev = gain_at(calc, inv, ctx, after)?;
    let mut day = after + Duration::days(1);
    while day <= through {
        let total = gain_at(calc, inv, ctx, day)?;
        if total != prev {
            out.push(YieldDay {
                day,
                gain_cents: total - prev,
                total_gain_cents: total,
            });
        }
        prev = total;
        day += Duration::days(1);
    }
    Ok(out)
}

/// The whole modeled history, from the start date to `history_end`.
pub fn full_history(
    calc: &dyn InvestmentCalculator,
    inv: &Investment,
    ctx: &CalcContext,
    today: NaiveDate,
) -> AppResult<Vec<YieldDay>> {
    let start = parse_start_date(inv)?;
    daily_gains(
        calc,
        inv,
        ctx,
        start - Duration::days(1),
        history_end(calc, inv, today),
    )
}

/// Today's entry of an observed history, given the gain on the last recorded
/// day (`None` = nothing recorded yet, so today is the baseline and earns 0).
pub fn observed_day(previous_total: Option<i64>, today: NaiveDate, total: i64) -> YieldDay {
    YieldDay {
        day: today,
        gain_cents: previous_total.map_or(0, |prev| total - prev),
        total_gain_cents: total,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::investments::{find, test_ctx, test_investment, Snapshot};

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    fn sum(days: &[YieldDay]) -> i64 {
        days.iter().map(|y| y.gain_cents).sum()
    }

    #[test]
    fn cetes_history_adds_up_to_the_gain_and_stops_at_maturity() {
        // $10,000.00 in CETES 91 days at 10.80%, ISR 0.50%, from 2026-01-01.
        // Gain on day d = 1_000_000 × (0.108·d/360 − 0.005·d/365)
        //               = d × (300 − 13.6986…) = d × 286.3014 ¢.
        // At maturity (d = 91): 91 × 286.3014 = 26_053.42 → 26_053 ¢.
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 50}"#,
        );
        let ctx = test_ctx(&[]);
        let calc = find("cetes").unwrap();
        // Asked through mid-year, the history still ends at maturity (04-02).
        let days = full_history(calc, &inv, &ctx, d("2026-06-30")).unwrap();
        assert_eq!(days.len(), 91);
        assert_eq!(days.first().unwrap().day, d("2026-01-02"));
        assert_eq!(days.last().unwrap().day, d("2026-04-02"));
        // Each day earns 286 or 287 ¢ (286.30 ¢ rounded through the running total).
        assert!(days.iter().all(|y| (286..=287).contains(&y.gain_cents)));
        assert_eq!(sum(&days), 26_053);
        assert_eq!(days.last().unwrap().total_gain_cents, 26_053);
        assert_eq!(gain_at(calc, &inv, &ctx, d("2026-06-30")).unwrap(), 26_053);
    }

    #[test]
    fn a_deposit_is_not_yield() {
        // tasa fija simple at 36.5% = exactly 0.1% per day.
        // $10,000.00 from 01-01, + $5,000.00 deposited on 01-11.
        // Day 01-11: the $10,000 earns its 10th day (1_000 ¢); the deposit
        // starts at factor 1 that same day, so it adds value AND capital alike.
        // Day 01-12: 1_000 ¢ + 0.1% of $5,000 (500 ¢) = 1_500 ¢.
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 3650, "compounding": "simple"}"#,
        );
        let ctx = test_ctx(&[("deposit", 500_000, "2026-01-11")]);
        let calc = find("fixed_rate").unwrap();
        let days = daily_gains(calc, &inv, &ctx, d("2025-12-31"), d("2026-01-12")).unwrap();
        let on = |s: &str| days.iter().find(|y| y.day == d(s)).unwrap().gain_cents;
        assert_eq!(on("2026-01-10"), 1_000);
        assert_eq!(on("2026-01-11"), 1_000);
        assert_eq!(on("2026-01-12"), 1_500);
        // 01-02 … 01-11 = 10 days × 1_000 + 01-12 × 1_500 = 11_500
        // = the position's gain on 01-12.
        assert_eq!(sum(&days), 11_500);
        assert_eq!(gain_at(calc, &inv, &ctx, d("2026-01-12")).unwrap(), 11_500);
    }

    #[test]
    fn a_withdrawal_is_not_a_loss() {
        // Same 0.1%/day position; $5,000.00 taken out on 01-11. A withdrawal
        // stops the amount accruing from that day, carrying the growth it had
        // earned (5 000 ¢ × 0.1% × 10 days = 500 ¢) out with it.
        // Day 01-11: 1_000 ¢ (the 10th day on $10,000) − 0 (new amount, factor 1)
        //   → value −500_000, net −500_000: the day still earns 1_000 ¢.
        // Day 01-12: 0.1% of the $5,000 left = 500 ¢.
        let inv = test_investment(
            "fixed_rate",
            1_000_000,
            r#"{"annual_rate_bps": 3650, "compounding": "simple"}"#,
        );
        let ctx = test_ctx(&[("withdrawal", 500_000, "2026-01-11")]);
        let calc = find("fixed_rate").unwrap();
        let days = daily_gains(calc, &inv, &ctx, d("2025-12-31"), d("2026-01-12")).unwrap();
        let on = |s: &str| days.iter().find(|y| y.day == d(s)).unwrap().gain_cents;
        assert_eq!(on("2026-01-11"), 1_000);
        assert_eq!(on("2026-01-12"), 500);
        assert!(days.iter().all(|y| y.gain_cents > 0));
        assert_eq!(
            sum(&days),
            gain_at(calc, &inv, &ctx, d("2026-01-12")).unwrap()
        );
    }

    #[test]
    fn manual_snapshots_land_on_their_own_days() {
        // $5,000.00 marked at $5,100.00 on 02-01 and $5,050.00 on 03-01:
        // +100.00 on 02-01, −50.00 on 03-01, nothing on the days between.
        let inv = test_investment("manual", 500_000, "{}");
        let ctx = CalcContext {
            snapshots: vec![
                Snapshot {
                    value_cents: 510_000,
                    as_of: d("2026-02-01"),
                },
                Snapshot {
                    value_cents: 505_000,
                    as_of: d("2026-03-01"),
                },
            ],
            ..Default::default()
        };
        let calc = find("manual").unwrap();
        let days = full_history(calc, &inv, &ctx, d("2026-03-31")).unwrap();
        assert_eq!(
            days,
            vec![
                YieldDay {
                    day: d("2026-02-01"),
                    gain_cents: 10_000,
                    total_gain_cents: 10_000
                },
                YieldDay {
                    day: d("2026-03-01"),
                    gain_cents: -5_000,
                    total_gain_cents: 5_000
                },
            ]
        );
    }

    #[test]
    fn observed_history_starts_with_a_zero_baseline() {
        // Crypto already up $1,200.00 when first seen: that day earns 0, and the
        // next day's move (to $1,150.00) is −50.00.
        let first = observed_day(None, d("2026-05-01"), 120_000);
        assert_eq!(first.gain_cents, 0);
        let next = observed_day(Some(first.total_gain_cents), d("2026-05-02"), 115_000);
        assert_eq!(next.gain_cents, -5_000);
        assert_eq!(next.total_gain_cents, 115_000);
    }

    #[test]
    fn which_calculators_value_past_days() {
        let crypto = test_investment("crypto", 100_000, r#"{"symbol": "BTC", "quantity_e8": 1}"#);
        assert!(!find("crypto").unwrap().values_past_days(&crypto));
        let exact = test_investment("bonddia", 100_000, r#"{"titulos": 30}"#);
        assert!(!find("bonddia").unwrap().values_past_days(&exact));
        let estimated = test_investment("bonddia", 100_000, r#"{"spread_bps": 50}"#);
        assert!(find("bonddia").unwrap().values_past_days(&estimated));
        let cetes = test_investment("cetes", 100_000, "{}");
        assert!(find("cetes").unwrap().values_past_days(&cetes));
    }
}
