pub mod bonddia;
pub mod cetes;
pub mod crypto;
pub mod fixed_rate;
pub mod manual;
pub mod nu_cajita;
pub mod simulate;
pub mod xirr;

use chrono::NaiveDate;
use serde_json::Value;

use crate::error::{AppError, AppResult};
use crate::models::Investment;

/// A pluggable yield calculator. To add a new one: implement this trait in a
/// new file, add it to `registry()`, write unit tests with hand-computed
/// reference values, and add the form variant in the frontend.
/// See docs/INVESTMENTS.md.
pub trait InvestmentCalculator: Send + Sync {
    fn id(&self) -> &'static str;
    /// Value in cents at `as_of`. Future dates yield projections.
    /// `ctx` carries the stored data some calculators need, preloaded by the
    /// storage layer (see `CalcContext`).
    fn value_at(&self, inv: &Investment, ctx: &CalcContext, as_of: NaiveDate) -> AppResult<i64>;
    fn maturity_date(&self, inv: &Investment) -> Option<NaiveDate>;

    /// Current effective annual rate in bps, used to project the value FORWARD
    /// from today. `None` means the instrument has no forward-growth model
    /// (crypto, manual): the projection then stays flat at the current value.
    /// For constant-rate calculators this equals the param rate; for bonddia it
    /// is the latest target rate minus the tracking spread.
    fn effective_annual_rate_bps(
        &self,
        _inv: &Investment,
        _ctx: &CalcContext,
    ) -> AppResult<Option<i64>> {
        Ok(None)
    }
}

/// Stored data backing one investment's valuation, preloaded by the storage
/// layer (rusqlite on desktop, D1 in the worker) so calculators stay
/// storage-agnostic. Only the fields the investment's calculator reads need
/// to be populated.
#[derive(Debug, Default)]
pub struct CalcContext {
    /// Deposits/withdrawals of this investment, chronological.
    pub movements: Vec<Movement>,
    /// (effective_date, rate_bps) steps of the 'objetivo' series,
    /// chronological (bonddia).
    pub rate_history: Vec<(NaiveDate, i64)>,
    /// Latest official BONDDIA price per título in micros, already passed
    /// through `parse_bonddia_price` (bonddia exact mode).
    pub bonddia_price_micros: Option<i64>,
    /// Latest cached MXN price in cents for this investment's symbol (crypto).
    pub crypto_price_cents: Option<i64>,
    /// Manual value snapshots, chronological (manual).
    pub snapshots: Vec<Snapshot>,
}

#[derive(Debug)]
pub struct Movement {
    pub kind: String, // 'deposit' | 'withdrawal'
    pub amount_cents: i64,
    pub occurred_at: NaiveDate,
}

#[derive(Debug)]
pub struct Snapshot {
    pub value_cents: i64,
    pub as_of: NaiveDate,
}

pub fn registry() -> &'static [&'static dyn InvestmentCalculator] {
    static CALCULATORS: &[&dyn InvestmentCalculator] = &[
        &nu_cajita::NuCajita,
        &cetes::Cetes,
        &bonddia::Bonddia,
        &crypto::Crypto,
        &fixed_rate::FixedRate,
        &manual::Manual,
    ];
    CALCULATORS
}

pub fn find(id: &str) -> AppResult<&'static dyn InvestmentCalculator> {
    registry()
        .iter()
        .find(|c| c.id() == id)
        .copied()
        .ok_or_else(|| AppError::InvalidInput(format!("calculadora desconocida: {id}")))
}

// ---- shared param helpers ----

pub(crate) fn parse_params(inv: &Investment) -> AppResult<Value> {
    serde_json::from_str(&inv.params_json)
        .map_err(|e| AppError::InvalidInput(format!("params_json inválido: {e}")))
}

pub(crate) fn param_i64(params: &Value, key: &str) -> AppResult<i64> {
    params
        .get(key)
        .and_then(Value::as_i64)
        .ok_or_else(|| AppError::InvalidInput(format!("falta el parámetro '{key}'")))
}

pub(crate) fn param_i64_or(params: &Value, key: &str, default: i64) -> i64 {
    params.get(key).and_then(Value::as_i64).unwrap_or(default)
}

pub(crate) fn parse_start_date(inv: &Investment) -> AppResult<NaiveDate> {
    NaiveDate::parse_from_str(&inv.start_date, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha de inicio inválida".into()))
}

/// Parse the stored 'bonddia_price' settings value into price-per-título
/// micros. Plausibility guard: the NAV is a few pesos; a value outside this
/// range means a bad scrape and must not poison valuations.
pub fn parse_bonddia_price(raw: &str) -> Option<i64> {
    serde_json::from_str::<Value>(raw)
        .ok()
        .and_then(|v| v.get("price_micros").and_then(|p| p.as_i64()))
        .filter(|p| (500_000..100_000_000).contains(p))
}

// ---- position valuation with movements ----

/// Value of a position where every amount accrues independently from its own
/// date: initial principal from start_date, each deposit from its date, and
/// each withdrawal stops accruing from its date (subtracted with the same
/// growth it would have earned). `factor(from)` is the calculator's growth
/// multiplier from `from` to the valuation date.
pub(crate) fn position_value(
    inv: &Investment,
    ctx: &CalcContext,
    as_of: NaiveDate,
    factor: impl Fn(NaiveDate) -> f64,
) -> AppResult<i64> {
    let start = parse_start_date(inv)?;
    let mut value = inv.principal_cents as f64 * factor(start);
    for m in &ctx.movements {
        if m.occurred_at > as_of {
            continue;
        }
        let sign = if m.kind == "withdrawal" { -1.0 } else { 1.0 };
        value += sign * m.amount_cents as f64 * factor(m.occurred_at.max(start));
    }
    Ok(value.round() as i64)
}

/// Net contributed capital up to `as_of`: principal + deposits − withdrawals.
/// Gain = current value − net invested (captures realized + unrealized yield).
pub fn net_invested(inv: &Investment, ctx: &CalcContext, as_of: NaiveDate) -> i64 {
    let mut total = inv.principal_cents;
    for m in &ctx.movements {
        if m.occurred_at > as_of {
            continue;
        }
        total += if m.kind == "withdrawal" {
            -m.amount_cents
        } else {
            m.amount_cents
        };
    }
    total
}

#[cfg(test)]
pub(crate) fn test_investment(calculator: &str, principal_cents: i64, params: &str) -> Investment {
    Investment {
        id: 1,
        name: "test".into(),
        calculator: calculator.into(),
        currency_code: "MXN".into(),
        principal_cents,
        start_date: "2026-01-01".into(),
        params_json: params.into(),
        linked_wallet_id: None,
        is_closed: false,
        notes: None,
        created_at: String::new(),
    }
}

/// Context with just movements, from (kind, amount_cents, 'YYYY-MM-DD') tuples.
#[cfg(test)]
pub(crate) fn test_ctx(movements: &[(&str, i64, &str)]) -> CalcContext {
    CalcContext {
        movements: movements
            .iter()
            .map(|(kind, amount_cents, date)| Movement {
                kind: (*kind).into(),
                amount_cents: *amount_cents,
                occurred_at: NaiveDate::parse_from_str(date, "%Y-%m-%d").unwrap(),
            })
            .collect(),
        ..Default::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn movements_accrue_from_their_own_dates() {
        // fixed_rate simple at 36.5% = exactly 0.1% per day, so factors are
        // trivial to hand-check. Start 2026-01-01, valued at 2026-04-11 (+100d):
        //   principal 1,000.00 × 1.100            = 1,100.00
        //   deposit     500.00 on 2026-03-02 (+40d to valuation) × 1.040 = 520.00
        //   withdrawal  300.00 on 2026-03-22 (+20d) × 1.020      = −306.00
        //   total = 1,314.00
        let inv = test_investment(
            "fixed_rate",
            100_000,
            r#"{"annual_rate_bps": 3650, "compounding": "simple"}"#,
        );
        let ctx = test_ctx(&[
            ("deposit", 50_000, "2026-03-02"),
            ("withdrawal", 30_000, "2026-03-22"),
        ]);
        let as_of = NaiveDate::from_ymd_opt(2026, 4, 11).unwrap();
        let value = find("fixed_rate")
            .unwrap()
            .value_at(&inv, &ctx, as_of)
            .unwrap();
        assert_eq!(value, 131_400);
    }

    #[test]
    fn future_movements_are_excluded() {
        let inv = test_investment(
            "fixed_rate",
            100_000,
            r#"{"annual_rate_bps": 3650, "compounding": "simple"}"#,
        );
        let ctx = test_ctx(&[("deposit", 50_000, "2026-06-01")]);
        let as_of = NaiveDate::from_ymd_opt(2026, 4, 11).unwrap(); // before the deposit
        let value = find("fixed_rate")
            .unwrap()
            .value_at(&inv, &ctx, as_of)
            .unwrap();
        assert_eq!(value, 110_000); // only the principal accrued
    }

    #[test]
    fn cetes_reinvest_compounds_per_plazo() {
        // 10,000.00 at 10.80%, plazo 91, reinversión automática.
        // 182 days = two full plazos: (1 + 0.108·91/360)² = 1.0273² = 1.05534529
        // → 10,553.45
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 0, "reinvest": true}"#,
        );
        let ctx = test_ctx(&[]);
        let as_of = NaiveDate::from_ymd_opt(2026, 7, 2).unwrap();
        let value = find("cetes").unwrap().value_at(&inv, &ctx, as_of).unwrap();
        assert_eq!(value, 1_055_345);
        // rolling position has no fixed maturity
        assert_eq!(find("cetes").unwrap().maturity_date(&inv), None);
    }

    #[test]
    fn nu_with_monthly_deposits_and_withdrawal() {
        // Mirrors the user's real scenario: monthly 1,000.00 deposits, then a
        // withdrawal, then nothing. Sanity bounds rather than exact values:
        // value must exceed net invested (positive yield) and stay below
        // gross-of-yield-on-everything upper bound.
        let inv = test_investment("nu_cajita", 100_000, r#"{"annual_rate_bps": 1500}"#);
        let ctx = test_ctx(&[
            ("deposit", 100_000, "2026-02-01"),
            ("deposit", 100_000, "2026-03-01"),
            ("deposit", 100_000, "2026-04-01"),
            ("withdrawal", 250_000, "2026-05-01"),
        ]);
        let as_of = NaiveDate::from_ymd_opt(2026, 6, 10).unwrap();
        let value = find("nu_cajita")
            .unwrap()
            .value_at(&inv, &ctx, as_of)
            .unwrap();
        let net = net_invested(&inv, &ctx, as_of);
        assert_eq!(net, 150_000);
        assert!(
            value > net,
            "value {value} should exceed net invested {net}"
        );
        assert!(value < 170_000, "value {value} suspiciously high");
    }

    #[test]
    fn net_invested_sums_signed_movements() {
        let inv = test_investment("nu_cajita", 100_000, r#"{"annual_rate_bps": 1500}"#);
        let ctx = test_ctx(&[
            ("deposit", 50_000, "2026-02-01"),
            ("withdrawal", 30_000, "2026-03-01"),
        ]);
        let as_of = NaiveDate::from_ymd_opt(2026, 6, 1).unwrap();
        assert_eq!(net_invested(&inv, &ctx, as_of), 120_000);
        // before the withdrawal happened
        let earlier = NaiveDate::from_ymd_opt(2026, 2, 15).unwrap();
        assert_eq!(net_invested(&inv, &ctx, earlier), 150_000);
    }

    #[test]
    fn bonddia_price_parses_and_guards_plausibility() {
        let raw = r#"{"price_micros": 2334524, "date": "2026-06-11"}"#;
        assert_eq!(parse_bonddia_price(raw), Some(2_334_524));
        // out-of-range scrapes are dropped
        assert_eq!(parse_bonddia_price(r#"{"price_micros": 1}"#), None);
        assert_eq!(parse_bonddia_price(r#"{"price_micros": 200000000}"#), None);
        assert_eq!(parse_bonddia_price("not json"), None);
    }
}
