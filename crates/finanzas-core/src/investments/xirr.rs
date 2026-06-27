//! XIRR: the annualized internal rate of return over irregularly-dated cash
//! flows. Used for the investments portfolio's "real" return, which a simple
//! gain/cost ratio can't express once money goes in and out on different dates.
//! Pure logic, tested against a hand-computed reference.
//!
//! Sign convention: outflows (money you put in) are NEGATIVE, inflows (money
//! you take out, plus the current value valued today) are POSITIVE. The rate
//! `r` solves NPV(r) = Σ cf_i / (1+r)^(years from the first flow) = 0.

use chrono::NaiveDate;

#[derive(Debug, Clone, Copy)]
pub struct CashFlow {
    pub date: NaiveDate,
    pub amount_cents: i64,
}

fn npv(flows: &[(f64, f64)], rate: f64) -> f64 {
    flows
        .iter()
        .map(|&(t, cf)| cf / (1.0 + rate).powf(t))
        .sum()
}

/// Annualized return as a fraction (0.10 = 10%), or `None` when it is
/// undefined (fewer than one in/out pair, no sign change, or no convergence).
pub fn xirr(flows: &[CashFlow]) -> Option<f64> {
    if flows.len() < 2 {
        return None;
    }
    let has_neg = flows.iter().any(|f| f.amount_cents < 0);
    let has_pos = flows.iter().any(|f| f.amount_cents > 0);
    if !has_neg || !has_pos {
        return None;
    }
    let t0 = flows.iter().map(|f| f.date).min()?;
    // (years since t0, amount in pesos) — scale cancels in NPV=0 but keeps the
    // numbers tame.
    let fs: Vec<(f64, f64)> = flows
        .iter()
        .map(|f| {
            (
                (f.date - t0).num_days() as f64 / 365.0,
                f.amount_cents as f64 / 100.0,
            )
        })
        .collect();

    // Newton-Raphson from 10%, then fall back to bisection if it leaves the
    // valid domain or stalls.
    let mut rate = 0.1_f64;
    for _ in 0..100 {
        let f = npv(&fs, rate);
        if f.abs() < 1e-7 {
            return Some(rate);
        }
        let eps = 1e-6;
        let deriv = (npv(&fs, rate + eps) - f) / eps;
        if deriv.abs() < 1e-12 {
            break;
        }
        let next = rate - f / deriv;
        if !next.is_finite() || next <= -0.9999 {
            break;
        }
        rate = next;
    }

    // Bisection on a wide bracket as a robust fallback.
    let (mut lo, mut hi) = (-0.9999_f64, 100.0_f64);
    let (mut flo, fhi) = (npv(&fs, lo), npv(&fs, hi));
    if flo.signum() == fhi.signum() {
        return None;
    }
    for _ in 0..200 {
        let mid = (lo + hi) / 2.0;
        let fmid = npv(&fs, mid);
        if fmid.abs() < 1e-7 {
            return Some(mid);
        }
        if fmid.signum() == flo.signum() {
            lo = mid;
            flo = fmid;
        } else {
            hi = mid;
        }
    }
    Some((lo + hi) / 2.0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(y: i32, m: u32, day: u32) -> NaiveDate {
        NaiveDate::from_ymd_opt(y, m, day).unwrap()
    }

    #[test]
    fn simple_one_year_10_percent() {
        // Put in 1,000 today, worth 1,100 a year later → 10%.
        let r = xirr(&[
            CashFlow { date: d(2025, 1, 1), amount_cents: -100_000 },
            CashFlow { date: d(2026, 1, 1), amount_cents: 110_000 },
        ])
        .unwrap();
        assert!((r - 0.10).abs() < 1e-4, "rate {r}");
    }

    #[test]
    fn doubling_in_one_year_is_100_percent() {
        let r = xirr(&[
            CashFlow { date: d(2025, 1, 1), amount_cents: -100_000 },
            CashFlow { date: d(2026, 1, 1), amount_cents: 200_000 },
        ])
        .unwrap();
        assert!((r - 1.0).abs() < 1e-4, "rate {r}");
    }

    #[test]
    fn multiple_contributions() {
        // 1,000 at t0, another 1,000 at +6 months, total worth 2,200 at +1yr.
        // Money-weighted return is positive and below the 20% a same-day double
        // would imply.
        let r = xirr(&[
            CashFlow { date: d(2025, 1, 1), amount_cents: -100_000 },
            CashFlow { date: d(2025, 7, 1), amount_cents: -100_000 },
            CashFlow { date: d(2026, 1, 1), amount_cents: 220_000 },
        ])
        .unwrap();
        assert!(r > 0.10 && r < 0.35, "rate {r}");
    }

    #[test]
    fn no_sign_change_is_none() {
        assert!(xirr(&[
            CashFlow { date: d(2025, 1, 1), amount_cents: -100_000 },
            CashFlow { date: d(2026, 1, 1), amount_cents: -50_000 },
        ])
        .is_none());
    }
}
