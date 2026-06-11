use chrono::{Duration, Local, NaiveDate};
use rusqlite::{params, Connection, Row};
use serde::Serialize;
use tauri::State;

use crate::db::Db;
use crate::error::{AppError, AppResult};
use crate::investments::{find, net_invested, registry};
use crate::models::{Investment, InvestmentMovement, InvestmentSnapshot};

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentWithValue {
    #[serde(flatten)]
    pub investment: Investment,
    pub current_value_cents: i64,
    /// principal + deposits − withdrawals up to today
    pub net_invested_cents: i64,
    /// current value − net invested: realized + unrealized yield
    pub gain_cents: i64,
    pub maturity_date: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProjectionPoint {
    pub date: String,
    pub value_cents: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentDetail {
    #[serde(flatten)]
    pub with_value: InvestmentWithValue,
    pub projection: Vec<ProjectionPoint>,
    pub snapshots: Vec<InvestmentSnapshot>,
    pub movements: Vec<InvestmentMovement>,
}

fn investment_from_row(r: &Row) -> rusqlite::Result<Investment> {
    Ok(Investment {
        id: r.get("id")?,
        name: r.get("name")?,
        calculator: r.get("calculator")?,
        currency_code: r.get("currency_code")?,
        principal_cents: r.get("principal_cents")?,
        start_date: r.get("start_date")?,
        params_json: r.get("params_json")?,
        linked_wallet_id: r.get("linked_wallet_id")?,
        is_closed: r.get::<_, i64>("is_closed")? != 0,
        notes: r.get("notes")?,
        created_at: r.get("created_at")?,
    })
}

const INVESTMENT_SELECT: &str = "
    SELECT id, name, calculator, currency_code, principal_cents, start_date,
           params_json, linked_wallet_id, is_closed, notes, created_at
    FROM investments";

fn fetch_investment(conn: &Connection, id: i64) -> AppResult<Investment> {
    let sql = format!("{INVESTMENT_SELECT} WHERE id = ?1");
    conn.query_row(&sql, [id], investment_from_row)
        .map_err(|e| match e {
            rusqlite::Error::QueryReturnedNoRows => AppError::NotFound("inversión"),
            other => other.into(),
        })
}

fn today() -> NaiveDate {
    Local::now().date_naive()
}

pub fn with_value(
    conn: &Connection,
    inv: Investment,
    as_of: NaiveDate,
) -> AppResult<InvestmentWithValue> {
    let calc = find(&inv.calculator)?;
    let current_value_cents = calc.value_at(&inv, conn, as_of)?;
    let net_invested_cents = net_invested(conn, &inv, as_of)?;
    let maturity_date = calc
        .maturity_date(&inv)
        .map(|d| d.format("%Y-%m-%d").to_string());
    Ok(InvestmentWithValue {
        gain_cents: current_value_cents - net_invested_cents,
        current_value_cents,
        net_invested_cents,
        maturity_date,
        investment: inv,
    })
}

fn validate_input(
    calculator: &str,
    principal_cents: i64,
    start_date: &str,
    params_json: &str,
) -> AppResult<()> {
    find(calculator)?;
    if principal_cents <= 0 {
        return Err(AppError::InvalidInput(
            "el monto invertido debe ser positivo".into(),
        ));
    }
    NaiveDate::parse_from_str(start_date, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha inválida (se espera YYYY-MM-DD)".into()))?;
    serde_json::from_str::<serde_json::Value>(params_json)
        .map_err(|e| AppError::InvalidInput(format!("parámetros inválidos: {e}")))?;
    Ok(())
}

/// Total current value of open investments converted to MXN with the given
/// rate lookup. Used by the dashboard.
pub fn open_total_mxn(
    conn: &Connection,
    rates: &std::collections::HashMap<String, i64>,
    as_of: NaiveDate,
) -> AppResult<i64> {
    let mut stmt = conn.prepare(&format!("{INVESTMENT_SELECT} WHERE is_closed = 0"))?;
    let invs = stmt
        .query_map([], investment_from_row)?
        .collect::<Result<Vec<_>, _>>()?;
    let mut total: i64 = 0;
    for inv in invs {
        let value = find(&inv.calculator)?.value_at(&inv, conn, as_of)?;
        if let Some(rate) = rates.get(&inv.currency_code) {
            total += ((value as i128 * *rate as i128) / 1_000_000i128) as i64;
        }
    }
    Ok(total)
}

// ---- commands ----

#[tauri::command]
pub fn list_calculators() -> Vec<&'static str> {
    registry().iter().map(|c| c.id()).collect()
}

#[tauri::command]
pub fn list_investments(
    db: State<Db>,
    include_closed: Option<bool>,
) -> AppResult<Vec<InvestmentWithValue>> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let filter = if include_closed.unwrap_or(false) {
        ""
    } else {
        " WHERE is_closed = 0"
    };
    let sql = format!("{INVESTMENT_SELECT}{filter} ORDER BY created_at, id");
    let mut stmt = conn.prepare(&sql)?;
    let invs = stmt
        .query_map([], investment_from_row)?
        .collect::<Result<Vec<_>, _>>()?;
    let as_of = today();
    invs.into_iter()
        .map(|inv| with_value(&conn, inv, as_of))
        .collect()
}

#[tauri::command]
#[allow(clippy::too_many_arguments)]
pub fn create_investment(
    db: State<Db>,
    name: String,
    calculator: String,
    currency_code: String,
    principal_cents: i64,
    start_date: String,
    params_json: String,
    linked_wallet_id: Option<i64>,
    notes: Option<String>,
) -> AppResult<InvestmentWithValue> {
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    validate_input(&calculator, principal_cents, &start_date, &params_json)?;
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    conn.execute(
        "INSERT INTO investments (name, calculator, currency_code, principal_cents, start_date, params_json, linked_wallet_id, notes)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
        params![name.trim(), calculator, currency_code, principal_cents, start_date, params_json, linked_wallet_id, notes],
    )?;
    let inv = fetch_investment(&conn, conn.last_insert_rowid())?;
    with_value(&conn, inv, today())
}

#[tauri::command]
#[allow(clippy::too_many_arguments)]
pub fn update_investment(
    db: State<Db>,
    id: i64,
    name: String,
    currency_code: String,
    principal_cents: i64,
    start_date: String,
    params_json: String,
    linked_wallet_id: Option<i64>,
    notes: Option<String>,
) -> AppResult<InvestmentWithValue> {
    if name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let existing = fetch_investment(&conn, id)?;
    validate_input(
        &existing.calculator,
        principal_cents,
        &start_date,
        &params_json,
    )?;
    conn.execute(
        "UPDATE investments
         SET name = ?2, currency_code = ?3, principal_cents = ?4, start_date = ?5,
             params_json = ?6, linked_wallet_id = ?7, notes = ?8
         WHERE id = ?1",
        params![
            id,
            name.trim(),
            currency_code,
            principal_cents,
            start_date,
            params_json,
            linked_wallet_id,
            notes
        ],
    )?;
    let inv = fetch_investment(&conn, id)?;
    with_value(&conn, inv, today())
}

#[tauri::command]
pub fn close_investment(db: State<Db>, id: i64, closed: bool) -> AppResult<()> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let updated = conn.execute(
        "UPDATE investments SET is_closed = ?2 WHERE id = ?1",
        params![id, closed as i64],
    )?;
    if updated == 0 {
        return Err(AppError::NotFound("inversión"));
    }
    Ok(())
}

#[tauri::command]
pub fn delete_investment(db: State<Db>, id: i64) -> AppResult<()> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let deleted = conn.execute("DELETE FROM investments WHERE id = ?1", [id])?;
    if deleted == 0 {
        return Err(AppError::NotFound("inversión"));
    }
    Ok(())
}

#[tauri::command]
pub fn add_snapshot(
    db: State<Db>,
    investment_id: i64,
    value_cents: i64,
    as_of: String,
) -> AppResult<()> {
    if value_cents < 0 {
        return Err(AppError::InvalidInput(
            "el valor no puede ser negativo".into(),
        ));
    }
    NaiveDate::parse_from_str(&as_of, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha inválida (se espera YYYY-MM-DD)".into()))?;
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    fetch_investment(&conn, investment_id)?;
    conn.execute(
        "INSERT INTO investment_snapshots (investment_id, value_cents, as_of)
         VALUES (?1, ?2, ?3)",
        params![investment_id, value_cents, as_of],
    )?;
    Ok(())
}

#[tauri::command]
pub fn get_investment_detail(db: State<Db>, id: i64) -> AppResult<InvestmentDetail> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let inv = fetch_investment(&conn, id)?;
    let calc = find(&inv.calculator)?;
    let as_of = today();

    let start = NaiveDate::parse_from_str(&inv.start_date, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha de inicio inválida".into()))?;
    let end = calc
        .maturity_date(&inv)
        .unwrap_or(as_of.max(start) + Duration::days(365));

    // Weekly points from start to maturity (or +1 year), endpoint included.
    let mut projection = Vec::new();
    let mut d = start;
    while d < end {
        projection.push(ProjectionPoint {
            date: d.format("%Y-%m-%d").to_string(),
            value_cents: calc.value_at(&inv, &conn, d)?,
        });
        d += Duration::days(7);
    }
    projection.push(ProjectionPoint {
        date: end.format("%Y-%m-%d").to_string(),
        value_cents: calc.value_at(&inv, &conn, end)?,
    });

    let mut stmt = conn.prepare(
        "SELECT id, investment_id, value_cents, as_of, source
         FROM investment_snapshots WHERE investment_id = ?1
         ORDER BY as_of DESC, id DESC",
    )?;
    let snapshots = stmt
        .query_map([id], |r| {
            Ok(InvestmentSnapshot {
                id: r.get(0)?,
                investment_id: r.get(1)?,
                value_cents: r.get(2)?,
                as_of: r.get(3)?,
                source: r.get(4)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;

    let mut stmt = conn.prepare(
        "SELECT id, investment_id, kind, amount_cents, occurred_at
         FROM investment_movements WHERE investment_id = ?1
         ORDER BY occurred_at DESC, id DESC",
    )?;
    let movements = stmt
        .query_map([id], |r| {
            Ok(InvestmentMovement {
                id: r.get(0)?,
                investment_id: r.get(1)?,
                kind: r.get(2)?,
                amount_cents: r.get(3)?,
                occurred_at: r.get(4)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;

    Ok(InvestmentDetail {
        with_value: with_value(&conn, inv, as_of)?,
        projection,
        snapshots,
        movements,
    })
}

#[tauri::command]
pub fn add_investment_movement(
    db: State<Db>,
    investment_id: i64,
    kind: String,
    amount_cents: i64,
    occurred_at: String,
) -> AppResult<()> {
    if kind != "deposit" && kind != "withdrawal" {
        return Err(AppError::InvalidInput("tipo de movimiento inválido".into()));
    }
    if amount_cents <= 0 {
        return Err(AppError::InvalidInput("el monto debe ser positivo".into()));
    }
    let date = NaiveDate::parse_from_str(&occurred_at, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha inválida (se espera YYYY-MM-DD)".into()))?;
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let inv = fetch_investment(&conn, investment_id)?;
    if inv.calculator == "manual" {
        return Err(AppError::InvalidInput(
            "las inversiones de valor manual se actualizan con snapshots, no con movimientos"
                .into(),
        ));
    }
    let start = NaiveDate::parse_from_str(&inv.start_date, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha de inicio inválida".into()))?;
    if date < start {
        return Err(AppError::InvalidInput(
            "el movimiento no puede ser anterior a la fecha de inicio".into(),
        ));
    }
    conn.execute(
        "INSERT INTO investment_movements (investment_id, kind, amount_cents, occurred_at)
         VALUES (?1, ?2, ?3, ?4)",
        params![investment_id, kind, amount_cents, occurred_at],
    )?;
    Ok(())
}

#[tauri::command]
pub fn delete_investment_movement(db: State<Db>, id: i64) -> AppResult<()> {
    let conn = db.0.lock().map_err(|e| AppError::Internal(e.to_string()))?;
    let deleted = conn.execute("DELETE FROM investment_movements WHERE id = ?1", [id])?;
    if deleted == 0 {
        return Err(AppError::NotFound("movimiento"));
    }
    Ok(())
}
