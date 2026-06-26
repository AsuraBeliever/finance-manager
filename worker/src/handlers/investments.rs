//! Port of src-tauri/src/commands/investments.rs, scoped by user_id.
//! Money math stays in finanzas-core; this side only loads CalcContext from D1.

use chrono::{Duration, NaiveDate};
use finanzas_core::error::{AppError, AppResult};
use finanzas_core::investments::simulate::{simulate, Cadence, SimulationInput};
use finanzas_core::investments::{
    find, net_invested, parse_bonddia_price, registry, CalcContext, Movement, Snapshot,
};
use finanzas_core::models::{Investment, InvestmentMovement, InvestmentSnapshot};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use crate::db::{all, batch, changes, exec, first, last_row_id, stmt, today_mx, ValueRow};
use crate::jsv;

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

#[derive(Deserialize)]
struct InvestmentRow {
    id: i64,
    name: String,
    calculator: String,
    currency_code: String,
    principal_cents: i64,
    start_date: String,
    params_json: String,
    linked_wallet_id: Option<i64>,
    is_closed: i64,
    notes: Option<String>,
    created_at: String,
}

impl From<InvestmentRow> for Investment {
    fn from(r: InvestmentRow) -> Self {
        Investment {
            id: r.id,
            name: r.name,
            calculator: r.calculator,
            currency_code: r.currency_code,
            principal_cents: r.principal_cents,
            start_date: r.start_date,
            params_json: r.params_json,
            linked_wallet_id: r.linked_wallet_id,
            is_closed: r.is_closed != 0,
            notes: r.notes,
            created_at: r.created_at,
        }
    }
}

const INVESTMENT_SELECT: &str = "
    SELECT id, name, calculator, currency_code, principal_cents, start_date,
           params_json, linked_wallet_id, is_closed, notes, created_at
    FROM investments";

async fn fetch_investment(db: &D1Database, uid: i64, id: i64) -> AppResult<Investment> {
    let sql = format!("{INVESTMENT_SELECT} WHERE id = ?1 AND user_id = ?2");
    first::<InvestmentRow>(db, &sql, jsv![id, uid])
        .await?
        .map(Into::into)
        .ok_or(AppError::NotFound("inversión"))
}

#[derive(Deserialize)]
struct MovementRow {
    kind: String,
    amount_cents: i64,
    occurred_at: String,
}

#[derive(Deserialize)]
struct RateRow {
    date: String,
    rate_bps: i64,
}

#[derive(Deserialize)]
struct PriceRow {
    price_mxn_cents: i64,
}

#[derive(Deserialize)]
struct SnapValueRow {
    value_cents: i64,
    as_of: String,
}

fn parse_date(s: &str, what: &str) -> AppResult<NaiveDate> {
    NaiveDate::parse_from_str(s, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput(format!("fecha de {what} inválida")))
}

/// Preload the stored data this investment's calculator reads into the
/// storage-agnostic context consumed by finanzas-core. Mirrors the desktop
/// loader (src-tauri/src/commands/investments.rs::load_calc_context).
async fn load_calc_context(db: &D1Database, inv: &Investment) -> AppResult<CalcContext> {
    let rows: Vec<MovementRow> = all(
        db,
        "SELECT kind, amount_cents, occurred_at FROM investment_movements
         WHERE investment_id = ?1 ORDER BY occurred_at, id",
        jsv![inv.id],
    )
    .await?;
    let mut ctx = CalcContext {
        movements: rows
            .into_iter()
            .map(|r| {
                Ok(Movement {
                    kind: r.kind,
                    amount_cents: r.amount_cents,
                    occurred_at: parse_date(&r.occurred_at, "movimiento")?,
                })
            })
            .collect::<AppResult<Vec<_>>>()?,
        ..Default::default()
    };
    match inv.calculator.as_str() {
        "bonddia" => {
            let rows: Vec<RateRow> = all(
                db,
                "SELECT date, rate_bps FROM rate_history WHERE series = 'objetivo' ORDER BY date",
                vec![],
            )
            .await?;
            ctx.rate_history = rows
                .into_iter()
                .filter_map(|r| {
                    NaiveDate::parse_from_str(&r.date, "%Y-%m-%d")
                        .ok()
                        .map(|d| (d, r.rate_bps))
                })
                .collect();
            // global market cache rows live under the system user (id 0)
            let raw: Option<ValueRow> = first(
                db,
                "SELECT value FROM settings WHERE user_id = 0 AND key = 'bonddia_price'",
                vec![],
            )
            .await?;
            ctx.bonddia_price_micros = raw.and_then(|r| parse_bonddia_price(&r.value));
        }
        "crypto" => {
            let symbol = serde_json::from_str::<serde_json::Value>(&inv.params_json)
                .ok()
                .and_then(|p| p.get("symbol").and_then(|v| v.as_str()).map(str::to_owned));
            if let Some(symbol) = symbol {
                let row: Option<PriceRow> = first(
                    db,
                    "SELECT price_mxn_cents FROM crypto_prices WHERE symbol = ?1",
                    jsv![symbol],
                )
                .await?;
                ctx.crypto_price_cents = row.map(|r| r.price_mxn_cents);
            }
        }
        "manual" => {
            let rows: Vec<SnapValueRow> = all(
                db,
                "SELECT value_cents, as_of FROM investment_snapshots
                 WHERE investment_id = ?1 ORDER BY as_of, id",
                jsv![inv.id],
            )
            .await?;
            ctx.snapshots = rows
                .into_iter()
                .filter_map(|r| {
                    NaiveDate::parse_from_str(&r.as_of, "%Y-%m-%d")
                        .ok()
                        .map(|as_of| Snapshot {
                            value_cents: r.value_cents,
                            as_of,
                        })
                })
                .collect();
        }
        _ => {}
    }
    Ok(ctx)
}

async fn with_value(
    db: &D1Database,
    inv: Investment,
    as_of: NaiveDate,
) -> AppResult<InvestmentWithValue> {
    let calc = find(&inv.calculator)?;
    let ctx = load_calc_context(db, &inv).await?;
    let current_value_cents = calc.value_at(&inv, &ctx, as_of)?;
    let net_invested_cents = net_invested(&inv, &ctx, as_of);
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
    parse_date(start_date, "inicio")?;
    serde_json::from_str::<serde_json::Value>(params_json)
        .map_err(|e| AppError::InvalidInput(format!("parámetros inválidos: {e}")))?;
    Ok(())
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentSlice {
    pub id: i64,
    pub name: String,
    pub value_mxn_cents: i64,
}

/// Current value of every open investment converted to MXN with the given
/// rate lookup. Used by the dashboard (total + donut slices).
pub async fn open_investments_mxn(
    db: &D1Database,
    uid: i64,
    rates: &std::collections::HashMap<String, i64>,
    as_of: NaiveDate,
) -> AppResult<Vec<InvestmentSlice>> {
    let sql = format!("{INVESTMENT_SELECT} WHERE is_closed = 0 AND user_id = ?1");
    let invs: Vec<InvestmentRow> = all(db, &sql, jsv![uid]).await?;
    let mut slices = Vec::new();
    for row in invs {
        let inv: Investment = row.into();
        // Skip investments that hadn't started yet at the as-of date (historical
        // views): they shouldn't inflate a past net worth.
        if NaiveDate::parse_from_str(&inv.start_date, "%Y-%m-%d")
            .map(|d| d > as_of)
            .unwrap_or(false)
        {
            continue;
        }
        let ctx = load_calc_context(db, &inv).await?;
        let value = find(&inv.calculator)?.value_at(&inv, &ctx, as_of)?;
        if let Some(rate) = rates.get(&inv.currency_code) {
            slices.push(InvestmentSlice {
                id: inv.id,
                name: inv.name,
                value_mxn_cents: ((value as i128 * *rate as i128) / 1_000_000i128) as i64,
            });
        }
    }
    slices.sort_by_key(|s| std::cmp::Reverse(s.value_mxn_cents));
    Ok(slices)
}

// ---- investment catalog ----

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CatalogItem {
    /// Stable id the frontend maps to a label/description (i18n).
    pub id: &'static str,
    pub calculator: &'static str,
    /// Prefilled params for the form; the rate is merged in when available.
    pub params_json: String,
    pub rate_bps: Option<i64>,
    pub rate_date: Option<String>,
}

/// Static catalog: (id, calculator, banxico kind for the live rate, base params).
const CATALOG: &[(&str, &str, Option<&str>, &str)] = &[
    (
        "cetes_28",
        "cetes",
        Some("cetes_28"),
        r#"{"plazo_days":28,"isr_rate_bps":0,"reinvest":false}"#,
    ),
    (
        "cetes_91",
        "cetes",
        Some("cetes_91"),
        r#"{"plazo_days":91,"isr_rate_bps":0,"reinvest":false}"#,
    ),
    (
        "cetes_182",
        "cetes",
        Some("cetes_182"),
        r#"{"plazo_days":182,"isr_rate_bps":0,"reinvest":false}"#,
    ),
    (
        "cetes_364",
        "cetes",
        Some("cetes_364"),
        r#"{"plazo_days":364,"isr_rate_bps":0,"reinvest":false}"#,
    ),
    // bonddia compounds over the cached historical target-rate series; the
    // live rate merged here is only the offline fallback + catalog badge.
    ("bonddia", "bonddia", Some("objetivo"), "{}"),
    ("nu_cajita", "nu_cajita", None, "{}"),
    ("crypto", "crypto", None, "{}"),
    (
        "fixed_rate",
        "fixed_rate",
        None,
        r#"{"compounding":"daily"}"#,
    ),
    ("manual", "manual", None, "{}"),
];

/// Catalog of known investment products with live Banxico rates where a
/// public source exists. Rate fetch failures degrade to None (the user can
/// still type the rate), so the picker keeps working offline.
pub async fn get_investment_catalog() -> Vec<CatalogItem> {
    let mut items = Vec::with_capacity(CATALOG.len());
    for (id, calculator, banxico_kind, base_params) in CATALOG {
        let rate = match banxico_kind {
            Some(kind) => crate::market::fetch_rate_tokenless(kind).await.ok(),
            None => None,
        };
        let mut params: serde_json::Value =
            serde_json::from_str(base_params).expect("static catalog params are valid JSON");
        if let Some(r) = &rate {
            params["annual_rate_bps"] = serde_json::json!(r.rate_bps);
        }
        items.push(CatalogItem {
            id,
            calculator,
            params_json: params.to_string(),
            rate_bps: rate.as_ref().map(|r| r.rate_bps),
            rate_date: rate.map(|r| r.date),
        });
    }
    items
}

// ---- commands ----

pub fn list_calculators() -> Vec<&'static str> {
    registry().iter().map(|c| c.id()).collect()
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListInvestmentsArgs {
    pub include_closed: Option<bool>,
}

pub async fn list_investments(
    db: &D1Database,
    uid: i64,
    a: ListInvestmentsArgs,
) -> AppResult<Vec<InvestmentWithValue>> {
    let filter = if a.include_closed.unwrap_or(false) {
        ""
    } else {
        " AND is_closed = 0"
    };
    let sql = format!("{INVESTMENT_SELECT} WHERE user_id = ?1{filter} ORDER BY created_at, id");
    let rows: Vec<InvestmentRow> = all(db, &sql, jsv![uid]).await?;
    let as_of = today_mx();
    let mut out = Vec::with_capacity(rows.len());
    for row in rows {
        out.push(with_value(db, row.into(), as_of).await?);
    }
    Ok(out)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateInvestmentArgs {
    pub name: String,
    pub calculator: String,
    pub currency_code: String,
    pub principal_cents: i64,
    pub start_date: String,
    pub params_json: String,
    pub linked_wallet_id: Option<i64>,
    pub notes: Option<String>,
}

pub async fn create_investment(
    db: &D1Database,
    uid: i64,
    a: CreateInvestmentArgs,
) -> AppResult<InvestmentWithValue> {
    if a.name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    validate_input(
        &a.calculator,
        a.principal_cents,
        &a.start_date,
        &a.params_json,
    )?;
    let res = exec(
        db,
        "INSERT INTO investments (user_id, name, calculator, currency_code, principal_cents, start_date, params_json, linked_wallet_id, notes)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
        jsv![uid, a.name.trim(), a.calculator, a.currency_code, a.principal_cents, a.start_date, a.params_json, a.linked_wallet_id, a.notes],
    )
    .await?;
    let inv = fetch_investment(db, uid, last_row_id(&res)?).await?;
    with_value(db, inv, today_mx()).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateInvestmentArgs {
    pub id: i64,
    pub name: String,
    pub currency_code: String,
    pub principal_cents: i64,
    pub start_date: String,
    pub params_json: String,
    pub linked_wallet_id: Option<i64>,
    pub notes: Option<String>,
}

pub async fn update_investment(
    db: &D1Database,
    uid: i64,
    a: UpdateInvestmentArgs,
) -> AppResult<InvestmentWithValue> {
    if a.name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    let existing = fetch_investment(db, uid, a.id).await?;
    validate_input(
        &existing.calculator,
        a.principal_cents,
        &a.start_date,
        &a.params_json,
    )?;
    exec(
        db,
        "UPDATE investments
         SET name = ?3, currency_code = ?4, principal_cents = ?5, start_date = ?6,
             params_json = ?7, linked_wallet_id = ?8, notes = ?9
         WHERE id = ?1 AND user_id = ?2",
        jsv![
            a.id,
            uid,
            a.name.trim(),
            a.currency_code,
            a.principal_cents,
            a.start_date,
            a.params_json,
            a.linked_wallet_id,
            a.notes
        ],
    )
    .await?;
    let inv = fetch_investment(db, uid, a.id).await?;
    with_value(db, inv, today_mx()).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CloseInvestmentArgs {
    pub id: i64,
    pub closed: bool,
}

pub async fn close_investment(db: &D1Database, uid: i64, a: CloseInvestmentArgs) -> AppResult<()> {
    let res = exec(
        db,
        "UPDATE investments SET is_closed = ?3 WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, a.closed],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("inversión"));
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

pub async fn delete_investment(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let res = exec(
        db,
        "DELETE FROM investments WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("inversión"));
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AddSnapshotArgs {
    pub investment_id: i64,
    pub value_cents: i64,
    pub as_of: String,
}

pub async fn add_snapshot(db: &D1Database, uid: i64, a: AddSnapshotArgs) -> AppResult<()> {
    if a.value_cents < 0 {
        return Err(AppError::InvalidInput(
            "el valor no puede ser negativo".into(),
        ));
    }
    parse_date(&a.as_of, "snapshot")?;
    fetch_investment(db, uid, a.investment_id).await?;
    exec(
        db,
        "INSERT INTO investment_snapshots (investment_id, value_cents, as_of)
         VALUES (?1, ?2, ?3)",
        jsv![a.investment_id, a.value_cents, a.as_of],
    )
    .await?;
    Ok(())
}

#[derive(Deserialize)]
struct SnapshotRow {
    id: i64,
    investment_id: i64,
    value_cents: i64,
    as_of: String,
    source: String,
}

#[derive(Deserialize)]
struct FullMovementRow {
    id: i64,
    investment_id: i64,
    kind: String,
    amount_cents: i64,
    occurred_at: String,
}

pub async fn get_investment_detail(
    db: &D1Database,
    uid: i64,
    a: IdArgs,
) -> AppResult<InvestmentDetail> {
    let inv = fetch_investment(db, uid, a.id).await?;
    let calc = find(&inv.calculator)?;
    let as_of = today_mx();

    let start = parse_date(&inv.start_date, "inicio")?;
    let end = calc
        .maturity_date(&inv)
        .unwrap_or(as_of.max(start) + Duration::days(365));

    // Weekly points from start to maturity (or +1 year), endpoint included.
    // One context load serves every projection point.
    let ctx = load_calc_context(db, &inv).await?;
    let mut projection = Vec::new();
    let mut d = start;
    while d < end {
        projection.push(ProjectionPoint {
            date: d.format("%Y-%m-%d").to_string(),
            value_cents: calc.value_at(&inv, &ctx, d)?,
        });
        d += Duration::days(7);
    }
    projection.push(ProjectionPoint {
        date: end.format("%Y-%m-%d").to_string(),
        value_cents: calc.value_at(&inv, &ctx, end)?,
    });

    let snapshots: Vec<SnapshotRow> = all(
        db,
        "SELECT id, investment_id, value_cents, as_of, source
         FROM investment_snapshots WHERE investment_id = ?1
         ORDER BY as_of DESC, id DESC",
        jsv![a.id],
    )
    .await?;
    let movements: Vec<FullMovementRow> = all(
        db,
        "SELECT id, investment_id, kind, amount_cents, occurred_at
         FROM investment_movements WHERE investment_id = ?1
         ORDER BY occurred_at DESC, id DESC",
        jsv![a.id],
    )
    .await?;

    Ok(InvestmentDetail {
        with_value: with_value(db, inv, as_of).await?,
        projection,
        snapshots: snapshots
            .into_iter()
            .map(|r| InvestmentSnapshot {
                id: r.id,
                investment_id: r.investment_id,
                value_cents: r.value_cents,
                as_of: r.as_of,
                source: r.source,
            })
            .collect(),
        movements: movements
            .into_iter()
            .map(|r| InvestmentMovement {
                id: r.id,
                investment_id: r.investment_id,
                kind: r.kind,
                amount_cents: r.amount_cents,
                occurred_at: r.occurred_at,
            })
            .collect(),
    })
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AddMovementArgs {
    pub investment_id: i64,
    pub kind: String,
    pub amount_cents: i64,
    pub occurred_at: String,
    /// Optional wallet the money moves from (deposit) or into (withdrawal). When
    /// set, the matching income/expense posts on that wallet — converted to its
    /// currency — atomically with the movement, and the wallet is remembered as
    /// this investment's default. None = external move with no wallet side.
    pub wallet_id: Option<i64>,
}

pub async fn add_investment_movement(
    db: &D1Database,
    uid: i64,
    a: AddMovementArgs,
) -> AppResult<()> {
    if a.kind != "deposit" && a.kind != "withdrawal" {
        return Err(AppError::InvalidInput("tipo de movimiento inválido".into()));
    }
    if a.amount_cents <= 0 {
        return Err(AppError::InvalidInput("el monto debe ser positivo".into()));
    }
    let date = parse_date(&a.occurred_at, "movimiento")?;
    let inv = fetch_investment(db, uid, a.investment_id).await?;
    if inv.calculator == "manual" {
        return Err(AppError::InvalidInput(
            "las inversiones de valor manual se actualizan con snapshots, no con movimientos"
                .into(),
        ));
    }
    let start = parse_date(&inv.start_date, "inicio")?;
    if date < start {
        return Err(AppError::InvalidInput(
            "el movimiento no puede ser anterior a la fecha de inicio".into(),
        ));
    }

    let Some(wallet_id) = a.wallet_id else {
        // No wallet side: external contribution/withdrawal (e.g. the very first
        // deposit before any wallet exists). Just record the movement.
        exec(
            db,
            "INSERT INTO investment_movements (investment_id, kind, amount_cents, occurred_at)
             VALUES (?1, ?2, ?3, ?4)",
            jsv![a.investment_id, a.kind, a.amount_cents, a.occurred_at],
        )
        .await?;
        return Ok(());
    };

    // Wallet side: a deposit leaves the wallet (expense), a withdrawal returns to
    // it (income), in the wallet's own currency. The transaction, the movement
    // (linked to it) and the remembered default wallet all post in one batch so
    // money never half-moves. last_insert_rowid() carries the just-inserted
    // transaction id within the batch transaction.
    #[derive(Deserialize)]
    struct CurrencyRow {
        currency_code: String,
    }
    let wallet: CurrencyRow = first(
        db,
        "SELECT currency_code FROM wallets WHERE id = ?1 AND user_id = ?2",
        jsv![wallet_id, uid],
    )
    .await?
    .ok_or(AppError::NotFound("cartera"))?;

    let rates = super::dashboard::load_rates(db, uid).await?;
    let wallet_amount = super::dashboard::convert(
        a.amount_cents,
        &inv.currency_code,
        &wallet.currency_code,
        &rates,
    )?;

    let (tx_kind, description) = if a.kind == "deposit" {
        ("expense", format!("Aporte a {}", inv.name))
    } else {
        ("income", format!("Retiro de {}", inv.name))
    };

    batch(
        db,
        vec![
            stmt(
                db,
                "INSERT INTO transactions (wallet_id, kind, amount_cents, description, occurred_at)
                 VALUES (?1, ?2, ?3, ?4, ?5)",
                jsv![
                    wallet_id,
                    tx_kind,
                    wallet_amount,
                    description,
                    a.occurred_at
                ],
            )?,
            stmt(
                db,
                "INSERT INTO investment_movements
                   (investment_id, kind, amount_cents, occurred_at, linked_transaction_id)
                 VALUES (?1, ?2, ?3, ?4, last_insert_rowid())",
                jsv![a.investment_id, a.kind, a.amount_cents, a.occurred_at],
            )?,
            stmt(
                db,
                "UPDATE investments SET linked_wallet_id = ?1 WHERE id = ?2 AND user_id = ?3",
                jsv![wallet_id, a.investment_id, uid],
            )?,
        ],
    )
    .await?;
    Ok(())
}

pub async fn delete_investment_movement(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    // Ownership check + grab the linked transaction (if any) in one lookup.
    #[derive(Deserialize)]
    struct MovementLinkRow {
        linked_transaction_id: Option<i64>,
    }
    let row: Option<MovementLinkRow> = first(
        db,
        "SELECT m.linked_transaction_id FROM investment_movements m
         JOIN investments i ON i.id = m.investment_id AND i.user_id = ?2
         WHERE m.id = ?1",
        jsv![a.id, uid],
    )
    .await?;
    let row = row.ok_or(AppError::NotFound("movimiento"))?;

    match row.linked_transaction_id {
        // Drop the wallet transaction too so the money returns; both in one
        // batch. (Deleting the tx would cascade-delete the movement anyway, but
        // being explicit keeps the intent clear and order-independent.)
        Some(tx_id) => {
            batch(
                db,
                vec![
                    stmt(
                        db,
                        "DELETE FROM investment_movements WHERE id = ?1",
                        jsv![a.id],
                    )?,
                    stmt(db, "DELETE FROM transactions WHERE id = ?1", jsv![tx_id])?,
                ],
            )
            .await?;
        }
        None => {
            exec(
                db,
                "DELETE FROM investment_movements WHERE id = ?1",
                jsv![a.id],
            )
            .await?;
        }
    }
    Ok(())
}

// ---- forward simulator ("¿cuánto crecería?") ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimulateArgs {
    pub initial_cents: i64,
    #[serde(default)]
    pub contribution_cents: i64,
    /// "monthly" | "biweekly" | "weekly" | "none"
    pub cadence: String,
    pub annual_rate_bps: i64,
    pub months: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SimPoint {
    pub month: i64,
    pub contributed_cents: i64,
    pub value_cents: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SimResult {
    pub points: Vec<SimPoint>,
    pub final_value_cents: i64,
    pub total_contributed_cents: i64,
    pub total_interest_cents: i64,
}

/// Pure forward projection — no DB. Powers the Simulator UI.
pub fn simulate_investment(a: SimulateArgs) -> AppResult<SimResult> {
    let cadence = match a.cadence.as_str() {
        "monthly" => Cadence::Monthly,
        "biweekly" => Cadence::Biweekly,
        "weekly" => Cadence::Weekly,
        _ => Cadence::None,
    };
    let r = simulate(&SimulationInput {
        initial_cents: a.initial_cents,
        contribution_cents: a.contribution_cents,
        cadence,
        annual_rate_bps: a.annual_rate_bps,
        months: a.months,
    })?;
    Ok(SimResult {
        points: r
            .points
            .into_iter()
            .map(|p| SimPoint {
                month: p.month,
                contributed_cents: p.contributed_cents,
                value_cents: p.value_cents,
            })
            .collect(),
        final_value_cents: r.final_value_cents,
        total_contributed_cents: r.total_contributed_cents,
        total_interest_cents: r.total_interest_cents,
    })
}
