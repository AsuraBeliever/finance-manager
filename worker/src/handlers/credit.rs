//! Credit-card wallets: the statement summary the detail page renders and the
//! MSI ("meses sin intereses") plans behind it. All calendar/money math is
//! pure in finanzas_core::credit; this module only loads rows and assembles.
//!
//! An MSI plan is NOT a transaction. Each installment becomes a real expense
//! posted on its cut date — by the daily cron and eagerly on plan creation —
//! with a deterministic `client_id` ("msi:<plan>:<n>") so re-runs are no-ops
//! (same idempotency scheme as wallet_yield). The wallet's debt therefore
//! matches what the bank has billed, while the unbilled remainder still
//! subtracts from available credit.

use chrono::NaiveDate;
use finanzas_core::credit::{
    due_date, last_cut_date, msi_installment_cents, msi_installment_date, msi_installments_due,
    next_anniversary, next_cut_date,
};
use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use crate::db::{all, batch, first, stmt, today_mx};
use crate::jsv;

use super::wallet_yield::balance_as_of;
use super::wallets::fetch_wallet;

/// MX cards give ~20 natural days after the cut to pay without interest;
/// used when the user didn't type their own.
const DEFAULT_DUE_DAYS: i64 = 20;

/// Highest MSI term banks offer (48 is already rare).
const MAX_MSI_MONTHS: i64 = 60;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CreditCardSummary {
    /// What you owe today: -balance, floored at zero.
    pub debt_cents: i64,
    pub credit_limit_cents: Option<i64>,
    /// limit − debt − unbilled MSI; None when the limit isn't tracked.
    pub available_credit_cents: Option<i64>,
    /// (debt + unbilled MSI) ÷ limit in basis points; None without a limit.
    pub utilization_bps: Option<i64>,
    pub next_cut_date: String,
    pub days_to_cut: i64,
    pub statement: Statement,
    pub next_anniversary: Option<String>,
    /// MSI amounts not yet billed — committed but not in the debt yet.
    pub pending_msi_cents: i64,
    pub msi_plans: Vec<MsiPlanView>,
}

/// The last closed statement and how it stands today.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Statement {
    pub cut_date: String,
    /// Saldo al corte: debt as of the cut day — pay this in full by the due
    /// date and no interest is generated.
    pub balance_cents: i64,
    /// Payments (income/transfer_in) posted after the cut.
    pub paid_cents: i64,
    /// balance − paid, floored at zero.
    pub remaining_cents: i64,
    pub due_date: String,
    /// Negative = past due.
    pub days_to_due: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MsiPlanView {
    pub id: i64,
    pub description: String,
    pub total_cents: i64,
    pub months: i64,
    /// The regular installment (the first one also carries the cent remainder).
    pub monthly_cents: i64,
    pub billed_months: i64,
    pub pending_cents: i64,
    /// None once the plan is fully billed.
    pub next_charge_date: Option<String>,
    pub next_charge_cents: Option<i64>,
    pub purchased_at: String,
}

#[derive(Deserialize)]
struct MsiPlanRow {
    id: i64,
    description: String,
    total_cents: i64,
    months: i64,
    purchased_at: String,
}

#[derive(Deserialize)]
struct SumRow {
    total: i64,
}

#[derive(Deserialize)]
struct IdRow {
    id: i64,
}

fn parse_date(s: &str, what: &str) -> AppResult<NaiveDate> {
    NaiveDate::parse_from_str(s, "%Y-%m-%d")
        .map_err(|_| AppError::InvalidInput(format!("{what} inválida")))
}

/// Cents billed for the first `billed` installments (remainder rides on #1).
fn billed_cents(total: i64, months: i64, billed: i64) -> i64 {
    if billed <= 0 || months <= 0 {
        return 0;
    }
    total / months * billed + total % months
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WalletIdArgs {
    pub wallet_id: i64,
}

pub async fn get_credit_card_summary(
    db: &D1Database,
    uid: i64,
    a: WalletIdArgs,
) -> AppResult<CreditCardSummary> {
    let wallet = fetch_wallet(db, uid, a.wallet_id).await?;
    let cut_day = wallet
        .credit_cut_day
        .ok_or_else(|| AppError::InvalidInput("la cartera no es tarjeta de crédito".into()))?
        as u32;
    let today = today_mx();

    let debt = (-wallet.balance_cents).max(0);

    // Last closed statement: debt at the cut, minus payments made since.
    let cut = last_cut_date(today, cut_day);
    let statement_balance = (-balance_as_of(db, wallet.id, &cut.to_string()).await?).max(0);
    let paid: i64 = first::<SumRow>(
        db,
        "SELECT COALESCE(SUM(t.amount_cents), 0) AS total
         FROM transactions t
         WHERE t.wallet_id = ?1 AND t.occurred_at > ?2
           AND t.kind IN ('income', 'transfer_in')",
        jsv![wallet.id, cut.to_string()],
    )
    .await?
    .map(|r| r.total)
    .unwrap_or(0);
    let due = due_date(cut, wallet.credit_due_days.unwrap_or(DEFAULT_DUE_DAYS));

    let plans: Vec<MsiPlanRow> = all(
        db,
        "SELECT id, description, total_cents, months, purchased_at
         FROM msi_plans WHERE wallet_id = ?1 ORDER BY created_at, id",
        jsv![wallet.id],
    )
    .await?;
    let mut pending_msi = 0_i64;
    let mut msi_plans = Vec::with_capacity(plans.len());
    for p in plans {
        let purchased = parse_date(&p.purchased_at, "fecha de compra")?;
        let billed = msi_installments_due(purchased, cut_day, p.months as u32, today) as i64;
        let pending = p.total_cents - billed_cents(p.total_cents, p.months, billed);
        pending_msi += pending;
        let next = (billed < p.months).then(|| {
            (
                msi_installment_date(purchased, cut_day, billed as u32 + 1).to_string(),
                msi_installment_cents(p.total_cents, p.months, billed + 1),
            )
        });
        msi_plans.push(MsiPlanView {
            id: p.id,
            description: p.description,
            total_cents: p.total_cents,
            months: p.months,
            monthly_cents: p.total_cents / p.months,
            billed_months: billed,
            pending_cents: pending,
            next_charge_date: next.as_ref().map(|(d, _)| d.clone()),
            next_charge_cents: next.map(|(_, c)| c),
            purchased_at: p.purchased_at,
        });
    }

    // Banks hold the full unbilled MSI against the line, so both utilization
    // and available credit count debt + pending. i128 guards the bps product.
    let owed = debt + pending_msi;
    let utilization_bps = wallet
        .credit_limit_cents
        .filter(|l| *l > 0)
        .map(|l| (owed as i128 * 10_000 / l as i128) as i64);
    let next_cut = next_cut_date(today, cut_day);

    Ok(CreditCardSummary {
        debt_cents: debt,
        credit_limit_cents: wallet.credit_limit_cents,
        available_credit_cents: wallet.credit_limit_cents.map(|l| l - owed),
        utilization_bps,
        next_cut_date: next_cut.to_string(),
        days_to_cut: (next_cut - today).num_days(),
        statement: Statement {
            cut_date: cut.to_string(),
            balance_cents: statement_balance,
            paid_cents: paid,
            remaining_cents: (statement_balance - paid).max(0),
            due_date: due.to_string(),
            days_to_due: (due - today).num_days(),
        },
        next_anniversary: wallet
            .credit_anniversary
            .as_deref()
            .and_then(|md| next_anniversary(today, md))
            .map(|d| d.to_string()),
        pending_msi_cents: pending_msi,
        msi_plans,
    })
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateMsiPlanArgs {
    pub wallet_id: i64,
    pub description: String,
    pub total_cents: i64,
    pub months: i64,
    pub purchased_at: Option<String>,
}

pub async fn create_msi_plan(db: &D1Database, uid: i64, a: CreateMsiPlanArgs) -> AppResult<()> {
    if a.description.trim().is_empty() {
        return Err(AppError::InvalidInput(
            "la descripción es obligatoria".into(),
        ));
    }
    if a.total_cents <= 0 {
        return Err(AppError::InvalidInput(
            "el monto debe ser mayor a cero".into(),
        ));
    }
    if !(2..=MAX_MSI_MONTHS).contains(&a.months) {
        return Err(AppError::InvalidInput(format!(
            "los meses deben estar entre 2 y {MAX_MSI_MONTHS}"
        )));
    }
    let wallet = fetch_wallet(db, uid, a.wallet_id).await?;
    let cut_day = wallet
        .credit_cut_day
        .ok_or_else(|| AppError::InvalidInput("la cartera no es tarjeta de crédito".into()))?
        as u32;
    let today = today_mx();
    let purchased = match &a.purchased_at {
        Some(s) => parse_date(s, "fecha de compra")?,
        None => today,
    };
    if purchased > today {
        return Err(AppError::InvalidInput(
            "la fecha de compra no puede ser futura".into(),
        ));
    }

    let res = crate::db::exec(
        db,
        "INSERT INTO msi_plans (wallet_id, description, total_cents, months, purchased_at)
         VALUES (?1, ?2, ?3, ?4, ?5)",
        jsv![
            a.wallet_id,
            a.description.trim(),
            a.total_cents,
            a.months,
            purchased.to_string()
        ],
    )
    .await?;
    let plan = MsiPlanRow {
        id: crate::db::last_row_id(&res)?,
        description: a.description.trim().to_string(),
        total_cents: a.total_cents,
        months: a.months,
        purchased_at: purchased.to_string(),
    };

    // A back-dated plan may already have billed installments — post them now
    // instead of waiting for tonight's cron.
    post_plan_installments(
        db,
        &plan,
        a.wallet_id,
        cut_day,
        msi_category(db).await?,
        today,
    )
    .await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdArgs {
    pub id: i64,
}

/// Deletes the plan and every installment expense it posted, in one batch —
/// leaving orphan charges would overstate the debt forever.
pub async fn delete_msi_plan(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let owned: Option<IdRow> = first(
        db,
        "SELECT p.id FROM msi_plans p
         JOIN wallets w ON w.id = p.wallet_id
         WHERE p.id = ?1 AND w.user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    if owned.is_none() {
        return Err(AppError::NotFound("plan MSI"));
    }
    let stmts = vec![
        stmt(
            db,
            "DELETE FROM transactions WHERE client_id LIKE ?1",
            jsv![format!("msi:{}:%", a.id)],
        )?,
        stmt(db, "DELETE FROM msi_plans WHERE id = ?1", jsv![a.id])?,
    ];
    batch(db, stmts).await?;
    Ok(())
}

/// The reserved 'Meses sin intereses' expense category (see migration 0027),
/// or None if it was ever removed — charges still post, just uncategorized.
async fn msi_category(db: &D1Database) -> AppResult<Option<i64>> {
    Ok(first::<IdRow>(
        db,
        "SELECT id FROM transaction_categories
         WHERE name = 'Meses sin intereses' AND kind = 'expense' AND user_id IS NULL",
        vec![],
    )
    .await?
    .map(|r| r.id))
}

/// Cron entry: post every installment that has reached its cut date, across
/// all credit-card wallets. Best-effort per plan, like accrue_yield.
pub async fn post_msi_installments(db: &D1Database) -> AppResult<()> {
    #[derive(Deserialize)]
    struct PlanWithCut {
        id: i64,
        wallet_id: i64,
        description: String,
        total_cents: i64,
        months: i64,
        purchased_at: String,
        cut_day: i64,
    }
    let plans: Vec<PlanWithCut> = all(
        db,
        "SELECT p.id, p.wallet_id, p.description, p.total_cents, p.months,
                p.purchased_at, w.credit_cut_day AS cut_day
         FROM msi_plans p
         JOIN wallets w ON w.id = p.wallet_id
         WHERE w.credit_cut_day IS NOT NULL AND w.is_archived = 0",
        vec![],
    )
    .await?;
    if plans.is_empty() {
        return Ok(());
    }
    let category = msi_category(db).await?;
    let today = today_mx();
    for p in plans {
        let row = MsiPlanRow {
            id: p.id,
            description: p.description,
            total_cents: p.total_cents,
            months: p.months,
            purchased_at: p.purchased_at,
        };
        if let Err(e) =
            post_plan_installments(db, &row, p.wallet_id, p.cut_day as u32, category, today).await
        {
            worker::console_warn!("msi posting failed for plan {}: {e}", p.id);
        }
    }
    Ok(())
}

/// Posts the due installments of one plan owned by `wallet_id`.
async fn post_plan_installments(
    db: &D1Database,
    plan: &MsiPlanRow,
    wallet_id: i64,
    cut_day: u32,
    category: Option<i64>,
    today: NaiveDate,
) -> AppResult<()> {
    let purchased = parse_date(&plan.purchased_at, "fecha de compra")?;
    let due = msi_installments_due(purchased, cut_day, plan.months as u32, today) as i64;
    if due == 0 {
        return Ok(());
    }
    // Cheap idempotency shortcut: if the newest due installment is already
    // posted, every earlier one is too (they only ever post in order).
    let latest = format!("msi:{}:{}", plan.id, due);
    if first::<IdRow>(
        db,
        "SELECT id FROM transactions WHERE client_id = ?1",
        jsv![latest],
    )
    .await?
    .is_some()
    {
        return Ok(());
    }
    let stmts = (1..=due)
        .map(|n| {
            stmt(
                db,
                "INSERT OR IGNORE INTO transactions
                   (wallet_id, kind, amount_cents, category_id, description, occurred_at, client_id)
                 VALUES (?1, 'expense', ?2, ?3, ?4, ?5, ?6)",
                jsv![
                    wallet_id,
                    msi_installment_cents(plan.total_cents, plan.months, n),
                    category,
                    format!("{} ({}/{})", plan.description, n, plan.months),
                    msi_installment_date(purchased, cut_day, n as u32).to_string(),
                    format!("msi:{}:{}", plan.id, n)
                ],
            )
        })
        .collect::<AppResult<Vec<_>>>()?;
    batch(db, stmts).await?;
    Ok(())
}
