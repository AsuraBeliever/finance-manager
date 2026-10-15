//! Recurring subscriptions. Tracked per user; "register payment" books an
//! expense dated today (reusing transactions::add_expense) into the linked
//! wallet and advances the next charge date by the cadence. Subscriptions with
//! `auto_charge` are booked by the daily cron instead (`post_due_subscriptions`),
//! each charge dated on its own charge date.

use finanzas_core::error::{AppError, AppResult};
use finanzas_core::period::{resolve_period, Period};
use finanzas_core::subscription::{due_charges, next_after};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use super::dashboard::{convert, load_rates, to_mxn};
use super::transactions::{add_expense, SimpleTxArgs};
use crate::db::{all, batch, changes, exec, first, last_row_id, stmt, today_mx};
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
    /// Charged by the daily cron on each due date (vs. only by hand).
    pub auto_charge: bool,
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
    auto_charge: i64,
}

impl From<SubRow> for Subscription {
    fn from(r: SubRow) -> Self {
        Subscription {
            is_active: r.is_active != 0,
            auto_charge: r.auto_charge != 0,
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
        next_charge_date, wallet_id, category_id, is_active, auto_charge FROM subscriptions";

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
    let start = resolved.start.format("%Y-%m-%d").to_string();
    let end = resolved.end.format("%Y-%m-%d").to_string();

    // Every subscription stays in the list (stable layout + the manager view);
    // `charged_in_period` reflects REAL charges: an expense booked through
    // "register payment" (linked via transactions.subscription_id) whose date
    // falls in the period. We never project from the billing calendar, so a
    // subscription only counts once it was actually paid. The total sums those
    // real charges, each converted from its wallet's currency to MXN.
    let rows: Vec<SubRow> = all(
        db,
        &format!("{SELECT} WHERE user_id = ?1 ORDER BY next_charge_date"),
        jsv![uid],
    )
    .await?;

    // D1 returns the SQL column names verbatim (snake_case), so this row — like
    // every other DB-row struct here — must NOT use rename_all = "camelCase".
    #[derive(Deserialize)]
    struct ChargeRow {
        subscription_id: i64,
        currency_code: String,
        total_cents: i64,
    }
    let charges: Vec<ChargeRow> = all(
        db,
        "SELECT t.subscription_id AS subscription_id,
                w.currency_code AS currency_code,
                SUM(t.amount_cents) AS total_cents
         FROM transactions t
         JOIN wallets w ON w.id = t.wallet_id
         WHERE w.user_id = ?1
           AND t.subscription_id IS NOT NULL
           AND t.kind = 'expense'
           AND t.occurred_at >= ?2 AND t.occurred_at < ?3
         GROUP BY t.subscription_id, w.currency_code",
        jsv![uid, start, end],
    )
    .await?;

    let mut charged_mxn: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();
    let mut monthly_total_mxn_cents = 0i64;
    for c in charges {
        let mxn = to_mxn(
            c.total_cents,
            rates.get(&c.currency_code).copied().unwrap_or(0),
        );
        *charged_mxn.entry(c.subscription_id).or_insert(0) += mxn;
        monthly_total_mxn_cents += mxn;
    }

    let mut subscriptions: Vec<Subscription> = rows
        .into_iter()
        .map(|r| {
            let mut sub: Subscription = r.into();
            sub.charged_in_period = charged_mxn.contains_key(&sub.id);
            sub
        })
        .collect();
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
    /// Older clients omit it → manual charging, the historical behaviour.
    #[serde(default)]
    pub auto_charge: bool,
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
    if a.auto_charge && a.wallet_id.is_none() {
        return Err(AppError::InvalidInput(
            "el cobro automático necesita una cartera".into(),
        ));
    }
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
           (user_id, name, icon, color, amount_cents, currency_code, cadence, next_charge_date, wallet_id, category_id, auto_charge, started_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, date('now'))",
        jsv![
            uid, a.name.trim(), a.icon, a.color, a.amount_cents, a.currency_code,
            a.cadence, a.next_charge_date, a.wallet_id, a.category_id, a.auto_charge as i64
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
            currency_code = ?7, cadence = ?8, next_charge_date = ?9, wallet_id = ?10, category_id = ?11,
            auto_charge = ?12
         WHERE id = ?1 AND user_id = ?2",
        jsv![
            a.id, uid, i.name.trim(), i.icon, i.color, i.amount_cents,
            i.currency_code, i.cadence, i.next_charge_date, i.wallet_id, i.category_id,
            i.auto_charge as i64
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
            // Auto-posted subscription charge: no meaningful wall-clock time.
            occurred_time: None,
            client_id: None,
            subscription_id: Some(sub.id),
            refund_of_id: None,
        },
    )
    .await?;

    let next = next_after(
        parse_day(&sub.next_charge_date)?,
        cadence_months(&sub.cadence),
    );
    exec(
        db,
        "UPDATE subscriptions SET next_charge_date = ?3 WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, next.to_string()],
    )
    .await?;
    fetch_sub(db, uid, a.id).await
}

fn cadence_months(cadence: &str) -> i32 {
    if cadence == "yearly" {
        12
    } else {
        1
    }
}

fn parse_day(s: &str) -> AppResult<chrono::NaiveDate> {
    chrono::NaiveDate::parse_from_str(s, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput("fecha inválida (YYYY-MM-DD)".into()))
}

/// Most charges booked for one subscription in a single cron run (catch-up
/// after a long outage, or a next date set far in the past).
const MAX_CATCH_UP: usize = 12;

/// Cron: books every due charge of active auto-charging subscriptions, each
/// dated on its own charge date, and advances `next_charge_date`. Inserts and
/// the date update share one batch; client_id 'sub:<id>:<date>' plus the
/// `next_charge_date = old` guard keep overlapping runs from double-charging.
pub async fn post_due_subscriptions(db: &D1Database) -> AppResult<()> {
    #[derive(Deserialize)]
    struct DueRow {
        id: i64,
        user_id: i64,
        name: String,
        amount_cents: i64,
        currency_code: String,
        cadence: String,
        next_charge_date: String,
        wallet_id: i64,
        category_id: Option<i64>,
        wallet_currency: String,
    }
    let today = today_mx();
    let rows: Vec<DueRow> = all(
        db,
        "SELECT s.id, s.user_id, s.name, s.amount_cents, s.currency_code, s.cadence,
                s.next_charge_date, s.wallet_id, s.category_id,
                w.currency_code AS wallet_currency
         FROM subscriptions s
         JOIN wallets w ON w.id = s.wallet_id AND w.user_id = s.user_id
         WHERE s.is_active = 1 AND s.auto_charge = 1 AND w.is_archived = 0
           AND s.next_charge_date <= ?1",
        jsv![today.to_string()],
    )
    .await?;

    let mut rates_by_user = std::collections::HashMap::new();
    for r in rows {
        let result: AppResult<()> = async {
            use std::collections::hash_map::Entry;
            let rates = match rates_by_user.entry(r.user_id) {
                Entry::Occupied(e) => e.into_mut(),
                Entry::Vacant(e) => e.insert(load_rates(db, r.user_id).await?),
            };
            let amount_cents =
                convert(r.amount_cents, &r.currency_code, &r.wallet_currency, rates)?;
            let (due, next) = due_charges(
                parse_day(&r.next_charge_date)?,
                cadence_months(&r.cadence),
                today,
                MAX_CATCH_UP,
            );
            if due.is_empty() {
                return Ok(());
            }
            let mut stmts = Vec::with_capacity(due.len() + 1);
            for date in &due {
                let date = date.to_string();
                stmts.push(stmt(
                    db,
                    "INSERT OR IGNORE INTO transactions
                       (wallet_id, kind, amount_cents, category_id, description, occurred_at, client_id, subscription_id)
                     VALUES (?1, 'expense', ?2, ?3, ?4, ?5, ?6, ?7)",
                    jsv![
                        r.wallet_id,
                        amount_cents,
                        r.category_id,
                        r.name.clone(),
                        date.clone(),
                        format!("sub:{}:{}", r.id, date),
                        r.id
                    ],
                )?);
            }
            stmts.push(stmt(
                db,
                "UPDATE subscriptions SET next_charge_date = ?2
                 WHERE id = ?1 AND next_charge_date = ?3",
                jsv![r.id, next.to_string(), r.next_charge_date.clone()],
            )?);
            batch(db, stmts).await?;
            Ok(())
        }
        .await;
        if let Err(e) = result {
            worker::console_warn!("subscription auto-charge failed for {}: {e}", r.id);
        }
    }
    Ok(())
}
