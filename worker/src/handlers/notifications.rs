//! In-app notification engine. The 14:00 UTC (08:00 CDMX) cron evaluates each
//! opted-in user's rules and inserts rows into `notifications`; the frontend
//! bell renders them. No text is stored: `kind` is an i18n key and
//! `params_json` carries the raw values (cents, dates, names) interpolated in
//! the active locale.
//!
//! Idempotency mirrors wallet_yield/MSI: a UNIQUE `(user_id, dedupe_key)`
//! index + `INSERT OR IGNORE`, with dedupe keys pinned to the *target* date
//! ('credit.dueSoon:<wallet>:<due_date>'), so a "≤ N days before" window
//! fires exactly once no matter how many mornings the cron runs inside it.
//!
//! Preferences live in the `notification_prefs` setting as JSON — per
//! category (credit/goals/subscriptions/investments) a master switch plus
//! per-rule toggles and params. Anything missing parses as OFF, so only
//! users who saved prefs generate any work at all.

use std::collections::HashMap;

use chrono::NaiveDate;
use finanzas_core::error::{AppError, AppResult};
use finanzas_core::goals::{plan_contribution, Cadence};
use finanzas_core::notify::{due_occurrence, period_key, ReminderCadence};
use serde::{Deserialize, Serialize};
use worker::{console_warn, D1Database};

use crate::db::{all, batch, batch_chunks, exec, first, stmt, today_mx, CountRow};
use crate::jsv;

use super::credit::{get_credit_card_summary, WalletIdArgs};
use super::investments::{fetch_investment, with_value};

// ---- preferences ----

fn default_in_app() -> bool {
    true
}

/// Delivery channels for a rule. Email is stored but not sent yet — the SMTP
/// milestone will read it; the bell only honors `in_app`.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Channels {
    pub in_app: bool,
    pub email: bool,
}

impl Default for Channels {
    fn default() -> Self {
        Channels {
            in_app: default_in_app(),
            email: false,
        }
    }
}

/// One rule's config. Params not used by a rule are simply ignored.
#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct Rule {
    pub enabled: bool,
    pub days_before: Option<i64>,
    pub threshold_bps: Option<i64>,
    pub channels: Channels,
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct CategoryPrefs {
    pub enabled: bool,
    pub rules: HashMap<String, Rule>,
}

impl CategoryPrefs {
    /// The rule, only when the category master check, the rule itself and its
    /// in-app channel are all on.
    fn active(&self, name: &str) -> Option<&Rule> {
        if !self.enabled {
            return None;
        }
        self.rules
            .get(name)
            .filter(|r| r.enabled && r.channels.in_app)
    }

    fn days_before(&self, name: &str, default: i64) -> Option<i64> {
        self.active(name)
            .map(|r| r.days_before.unwrap_or(default).max(0))
    }
}

#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct NotificationPrefs {
    pub credit: CategoryPrefs,
    pub goals: CategoryPrefs,
    pub subscriptions: CategoryPrefs,
    pub investments: CategoryPrefs,
}

/// Malformed prefs must never break the cron for everyone — they parse as
/// "everything off" for that user.
fn parse_prefs(json: &str) -> NotificationPrefs {
    serde_json::from_str(json).unwrap_or_default()
}

// ---- RPC: bell ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListNotificationsArgs {
    #[serde(default)]
    pub limit: Option<i64>,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationView {
    pub id: i64,
    pub kind: String,
    pub params_json: String,
    pub created_at: String,
    pub read_at: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationList {
    pub items: Vec<NotificationView>,
    pub unread_count: i64,
}

pub async fn list_notifications(
    db: &D1Database,
    uid: i64,
    a: ListNotificationsArgs,
) -> AppResult<NotificationList> {
    let limit = a.limit.unwrap_or(30).clamp(1, 100);
    // Aliases match the camelCase the view (de)serializes with.
    let items: Vec<NotificationView> = all(
        db,
        "SELECT id, kind, params_json AS paramsJson, created_at AS createdAt,
                read_at AS readAt
         FROM notifications WHERE user_id = ?1
         ORDER BY created_at DESC, id DESC LIMIT ?2",
        jsv![uid, limit],
    )
    .await?;
    let unread_count = first::<CountRow>(
        db,
        "SELECT COUNT(*) AS n FROM notifications WHERE user_id = ?1 AND read_at IS NULL",
        jsv![uid],
    )
    .await?
    .map(|r| r.n)
    .unwrap_or(0);
    Ok(NotificationList {
        items,
        unread_count,
    })
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MarkReadArgs {
    /// Specific notifications to mark; None marks everything unread.
    #[serde(default)]
    pub ids: Option<Vec<i64>>,
}

pub async fn mark_notifications_read(db: &D1Database, uid: i64, a: MarkReadArgs) -> AppResult<()> {
    match a.ids {
        None => {
            exec(
                db,
                "UPDATE notifications SET read_at = datetime('now')
                 WHERE user_id = ?1 AND read_at IS NULL",
                jsv![uid],
            )
            .await?;
        }
        Some(ids) if ids.is_empty() => {}
        Some(ids) => {
            // D1 has no array binds; inline the ids (they're i64, not user text).
            let list = ids
                .iter()
                .map(|i| i.to_string())
                .collect::<Vec<_>>()
                .join(",");
            let sql = format!(
                "UPDATE notifications SET read_at = datetime('now')
                 WHERE user_id = ?1 AND read_at IS NULL AND id IN ({list})"
            );
            exec(db, &sql, jsv![uid]).await?;
        }
    }
    Ok(())
}

// ---- RPC: per-investment reminders ----

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentReminderView {
    pub investment_id: i64,
    pub kind: String,
    pub cadence: String,
}

/// Every reminder across the caller's investments — the notification settings
/// page (the only place reminders are configured) renders them per investment.
pub async fn list_investment_reminders(
    db: &D1Database,
    uid: i64,
) -> AppResult<Vec<InvestmentReminderView>> {
    all(
        db,
        "SELECT r.investment_id AS investmentId, r.kind, r.cadence
         FROM investment_reminders r
         JOIN investments i ON i.id = r.investment_id
         WHERE i.user_id = ?1
         ORDER BY r.investment_id, r.kind",
        jsv![uid],
    )
    .await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SetReminderArgs {
    pub investment_id: i64,
    /// 'contribute' | 'performance'
    pub kind: String,
    /// A valid cadence upserts the reminder; None removes it.
    #[serde(default)]
    pub cadence: Option<String>,
}

pub async fn set_investment_reminder(
    db: &D1Database,
    uid: i64,
    a: SetReminderArgs,
) -> AppResult<()> {
    if !matches!(a.kind.as_str(), "contribute" | "performance") {
        return Err(AppError::InvalidInput(
            "tipo de recordatorio inválido".into(),
        ));
    }
    fetch_investment(db, uid, a.investment_id).await?; // ownership
    match a.cadence.as_deref() {
        None => {
            exec(
                db,
                "DELETE FROM investment_reminders WHERE investment_id = ?1 AND kind = ?2",
                jsv![a.investment_id, a.kind],
            )
            .await?;
        }
        Some(c) => {
            if ReminderCadence::parse(c).is_none() {
                return Err(AppError::InvalidInput("cadencia inválida".into()));
            }
            // Re-anchoring at today means "every X starting now": the first
            // occurrence lands one full period ahead, and the cursor resets so
            // the next performance summary reports total gain from scratch.
            exec(
                db,
                "INSERT INTO investment_reminders (investment_id, kind, cadence, anchor_date)
                 VALUES (?1, ?2, ?3, ?4)
                 ON CONFLICT(investment_id, kind) DO UPDATE SET
                   cadence = excluded.cadence,
                   anchor_date = excluded.anchor_date,
                   last_fired_date = NULL,
                   last_value_cents = NULL",
                jsv![a.investment_id, a.kind, c, today_mx().to_string()],
            )
            .await?;
        }
    }
    Ok(())
}

// ---- cron: evaluation ----

/// A notification waiting to be inserted (OR IGNORE makes re-runs no-ops).
struct Pending {
    kind: &'static str,
    params: serde_json::Value,
    dedupe_key: String,
}

impl Pending {
    fn new(kind: &'static str, entity: i64, target: &str, params: serde_json::Value) -> Self {
        Pending {
            kind,
            params,
            dedupe_key: format!("{kind}:{entity}:{target}"),
        }
    }
}

#[derive(Deserialize)]
struct PrefsRow {
    user_id: i64,
    value: String,
}

/// Cron entry (14:00 UTC): evaluate every opted-in user's rules, insert the
/// due notifications and prune old rows. Best-effort per user.
pub async fn generate_all(db: &D1Database) -> AppResult<()> {
    let users: Vec<PrefsRow> = all(
        db,
        "SELECT user_id, value FROM settings
         WHERE key = 'notification_prefs' AND user_id > 0",
        vec![],
    )
    .await?;
    let today = today_mx();
    for u in users {
        let prefs = parse_prefs(&u.value);
        if let Err(e) = generate_for_user(db, u.user_id, &prefs, today).await {
            console_warn!("notifications failed for user {}: {e}", u.user_id);
        }
    }
    exec(
        db,
        "DELETE FROM notifications WHERE created_at < datetime('now', '-60 days')",
        vec![],
    )
    .await?;
    Ok(())
}

async fn generate_for_user(
    db: &D1Database,
    uid: i64,
    prefs: &NotificationPrefs,
    today: NaiveDate,
) -> AppResult<()> {
    let mut out = Vec::new();
    if prefs.credit.enabled {
        evaluate_credit(db, uid, &prefs.credit, today, &mut out).await?;
    }
    if prefs.goals.enabled {
        evaluate_goals(db, uid, &prefs.goals, today, &mut out).await?;
    }
    if prefs.subscriptions.enabled {
        evaluate_subscriptions(db, uid, &prefs.subscriptions, today, &mut out).await?;
    }
    insert_pending(db, uid, out).await?;
    if prefs.investments.enabled {
        // Handled apart: each fired reminder pairs its INSERT with the cursor
        // UPDATE in one batch, so the two can't drift.
        evaluate_investments(db, uid, &prefs.investments, today).await?;
    }
    Ok(())
}

async fn insert_pending(db: &D1Database, uid: i64, pending: Vec<Pending>) -> AppResult<()> {
    if pending.is_empty() {
        return Ok(());
    }
    let stmts = pending
        .iter()
        .map(|p| insert_stmt(db, uid, p))
        .collect::<AppResult<Vec<_>>>()?;
    batch_chunks(db, stmts, 20).await
}

fn insert_stmt(db: &D1Database, uid: i64, p: &Pending) -> AppResult<worker::D1PreparedStatement> {
    stmt(
        db,
        "INSERT OR IGNORE INTO notifications (user_id, kind, params_json, dedupe_key)
         VALUES (?1, ?2, ?3, ?4)",
        jsv![uid, p.kind, p.params.to_string(), p.dedupe_key],
    )
}

// ---- credit cards ----

#[derive(Deserialize)]
struct CardRow {
    id: i64,
    name: String,
    currency_code: String,
}

async fn evaluate_credit(
    db: &D1Database,
    uid: i64,
    p: &CategoryPrefs,
    today: NaiveDate,
    out: &mut Vec<Pending>,
) -> AppResult<()> {
    let cards: Vec<CardRow> = all(
        db,
        "SELECT id, name, currency_code FROM wallets
         WHERE user_id = ?1 AND credit_cut_day IS NOT NULL AND is_archived = 0",
        jsv![uid],
    )
    .await?;
    for card in cards {
        let s = get_credit_card_summary(db, uid, WalletIdArgs { wallet_id: card.id }).await?;

        // A cut with no debt is a non-event; don't nag about unused cards.
        if let Some(days) = p.days_before("cutSoon", 3) {
            if s.debt_cents > 0 && (0..=days).contains(&s.days_to_cut) {
                out.push(Pending::new(
                    "credit.cutSoon",
                    card.id,
                    &s.next_cut_date,
                    serde_json::json!({
                        "wallet": card.name,
                        "date": s.next_cut_date,
                        "days": s.days_to_cut,
                        "debtCents": s.debt_cents,
                        "currencyCode": card.currency_code,
                    }),
                ));
            }
        }
        if let Some(days) = p.days_before("dueSoon", 5) {
            if s.statement.remaining_cents > 0 && (0..=days).contains(&s.statement.days_to_due) {
                out.push(Pending::new(
                    "credit.dueSoon",
                    card.id,
                    &s.statement.due_date,
                    serde_json::json!({
                        "wallet": card.name,
                        "amountCents": s.statement.remaining_cents,
                        "date": s.statement.due_date,
                        "days": s.statement.days_to_due,
                        "currencyCode": card.currency_code,
                    }),
                ));
            }
        }
        if let Some(rule) = p.active("utilization") {
            let threshold = rule.threshold_bps.unwrap_or(7_000).max(1);
            if let Some(bps) = s.utilization_bps {
                // One alert per statement period, keyed on the current cut.
                if bps >= threshold {
                    out.push(Pending::new(
                        "credit.utilization",
                        card.id,
                        &s.statement.cut_date,
                        serde_json::json!({
                            "wallet": card.name,
                            "utilizationBps": bps,
                            "thresholdBps": threshold,
                        }),
                    ));
                }
            }
        }
        if let Some(days) = p.days_before("anniversary", 14) {
            if let Some(ann) = &s.next_anniversary {
                if let Ok(d) = NaiveDate::parse_from_str(ann, "%Y-%m-%d") {
                    if (0..=days).contains(&(d - today).num_days()) {
                        out.push(Pending::new(
                            "credit.anniversary",
                            card.id,
                            ann,
                            serde_json::json!({ "wallet": card.name, "date": ann }),
                        ));
                    }
                }
            }
        }
    }

    // MSI installments the nightly cron just posted (dedupe = the charge's own
    // client_id, so this window can overlap previous days safely).
    if p.active("msiPosted").is_some() {
        #[derive(Deserialize)]
        struct MsiTxRow {
            client_id: String,
            description: Option<String>,
            amount_cents: i64,
            occurred_at: String,
            wallet: String,
            currency_code: String,
        }
        let rows: Vec<MsiTxRow> = all(
            db,
            "SELECT t.client_id, t.description, t.amount_cents, t.occurred_at,
                    w.name AS wallet, w.currency_code
             FROM transactions t JOIN wallets w ON w.id = t.wallet_id
             WHERE w.user_id = ?1 AND t.client_id LIKE 'msi:%' AND t.occurred_at >= ?2",
            jsv![uid, (today - chrono::Duration::days(2)).to_string()],
        )
        .await?;
        for r in rows {
            out.push(Pending {
                kind: "credit.msiPosted",
                params: serde_json::json!({
                    "wallet": r.wallet,
                    "description": r.description.unwrap_or_default(),
                    "amountCents": r.amount_cents,
                    "date": r.occurred_at,
                    "currencyCode": r.currency_code,
                }),
                dedupe_key: format!("credit.msiPosted:{}", r.client_id),
            });
        }
    }
    Ok(())
}

// ---- savings goals ----

#[derive(Deserialize)]
struct GoalRow {
    id: i64,
    name: String,
    currency_code: String,
    target_cents: i64,
    saved_cents: i64,
    target_date: Option<String>,
    contribution_cadence: Option<String>,
    #[serde(default)]
    plan_anchor_date: Option<String>,
    created_date: String,
}

async fn evaluate_goals(
    db: &D1Database,
    uid: i64,
    p: &CategoryPrefs,
    today: NaiveDate,
    out: &mut Vec<Pending>,
) -> AppResult<()> {
    let goals: Vec<GoalRow> = all(
        db,
        "SELECT id, name, currency_code, target_cents, saved_cents, target_date,
                contribution_cadence, plan_anchor_date,
                substr(created_at, 1, 10) AS created_date
         FROM savings_goals WHERE user_id = ?1 AND archived_at IS NULL",
        jsv![uid],
    )
    .await?;
    // Same period baseline the goals page uses, so the reminder talks about
    // the SAME missing amount the card shows (and stays quiet once covered).
    let baselines = super::goals::period_baselines(db, uid, today).await?;
    for g in goals {
        if p.active("completed").is_some() && g.saved_cents >= g.target_cents {
            // No period in the key: congratulate once, ever.
            out.push(Pending::new(
                "goal.completed",
                g.id,
                "done",
                serde_json::json!({
                    "name": g.name,
                    "targetCents": g.target_cents,
                    "currencyCode": g.currency_code,
                }),
            ));
            continue;
        }

        let (Some(date_s), Some(cad_s)) = (&g.target_date, &g.contribution_cadence) else {
            continue; // no deadline → no pace to keep
        };
        let (Ok(deadline), Some(cadence), Ok(start)) = (
            NaiveDate::parse_from_str(date_s, "%Y-%m-%d"),
            Cadence::parse(cad_s),
            NaiveDate::parse_from_str(
                g.plan_anchor_date.as_deref().unwrap_or(&g.created_date),
                "%Y-%m-%d",
            ),
        ) else {
            continue;
        };
        let baseline = baselines.get(&g.id).copied().unwrap_or(0);
        let plan = plan_contribution(
            start,
            deadline,
            today,
            cadence,
            g.target_cents,
            g.saved_cents,
            (g.saved_cents - baseline).max(0),
        );

        if p.active("contribution").is_some() && !plan.overdue && plan.period_missing_cents > 0 {
            // Fires on the first cron morning of each cadence period, with
            // what's still missing for it; a covered period stays silent.
            out.push(Pending::new(
                "goal.contribution",
                g.id,
                &period_key(cadence, today),
                serde_json::json!({
                    "name": g.name,
                    "amountCents": plan.period_missing_cents,
                    "cadence": cad_s,
                    "currencyCode": g.currency_code,
                }),
            ));
        }
        if p.active("behind").is_some() && plan.behind_cents > 0 && !plan.overdue {
            // Monthly cadence so it nudges without nagging every day.
            out.push(Pending::new(
                "goal.behind",
                g.id,
                &today.format("%Y-%m").to_string(),
                serde_json::json!({
                    "name": g.name,
                    "behindCents": plan.behind_cents,
                    "currencyCode": g.currency_code,
                }),
            ));
        }
        if let Some(days) = p.days_before("deadlineSoon", 7) {
            let remaining = (g.target_cents - g.saved_cents).max(0);
            if remaining > 0 && (0..=days).contains(&plan.days_left) {
                out.push(Pending::new(
                    "goal.deadlineSoon",
                    g.id,
                    date_s,
                    serde_json::json!({
                        "name": g.name,
                        "date": date_s,
                        "days": plan.days_left,
                        "remainingCents": remaining,
                        "currencyCode": g.currency_code,
                    }),
                ));
            }
        }
    }
    Ok(())
}

// ---- subscriptions ----

#[derive(Deserialize)]
struct SubRow {
    id: i64,
    name: String,
    amount_cents: i64,
    currency_code: String,
    next_charge_date: String,
}

async fn evaluate_subscriptions(
    db: &D1Database,
    uid: i64,
    p: &CategoryPrefs,
    today: NaiveDate,
    out: &mut Vec<Pending>,
) -> AppResult<()> {
    let subs: Vec<SubRow> = all(
        db,
        "SELECT id, name, amount_cents, currency_code, next_charge_date
         FROM subscriptions
         WHERE user_id = ?1 AND is_active = 1 AND ended_at IS NULL",
        jsv![uid],
    )
    .await?;
    for s in subs {
        let Ok(charge) = NaiveDate::parse_from_str(&s.next_charge_date, "%Y-%m-%d") else {
            continue;
        };
        let days_left = (charge - today).num_days();
        let params = serde_json::json!({
            "name": s.name,
            "amountCents": s.amount_cents,
            "date": s.next_charge_date,
            "days": days_left,
            "currencyCode": s.currency_code,
        });
        if let Some(days) = p.days_before("chargeSoon", 3) {
            if (1..=days).contains(&days_left) {
                out.push(Pending::new(
                    "sub.chargeSoon",
                    s.id,
                    &s.next_charge_date,
                    params.clone(),
                ));
            }
        }
        if p.active("chargeToday").is_some() && days_left == 0 {
            out.push(Pending::new(
                "sub.chargeToday",
                s.id,
                &s.next_charge_date,
                params,
            ));
        }
    }
    Ok(())
}

// ---- investments ----

#[derive(Deserialize)]
struct ReminderRow {
    id: i64,
    investment_id: i64,
    kind: String,
    cadence: String,
    anchor_date: String,
    last_fired_date: Option<String>,
    last_value_cents: Option<i64>,
    name: String,
    currency_code: String,
}

async fn evaluate_investments(
    db: &D1Database,
    uid: i64,
    p: &CategoryPrefs,
    today: NaiveDate,
) -> AppResult<()> {
    let contribute_on = p.active("contribute").is_some();
    let performance_on = p.active("performance").is_some();
    if contribute_on || performance_on {
        let reminders: Vec<ReminderRow> = all(
            db,
            "SELECT r.id, r.investment_id, r.kind, r.cadence, r.anchor_date,
                    r.last_fired_date, r.last_value_cents,
                    i.name, i.currency_code
             FROM investment_reminders r
             JOIN investments i ON i.id = r.investment_id
             WHERE i.user_id = ?1 AND i.is_closed = 0",
            jsv![uid],
        )
        .await?;
        for r in reminders {
            let on = match r.kind.as_str() {
                "contribute" => contribute_on,
                "performance" => performance_on,
                _ => false,
            };
            if !on {
                continue;
            }
            if let Err(e) = fire_reminder(db, uid, &r, today).await {
                console_warn!("investment reminder {} failed: {e}", r.id);
            }
        }
    }

    // CETES maturity is pure calendar math (start + plazo), no valuation needed.
    if let Some(days) = p.days_before("cetesMaturity", 7) {
        let sql = "SELECT id, name, calculator, currency_code, principal_cents, start_date,
                          params_json, linked_wallet_id, is_closed, notes, created_at
                   FROM investments
                   WHERE user_id = ?1 AND is_closed = 0 AND calculator = 'cetes'";
        let rows: Vec<finanzas_core::models::Investment> = {
            #[derive(Deserialize)]
            struct Row {
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
            all::<Row>(db, sql, jsv![uid])
                .await?
                .into_iter()
                .map(|r| finanzas_core::models::Investment {
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
                })
                .collect()
        };
        let mut out = Vec::new();
        for inv in rows {
            let Ok(calc) = finanzas_core::investments::find(&inv.calculator) else {
                continue;
            };
            if let Some(maturity) = calc.maturity_date(&inv) {
                if (0..=days).contains(&(maturity - today).num_days()) {
                    out.push(Pending::new(
                        "inv.cetesMaturity",
                        inv.id,
                        &maturity.to_string(),
                        serde_json::json!({
                            "name": inv.name,
                            "date": maturity.to_string(),
                            "days": (maturity - today).num_days(),
                            "currencyCode": inv.currency_code,
                        }),
                    ));
                }
            }
        }
        insert_pending(db, uid, out).await?;
    }
    Ok(())
}

/// Fire one due reminder: the notification INSERT and the cursor UPDATE go in
/// the same batch so a crash can't notify twice or silently skip.
async fn fire_reminder(
    db: &D1Database,
    uid: i64,
    r: &ReminderRow,
    today: NaiveDate,
) -> AppResult<()> {
    let Some(cadence) = ReminderCadence::parse(&r.cadence) else {
        return Ok(());
    };
    let Ok(anchor) = NaiveDate::parse_from_str(&r.anchor_date, "%Y-%m-%d") else {
        return Ok(());
    };
    let last_fired = r
        .last_fired_date
        .as_deref()
        .and_then(|s| NaiveDate::parse_from_str(s, "%Y-%m-%d").ok());
    let Some(occurrence) = due_occurrence(cadence, anchor, last_fired, today) else {
        return Ok(());
    };
    let occ = occurrence.to_string();

    let (pending, new_value) = match r.kind.as_str() {
        "contribute" => (
            Pending::new(
                "inv.contribute",
                r.id,
                &occ,
                serde_json::json!({
                    "name": r.name,
                    "cadence": r.cadence,
                    "currencyCode": r.currency_code,
                }),
            ),
            None,
        ),
        "performance" => {
            let inv = fetch_investment(db, uid, r.investment_id).await?;
            let v = with_value(db, inv, today).await?;
            let since = r.last_fired_date.clone().unwrap_or(r.anchor_date.clone());
            (
                Pending::new(
                    "inv.performance",
                    r.id,
                    &occ,
                    serde_json::json!({
                        "name": r.name,
                        "valueCents": v.current_value_cents,
                        "gainSinceCents": r.last_value_cents.map(|prev| v.current_value_cents - prev),
                        "totalGainCents": v.gain_cents,
                        "since": since,
                        "currencyCode": r.currency_code,
                    }),
                ),
                Some(v.current_value_cents),
            )
        }
        _ => return Ok(()),
    };

    let stmts = vec![
        insert_stmt(db, uid, &pending)?,
        stmt(
            db,
            "UPDATE investment_reminders SET last_fired_date = ?2, last_value_cents = ?3
             WHERE id = ?1",
            jsv![r.id, occ, new_value.or(r.last_value_cents)],
        )?,
    ];
    batch(db, stmts).await?;
    Ok(())
}
