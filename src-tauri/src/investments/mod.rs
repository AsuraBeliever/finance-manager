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
