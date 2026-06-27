//! Forward investment simulation: "if I put X now and Y every month at Z% for
//! N years, how much would it grow?". Pure logic, tested with hand-computed
//! reference values. Powers the Simulator UI, the forward half of the detail
//! chart, and (via `solve_contribution`) investment goals.
//!
//! Convention: **monthly compounding** with the nominal annual rate split into
//! 12 (`i = annual_rate_bps / 10_000 / 12`), recurring contributions applied at
//! the END of each month. This is the standard savings-calculator model and is
//! easy to verify by hand; it is an estimate, not the day-count-exact accrual
//! the live calculators (Nu/CETES/BONDDIA) use for real positions.

use crate::error::{AppError, AppResult};

/// How often the recurring contribution is made. Everything is normalized to a
/// monthly step, so non-monthly cadences spread their amount across the month.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Cadence {
    Monthly,
    Biweekly,
    Weekly,
    None,
}

impl Cadence {
    /// Contribution amount per month for this cadence (the simulator advances
    /// one month at a time). Biweekly ≈ 2/month, weekly ≈ 52/12 per month.
    fn monthly_multiple(self) -> f64 {
        match self {
            Cadence::Monthly => 1.0,
            Cadence::Biweekly => 2.0,
            Cadence::Weekly => 52.0 / 12.0,
            Cadence::None => 0.0,
        }
    }
}

#[derive(Debug, Clone)]
pub struct SimulationInput {
    /// Lump sum invested at t=0.
    pub initial_cents: i64,
    /// Recurring contribution amount (per cadence occurrence).
    pub contribution_cents: i64,
    pub cadence: Cadence,
    /// Nominal annual rate in basis points (e.g. 1050 = 10.50%).
    pub annual_rate_bps: i64,
    /// Horizon in whole months (e.g. 60 = 5 years).
    pub months: i64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct SimulationPoint {
    /// Months elapsed since t=0 (0 = today).
    pub month: i64,
    /// Cumulative amount the user has put in by this month (initial + Σ contributions).
    pub contributed_cents: i64,
    /// Projected portfolio value at this month.
    pub value_cents: i64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct SimulationResult {
    /// One point per month, including t=0 and the final month.
    pub points: Vec<SimulationPoint>,
    pub final_value_cents: i64,
    pub total_contributed_cents: i64,
    /// final_value − total_contributed (the compound growth).
    pub total_interest_cents: i64,
}

/// Run the forward simulation, returning a monthly series plus headline totals.
pub fn simulate(input: &SimulationInput) -> AppResult<SimulationResult> {
    if input.months < 0 {
        return Err(AppError::InvalidInput("el plazo no puede ser negativo".into()));
    }
    if input.annual_rate_bps < 0 {
        return Err(AppError::InvalidInput("la tasa no puede ser negativa".into()));
    }
    let i = input.annual_rate_bps as f64 / 10_000.0 / 12.0;
    let monthly_contrib = input.contribution_cents as f64 * input.cadence.monthly_multiple();

    let mut value = input.initial_cents as f64;
    let mut contributed = input.initial_cents as f64;
    let mut points = Vec::with_capacity(input.months as usize + 1);
    points.push(SimulationPoint {
        month: 0,
        contributed_cents: contributed.round() as i64,
        value_cents: value.round() as i64,
    });

    for month in 1..=input.months {
        // interest accrues on the running balance, then the contribution lands.
        value = value * (1.0 + i) + monthly_contrib;
        contributed += monthly_contrib;
        points.push(SimulationPoint {
            month,
            contributed_cents: contributed.round() as i64,
            value_cents: value.round() as i64,
        });
    }

    let final_value_cents = value.round() as i64;
    let total_contributed_cents = contributed.round() as i64;
    Ok(SimulationResult {
        final_value_cents,
        total_contributed_cents,
        total_interest_cents: final_value_cents - total_contributed_cents,
        points,
    })
}

/// Inverse of `simulate` for goal planning: the monthly contribution needed so
/// that `initial` grows to `target` in `months` at `annual_rate_bps`. Returns
/// cents/month (rounded up so the goal is reached, never undershot). Returns 0
/// when the initial amount alone already reaches the target.
pub fn solve_monthly_contribution(
    initial_cents: i64,
    target_cents: i64,
    annual_rate_bps: i64,
    months: i64,
) -> AppResult<i64> {
    if months <= 0 {
        return Err(AppError::InvalidInput("el plazo debe ser positivo".into()));
    }
    let i = annual_rate_bps as f64 / 10_000.0 / 12.0;
    let n = months as f64;
    // Future value of the initial lump sum.
    let fv_initial = initial_cents as f64 * (1.0 + i).powf(n);
    let needed = target_cents as f64 - fv_initial;
    if needed <= 0.0 {
        return Ok(0);
    }
    // Future-value-of-annuity factor (contribution at end of each month).
    let annuity_factor = if i.abs() < 1e-12 {
        n
    } else {
        ((1.0 + i).powf(n) - 1.0) / i
    };
    Ok((needed / annuity_factor).ceil() as i64)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lump_sum_only_compounds_monthly() {
        // 10,000.00 at 12% nominal (1%/month) for 12 months:
        // 1,000,000 × 1.01^12 = 1,126,825.03 → 1,126,825
        let r = simulate(&SimulationInput {
            initial_cents: 1_000_000,
            contribution_cents: 0,
            cadence: Cadence::None,
            annual_rate_bps: 1200,
            months: 12,
        })
        .unwrap();
        assert_eq!(r.final_value_cents, 1_126_825);
        assert_eq!(r.total_contributed_cents, 1_000_000);
        assert_eq!(r.total_interest_cents, 126_825);
        assert_eq!(r.points.len(), 13);
        assert_eq!(r.points[0].value_cents, 1_000_000);
    }

    #[test]
    fn monthly_contributions_only() {
        // 1,000.00/month at 12% (1%/month) for 12 months, end-of-month:
        // 100,000 × ((1.01^12 − 1)/0.01) = 100,000 × 12.6825030 = 1,268,250
        let r = simulate(&SimulationInput {
            initial_cents: 0,
            contribution_cents: 100_000,
            cadence: Cadence::Monthly,
            annual_rate_bps: 1200,
            months: 12,
        })
        .unwrap();
        assert_eq!(r.final_value_cents, 1_268_250);
        assert_eq!(r.total_contributed_cents, 1_200_000);
        assert_eq!(r.total_interest_cents, 68_250);
    }

    #[test]
    fn zero_rate_is_just_the_sum() {
        let r = simulate(&SimulationInput {
            initial_cents: 50_000,
            contribution_cents: 10_000,
            cadence: Cadence::Monthly,
            annual_rate_bps: 0,
            months: 10,
        })
        .unwrap();
        assert_eq!(r.final_value_cents, 150_000);
        assert_eq!(r.total_interest_cents, 0);
    }

    #[test]
    fn solve_contribution_round_trips() {
        // Find the monthly amount to reach 1,268,250 in 12 months at 12% from 0.
        let c = solve_monthly_contribution(0, 1_268_250, 1200, 12).unwrap();
        // Should be ~100,000; feeding it back must reach (and not undershoot).
        assert!((99_990..=100_010).contains(&c), "contribution {c}");
        let r = simulate(&SimulationInput {
            initial_cents: 0,
            contribution_cents: c,
            cadence: Cadence::Monthly,
            annual_rate_bps: 1200,
            months: 12,
        })
        .unwrap();
        assert!(r.final_value_cents >= 1_268_250);
    }

    #[test]
    fn solve_returns_zero_when_initial_already_reaches() {
        let c = solve_monthly_contribution(1_000_000, 500_000, 1000, 24).unwrap();
        assert_eq!(c, 0);
    }
}
