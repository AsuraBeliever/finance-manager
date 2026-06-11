pub mod cetes;
pub mod fixed_rate;
pub mod manual;
pub mod nu_cajita;

use chrono::NaiveDate;
use rusqlite::Connection;
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
    /// `conn` is available for calculators backed by stored data (e.g. manual snapshots).
    fn value_at(&self, inv: &Investment, conn: &Connection, as_of: NaiveDate) -> AppResult<i64>;
    fn maturity_date(&self, inv: &Investment) -> Option<NaiveDate>;
}

pub fn registry() -> &'static [&'static dyn InvestmentCalculator] {
    static CALCULATORS: &[&dyn InvestmentCalculator] = &[
        &nu_cajita::NuCajita,
        &cetes::Cetes,
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

// ---- position valuation with movements ----

pub(crate) struct Movement {
    pub kind: String, // 'deposit' | 'withdrawal'
    pub amount_cents: i64,
    pub occurred_at: NaiveDate,
}

pub(crate) fn load_movements(conn: &Connection, investment_id: i64) -> AppResult<Vec<Movement>> {
    let mut stmt = conn.prepare(
        "SELECT kind, amount_cents, occurred_at FROM investment_movements
         WHERE investment_id = ?1 ORDER BY occurred_at, id",
    )?;
    let rows = stmt
        .query_map([investment_id], |r| {
            Ok((
                r.get::<_, String>(0)?,
                r.get::<_, i64>(1)?,
                r.get::<_, String>(2)?,
            ))
        })?
        .collect::<Result<Vec<_>, _>>()?;
    rows.into_iter()
        .map(|(kind, amount_cents, date)| {
            Ok(Movement {
                kind,
                amount_cents,
                occurred_at: NaiveDate::parse_from_str(&date, "%Y-%m-%d")
                    .map_err(|_| AppError::InvalidInput("fecha de movimiento inválida".into()))?,
            })
        })
        .collect()
}

/// Value of a position where every amount accrues independently from its own
/// date: initial principal from start_date, each deposit from its date, and
/// each withdrawal stops accruing from its date (subtracted with the same
/// growth it would have earned). `factor(from)` is the calculator's growth
/// multiplier from `from` to the valuation date.
pub(crate) fn position_value(
    inv: &Investment,
    conn: &Connection,
    as_of: NaiveDate,
    factor: impl Fn(NaiveDate) -> f64,
) -> AppResult<i64> {
    let start = parse_start_date(inv)?;
    let mut value = inv.principal_cents as f64 * factor(start);
    for m in load_movements(conn, inv.id)? {
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
pub(crate) fn net_invested(
    conn: &Connection,
    inv: &Investment,
    as_of: NaiveDate,
) -> AppResult<i64> {
    let mut total = inv.principal_cents;
    for m in load_movements(conn, inv.id)? {
        if m.occurred_at > as_of {
            continue;
        }
        total += if m.kind == "withdrawal" {
            -m.amount_cents
        } else {
            m.amount_cents
        };
    }
    Ok(total)
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;

    /// Insert the investment row (FK target) plus its movements.
    fn seed(conn: &Connection, inv: &Investment, movements: &[(&str, i64, &str)]) {
        conn.execute(
            "INSERT INTO investments (id, name, calculator, principal_cents, start_date, params_json)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            rusqlite::params![inv.id, inv.name, inv.calculator, inv.principal_cents, inv.start_date, inv.params_json],
        )
        .unwrap();
        for (kind, amount, date) in movements {
            conn.execute(
                "INSERT INTO investment_movements (investment_id, kind, amount_cents, occurred_at)
                 VALUES (?1, ?2, ?3, ?4)",
                rusqlite::params![inv.id, kind, amount, date],
            )
            .unwrap();
        }
    }

    #[test]
    fn movements_accrue_from_their_own_dates() {
        let conn = open_in_memory();
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
        seed(
            &conn,
            &inv,
            &[
                ("deposit", 50_000, "2026-03-02"),
                ("withdrawal", 30_000, "2026-03-22"),
            ],
        );
        let as_of = NaiveDate::from_ymd_opt(2026, 4, 11).unwrap();
        let value = find("fixed_rate")
            .unwrap()
            .value_at(&inv, &conn, as_of)
            .unwrap();
        assert_eq!(value, 131_400);
    }

    #[test]
    fn future_movements_are_excluded() {
        let conn = open_in_memory();
        let inv = test_investment(
            "fixed_rate",
            100_000,
            r#"{"annual_rate_bps": 3650, "compounding": "simple"}"#,
        );
        seed(&conn, &inv, &[("deposit", 50_000, "2026-06-01")]);
        let as_of = NaiveDate::from_ymd_opt(2026, 4, 11).unwrap(); // before the deposit
        let value = find("fixed_rate")
            .unwrap()
            .value_at(&inv, &conn, as_of)
            .unwrap();
        assert_eq!(value, 110_000); // only the principal accrued
    }

    #[test]
    fn cetes_reinvest_compounds_per_plazo() {
        let conn = open_in_memory();
        // 10,000.00 at 10.80%, plazo 91, reinversión automática.
        // 182 days = two full plazos: (1 + 0.108·91/360)² = 1.0273² = 1.05534529
        // → 10,553.45
        let inv = test_investment(
            "cetes",
            1_000_000,
            r#"{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 0, "reinvest": true}"#,
        );
        seed(&conn, &inv, &[]);
        let as_of = NaiveDate::from_ymd_opt(2026, 7, 2).unwrap();
        let value = find("cetes").unwrap().value_at(&inv, &conn, as_of).unwrap();
        assert_eq!(value, 1_055_345);
        // rolling position has no fixed maturity
        assert_eq!(find("cetes").unwrap().maturity_date(&inv), None);
    }

    #[test]
    fn nu_with_monthly_deposits_and_withdrawal() {
        let conn = open_in_memory();
        // Mirrors the user's real scenario: monthly 1,000.00 deposits, then a
        // withdrawal, then nothing. Sanity bounds rather than exact values:
        // value must exceed net invested (positive yield) and stay below
        // gross-of-yield-on-everything upper bound.
        let inv = test_investment("nu_cajita", 100_000, r#"{"annual_rate_bps": 1500}"#);
        seed(
            &conn,
            &inv,
            &[
                ("deposit", 100_000, "2026-02-01"),
                ("deposit", 100_000, "2026-03-01"),
                ("deposit", 100_000, "2026-04-01"),
                ("withdrawal", 250_000, "2026-05-01"),
            ],
        );
        let as_of = NaiveDate::from_ymd_opt(2026, 6, 10).unwrap();
        let value = find("nu_cajita")
            .unwrap()
            .value_at(&inv, &conn, as_of)
            .unwrap();
        let net = net_invested(&conn, &inv, as_of).unwrap();
        assert_eq!(net, 150_000);
        assert!(
            value > net,
            "value {value} should exceed net invested {net}"
        );
        assert!(value < 170_000, "value {value} suspiciously high");
    }

    #[test]
    fn net_invested_sums_signed_movements() {
        let conn = open_in_memory();
        let inv = test_investment("nu_cajita", 100_000, r#"{"annual_rate_bps": 1500}"#);
        seed(
            &conn,
            &inv,
            &[
                ("deposit", 50_000, "2026-02-01"),
                ("withdrawal", 30_000, "2026-03-01"),
            ],
        );
        let as_of = NaiveDate::from_ymd_opt(2026, 6, 1).unwrap();
        assert_eq!(net_invested(&conn, &inv, as_of).unwrap(), 120_000);
        // before the withdrawal happened
        let earlier = NaiveDate::from_ymd_opt(2026, 2, 15).unwrap();
        assert_eq!(net_invested(&conn, &inv, earlier).unwrap(), 150_000);
    }
}
