//! Recurring subscriptions. Tracked per user; "register payment" books an
//! expense (reusing transactions::add_expense) into the linked wallet and
//! advances the next charge date by the cadence.

use chrono::NaiveDate;
use finanzas_core::error::{AppError, AppResult};
use finanzas_core::period::{resolve_period, Period};
use finanzas_core::subscription::count_charges;
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{convert, load_rates, to_mxn};
use super::transactions::{add_expense, SimpleTxArgs};
use crate::db::{all, changes, exec, first, last_row_id, today_mx};
use crate::jsv;

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct SubArgs {
    #[serde(default)]
    pub period: Period,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Subscription {
    pub id: i64,
    pub name: String,
    pub icon: Option<String>,
    pub color: Option<String>,
    pub amount_cents: i64,
    pub currency_code: String,
    pub cadence: String,
    pub next_charge_date: String,
    pub wallet_id: Option<i64>,
    pub category_id: Option<i64>,
    pub is_active: bool,
    /// Whether a charge for this subscription falls inside the selected period.
    pub charged_in_period: bool,
}

#[derive(Deserialize)]
struct SubRow {
    id: i64,
    name: String,
    icon: Option<String>,
    color: Option<String>,
    amount_cents: i64,
    currency_code: String,
    cadence: String,
    next_charge_date: String,
    wallet_id: Option<i64>,
    category_id: Option<i64>,
    is_active: i64,
    #[serde(default)]
    started_at: Option<String>,
    #[serde(default)]
    ended_at: Option<String>,
}

impl From<SubRow> for Subscription {
    fn from(r: SubRow) -> Self {
        Subscription {
            is_active: r.is_active != 0,
            charged_in_period: false,
            id: r.id,
            name: r.name,
            icon: r.icon,
            color: r.color,
            amount_cents: r.amount_cents,
            currency_code: r.currency_code,
            cadence: r.cadence,
            next_charge_date: r.next_charge_date,
            wallet_id: r.wallet_id,
            category_id: r.category_id,
        }
    }
}

const SELECT: &str = "SELECT id, name, icon, color, amount_cents, currency_code, cadence,
        next_charge_date, wallet_id, category_id, is_active FROM subscriptions";

async fn fetch_sub(db: &D1Database, uid: i64, id: i64) -> AppResult<Subscription> {
    let row: SubRow = first(
        db,
        &format!("{SELECT} WHERE id = ?1 AND user_id = ?2"),
        jsv![id, uid],
    )
    .await?
    .ok_or(AppError::NotFound("suscripción"))?;
    Ok(row.into())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SubscriptionList {
    pub subscriptions: Vec<Subscription>,
    /// Active subscriptions normalized to a monthly cost in MXN.
    pub monthly_total_mxn_cents: i64,
}

pub async fn list_subscriptions(
    db: &D1Database,
    uid: i64,
    a: SubArgs,
) -> AppResult<SubscriptionList> {
    let rates = load_rates(db, uid).await?;
    let resolved = resolve_period(&a.period, today_mx());
    let (start, end) = (resolved.start, resolved.end);

    // Every subscription stays in the list (stable layout + the manager view);
    // `charged_in_period` marks the ones that were actually charged during the
    // period — a charge occurrence (by cadence) that also falls inside the
    // active window. The total sums those charges (amount × occurrences).
    let rows: Vec<SubRow> = all(
        db,
        "SELECT id, name, icon, color, amount_cents, currency_code, cadence,
                next_charge_date, wallet_id, category_id, is_active, started_at, ended_at
         FROM subscriptions
         WHERE user_id = ?1
         ORDER BY next_charge_date",
        jsv![uid],
    )
    .await?;

    let parse = |s: &Option<String>| {
        s.as_deref()
            .and_then(|d| NaiveDate::parse_from_str(d, "%Y-%m-%d").ok())
    };
    let mut subscriptions = Vec::with_capacity(rows.len());
    let mut monthly_total_mxn_cents = 0i64;
    for r in rows {
        // Charge window = period ∩ active span.
        let lo = parse(&r.started_at).map_or(start, |d| d.max(start));
        let hi = parse(&r.ended_at).map_or(end, |d| d.min(end));
        let cadence_months = if r.cadence == "yearly" { 12 } else { 1 };
        let count = NaiveDate::parse_from_str(&r.next_charge_date, "%Y-%m-%d")
            .map(|next| count_charges(next, cadence_months, lo, hi))
            .unwrap_or(0);
        if count > 0 {
            monthly_total_mxn_cents += to_mxn(
                r.amount_cents * count,
                rates.get(&r.currency_code).copied().unwrap_or(0),
            );
        }
        let mut sub: Subscription = r.into();
        sub.charged_in_period = count > 0;
        subscriptions.push(sub);
    }
    // Charged-in-period first, then by next charge date (already roughly sorted).
    subscriptions.sort_by_key(|s| !s.charged_in_period);
    Ok(SubscriptionList {
        subscriptions,
        monthly_total_mxn_cents,
    })
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SubInput {
    pub name: String,
    pub icon: Option<String>,
    pub color: Option<String>,
    pub amount_cents: i64,
    pub currency_code: String,
    pub cadence: String, // 'monthly' | 'yearly'
    pub next_charge_date: String,
    pub wallet_id: Option<i64>,
    pub category_id: Option<i64>,
}

fn validate(a: &SubInput) -> AppResult<()> {
    if a.name.trim().is_empty() {
        return Err(AppError::InvalidInput("el nombre es obligatorio".into()));
    }
    if a.amount_cents <= 0 {
        return Err(AppError::InvalidInput("el monto debe ser positivo".into()));
    }
    if a.cadence != "monthly" && a.cadence != "yearly" {
        return Err(AppError::InvalidInput("cadencia inválida".into()));
    }
    chrono::NaiveDate::parse_from_str(&a.next_charge_date, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha inválida (YYYY-MM-DD)".into()))?;
    Ok(())
}

pub async fn create_subscription(
    db: &D1Database,
    uid: i64,
    a: SubInput,
) -> AppResult<Subscription> {
    validate(&a)?;
    let res = exec(
        db,
        "INSERT INTO subscriptions
           (user_id, name, icon, color, amount_cents, currency_code, cadence, next_charge_date, wallet_id, category_id, started_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, date('now'))",
        jsv![
            uid, a.name.trim(), a.icon, a.color, a.amount_cents, a.currency_code,
            a.cadence, a.next_charge_date, a.wallet_id, a.category_id
        ],
    )
    .await?;
    fetch_sub(db, uid, last_row_id(&res)?).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SubUpdate {
    pub id: i64,
    #[serde(flatten)]
    pub input: SubInput,
}

pub async fn update_subscription(
    db: &D1Database,
    uid: i64,
    a: SubUpdate,
) -> AppResult<Subscription> {
    validate(&a.input)?;
    let i = a.input;
    let res = exec(
        db,
        "UPDATE subscriptions SET name = ?3, icon = ?4, color = ?5, amount_cents = ?6,
            currency_code = ?7, cadence = ?8, next_charge_date = ?9, wallet_id = ?10, category_id = ?11
         WHERE id = ?1 AND user_id = ?2",
        jsv![
            a.id, uid, i.name.trim(), i.icon, i.color, i.amount_cents,
            i.currency_code, i.cadence, i.next_charge_date, i.wallet_id, i.category_id
        ],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("suscripción"));
    }
    fetch_sub(db, uid, a.id).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SetActiveArgs {
    pub id: i64,
    pub active: bool,
}

pub async fn set_subscription_active(
    db: &D1Database,
    uid: i64,
    a: SetActiveArgs,
) -> AppResult<Subscription> {
    // Maintain the active window: reactivating opens a new span, cancelling
    // closes the current one (so past periods still count it as active).
    let sql = if a.active {
        "UPDATE subscriptions SET is_active = 1, started_at = date('now'), ended_at = NULL
         WHERE id = ?1 AND user_id = ?2"
    } else {
        "UPDATE subscriptions SET is_active = 0, ended_at = date('now')
         WHERE id = ?1 AND user_id = ?2"
    };
    let res = exec(db, sql, jsv![a.id, uid]).await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("suscripción"));
    }
    fetch_sub(db, uid, a.id).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

pub async fn delete_subscription(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let res = exec(
        db,
        "DELETE FROM subscriptions WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("suscripción"));
    }
    Ok(())
}

/// Book this period's charge as an expense in the linked wallet, then move the
/// next charge date forward by the cadence.
pub async fn register_subscription_payment(
    db: &D1Database,
    uid: i64,
    a: IdArgs,
) -> AppResult<Subscription> {
    let sub = fetch_sub(db, uid, a.id).await?;
    let wallet_id = sub
        .wallet_id
        .ok_or_else(|| AppError::InvalidInput("la suscripción no tiene cartera asignada".into()))?;

    // Transactions are stored in the wallet's currency, so a USD subscription
    // charged to an MXN wallet must be converted first.
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
    let rates = load_rates(db, uid).await?;
    let amount_cents = convert(
        sub.amount_cents,
        &sub.currency_code,
        &wallet.currency_code,
        &rates,
    )?;

    add_expense(
        db,
        uid,
        SimpleTxArgs {
            wallet_id,
            amount_cents,
            category_id: sub.category_id,
            description: Some(sub.name.clone()),
            occurred_at: today_mx().format("%Y-%m-%d").to_string(),
            client_id: None,
        },
    )
    .await?;

    // Allowlisted SQL modifier (never user text): cadence only selects a literal.
    let step = if sub.cadence == "yearly" {
        "+1 year"
    } else {
        "+1 month"
    };
    exec(
        db,
        &format!(
            "UPDATE subscriptions SET next_charge_date = date(next_charge_date, '{step}')
             WHERE id = ?1 AND user_id = ?2"
        ),
        jsv![a.id, uid],
    )
    .await?;
    fetch_sub(db, uid, a.id).await
}
