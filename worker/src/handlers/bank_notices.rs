//! Bank notices → movements waiting for the user (FIN-5).
//!
//! A notice arrives from Gmail (push → `process_push`) and goes through
//! `ingest`, which runs the core reader and stores a pending row. Nothing touches a balance until the
//! user confirms it: `confirm_pending_transaction` books an ordinary
//! income/expense through `transactions::add_*` (idempotent by client_id).
//!
//! Suggested wallet (card ending → wallet, then sender → wallet), suggested
//! category (the last one used for the same merchant) and possible duplicates
//! are worked out when listing, so a new mapping also applies to what is
//! already waiting. All of it scoped by user_id.

use chrono::NaiveDateTime;
use finanzas_core::bank_notice::{
    known_bank, looks_like_same_movement, read_notice, sender_address, sender_matches, BankNotice,
    NoticeKind, DUPLICATE_WINDOW_DAYS, KNOWN_BANKS,
};
use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::D1Database;

use crate::db::{all, changes, exec, first, today_mx};
use crate::gmail::{GmailConfig, HttpStatus, Tokens};
use crate::handlers::transactions::{self, SimpleTxArgs};
use crate::jsv;

// ----------------------------------------------------------------- ingest --

/// Read one notice and store it as pending. `true` = a new pending row;
/// `false` = already received (same message) or not a movement — the reason
/// goes to the log (`wrangler tail`), the user never sees it.
#[allow(clippy::too_many_arguments)]
async fn ingest(
    db: &D1Database,
    uid: i64,
    source: &str,
    source_id: &str,
    from: &str,
    subject: &str,
    body: &str,
    received_at: Option<NaiveDateTime>,
) -> AppResult<bool> {
    let notice: BankNotice = match read_notice(from, subject, body, received_at) {
        Ok(n) => n,
        Err(reason) => {
            worker::console_log!("notice {source_id} (user {uid}) skipped: {reason:?}");
            return Ok(false);
        }
    };
    let occurred_at = notice
        .occurred_at
        .clone()
        .unwrap_or_else(|| today_mx().format("%Y-%m-%d").to_string());
    let subject = (!subject.trim().is_empty()).then(|| truncate(subject, 200));
    let res = exec(
        db,
        "INSERT OR IGNORE INTO pending_transactions
           (user_id, source, source_id, sender, subject, bank, kind, amount_cents,
            currency_code, merchant, card_last4, occurred_at, occurred_time)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
        jsv![
            uid,
            source,
            source_id,
            sender_address(from),
            subject,
            notice.bank,
            notice.kind.as_str(),
            notice.amount_cents,
            notice.currency,
            notice.merchant.as_deref().map(|m| truncate(m, 80)),
            notice.card_last4,
            occurred_at,
            notice.occurred_time,
        ],
    )
    .await?;
    if changes(&res) == 0 {
        return Ok(false);
    }
    Ok(true)
}

fn truncate(s: &str, max: usize) -> String {
    s.trim().chars().take(max).collect()
}

/// Is this sender one of the user's banks (built-in or added)?
fn is_bank_sender(senders: &[BankSender], from: &str) -> bool {
    known_bank(from).is_some() || senders.iter().any(|s| sender_matches(from, &s.pattern))
}

// ------------------------------------------------------- settings / lists --

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BankSender {
    pub id: i64,
    pub pattern: String,
    pub wallet_id: Option<i64>,
}

#[derive(Deserialize)]
struct SenderRow {
    id: i64,
    pattern: String,
    wallet_id: Option<i64>,
}

async fn list_senders(db: &D1Database, uid: i64) -> AppResult<Vec<BankSender>> {
    Ok(all::<SenderRow>(
        db,
        "SELECT id, pattern, wallet_id FROM bank_senders WHERE user_id = ?1 ORDER BY pattern",
        jsv![uid],
    )
    .await?
    .into_iter()
    .map(|r| BankSender {
        id: r.id,
        pattern: r.pattern,
        wallet_id: r.wallet_id,
    })
    .collect())
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CardWallet {
    pub card_last4: String,
    pub wallet_id: i64,
}

#[derive(Deserialize)]
struct CardRow {
    card_last4: String,
    wallet_id: i64,
}

async fn list_cards(db: &D1Database, uid: i64) -> AppResult<Vec<CardWallet>> {
    Ok(all::<CardRow>(
        db,
        "SELECT c.card_last4, c.wallet_id FROM card_wallets c
         JOIN wallets w ON w.id = c.wallet_id AND w.user_id = ?1
         WHERE c.user_id = ?1 ORDER BY c.card_last4",
        jsv![uid],
    )
    .await?
    .into_iter()
    .map(|r| CardWallet {
        card_last4: r.card_last4,
        wallet_id: r.wallet_id,
    })
    .collect())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct KnownBank {
    pub key: &'static str,
    pub name: &'static str,
    pub domains: &'static [&'static str],
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GmailStatus {
    pub email: String,
    pub connected_at: String,
    pub last_push_at: Option<String>,
    pub needs_reconnect: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BankNoticeSettings {
    /// This deployment has Gmail configured.
    pub gmail_available: bool,
    pub gmail: Option<GmailStatus>,
    pub known_banks: Vec<KnownBank>,
    pub senders: Vec<BankSender>,
    pub cards: Vec<CardWallet>,
}

#[derive(Deserialize)]
struct ConnRow {
    email: String,
    created_at: String,
    last_push_at: Option<String>,
    needs_reconnect: i64,
}

pub async fn get_bank_notice_settings(
    db: &D1Database,
    uid: i64,
    gmail_available: bool,
) -> AppResult<BankNoticeSettings> {
    let conn: Option<ConnRow> = first(
        db,
        "SELECT email, created_at, last_push_at, needs_reconnect
         FROM gmail_connections WHERE user_id = ?1",
        jsv![uid],
    )
    .await?;
    Ok(BankNoticeSettings {
        gmail_available,
        gmail: conn.map(|c| GmailStatus {
            email: c.email,
            connected_at: c.created_at,
            last_push_at: c.last_push_at,
            needs_reconnect: c.needs_reconnect != 0,
        }),
        known_banks: KNOWN_BANKS
            .iter()
            .map(|b| KnownBank {
                key: b.key,
                name: b.name,
                domains: b.domains,
            })
            .collect(),
        senders: list_senders(db, uid).await?,
        cards: list_cards(db, uid).await?,
    })
}

async fn check_wallet(db: &D1Database, uid: i64, wallet_id: Option<i64>) -> AppResult<()> {
    if let Some(w) = wallet_id {
        let n = first::<crate::db::CountRow>(
            db,
            "SELECT COUNT(*) AS n FROM wallets WHERE id = ?1 AND user_id = ?2",
            jsv![w, uid],
        )
        .await?
        .map(|r| r.n)
        .unwrap_or(0);
        if n == 0 {
            return Err(AppError::NotFound("cartera"));
        }
    }
    Ok(())
}

/// `banco.com`, `@banco.com` or `alertas@banco.com`, lowercased.
fn normalize_pattern(raw: &str) -> AppResult<String> {
    let p = sender_address(raw).trim_start_matches('@').to_string();
    let valid = !p.is_empty()
        && p.len() <= 120
        && p.contains('.')
        && !p.contains(char::is_whitespace)
        && p.matches('@').count() <= 1
        && !p.ends_with('.')
        && p.rsplit('@').next().is_some_and(|d| d.contains('.'));
    if !valid {
        return Err(AppError::InvalidInput(
            "escribe un correo (alertas@banco.com) o un dominio (banco.com)".into(),
        ));
    }
    Ok(p)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AddSenderArgs {
    pub pattern: String,
    #[serde(default)]
    pub wallet_id: Option<i64>,
}

pub async fn add_bank_sender(db: &D1Database, uid: i64, a: AddSenderArgs) -> AppResult<i64> {
    let pattern = normalize_pattern(&a.pattern)?;
    check_wallet(db, uid, a.wallet_id).await?;
    exec(
        db,
        "INSERT INTO bank_senders (user_id, pattern, wallet_id) VALUES (?1, ?2, ?3)
         ON CONFLICT (user_id, pattern) DO UPDATE SET wallet_id = excluded.wallet_id",
        jsv![uid, pattern, a.wallet_id],
    )
    .await?;
    #[derive(Deserialize)]
    struct IdRow {
        id: i64,
    }
    Ok(first::<IdRow>(
        db,
        "SELECT id FROM bank_senders WHERE user_id = ?1 AND pattern = ?2",
        jsv![uid, pattern],
    )
    .await?
    .map(|r| r.id)
    .unwrap_or(0))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateSenderArgs {
    pub id: i64,
    pub wallet_id: Option<i64>,
}

pub async fn update_bank_sender(db: &D1Database, uid: i64, a: UpdateSenderArgs) -> AppResult<()> {
    check_wallet(db, uid, a.wallet_id).await?;
    let res = exec(
        db,
        "UPDATE bank_senders SET wallet_id = ?3 WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, a.wallet_id],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("remitente"));
    }
    Ok(())
}

#[derive(Deserialize)]
pub struct IdArgs {
    pub id: i64,
}

pub async fn delete_bank_sender(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    exec(
        db,
        "DELETE FROM bank_senders WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?;
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CardArgs {
    pub card_last4: String,
    /// None removes the mapping.
    pub wallet_id: Option<i64>,
}

fn validate_last4(s: &str) -> AppResult<String> {
    let s = s.trim();
    if s.len() == 4 && s.chars().all(|c| c.is_ascii_digit()) {
        Ok(s.to_string())
    } else {
        Err(AppError::InvalidInput(
            "la terminación son los últimos 4 dígitos".into(),
        ))
    }
}

pub async fn set_card_wallet(db: &D1Database, uid: i64, a: CardArgs) -> AppResult<()> {
    let last4 = validate_last4(&a.card_last4)?;
    match a.wallet_id {
        Some(w) => {
            check_wallet(db, uid, Some(w)).await?;
            exec(
                db,
                "INSERT INTO card_wallets (user_id, card_last4, wallet_id) VALUES (?1, ?2, ?3)
                 ON CONFLICT (user_id, card_last4) DO UPDATE SET wallet_id = excluded.wallet_id",
                jsv![uid, last4, w],
            )
            .await?;
        }
        None => {
            exec(
                db,
                "DELETE FROM card_wallets WHERE user_id = ?1 AND card_last4 = ?2",
                jsv![uid, last4],
            )
            .await?;
        }
    }
    Ok(())
}

// ---------------------------------------------------------------- inbox --

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DuplicateRef {
    pub id: i64,
    pub description: Option<String>,
    pub occurred_at: String,
    pub wallet_name: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PendingTx {
    pub id: i64,
    pub source: String,
    pub sender: String,
    pub subject: Option<String>,
    pub bank_name: Option<String>,
    pub kind: String,
    pub amount_cents: i64,
    pub currency_code: String,
    pub merchant: Option<String>,
    pub card_last4: Option<String>,
    pub occurred_at: String,
    pub occurred_time: Option<String>,
    pub suggested_wallet_id: Option<i64>,
    pub suggested_category_id: Option<i64>,
    pub duplicate_of: Option<DuplicateRef>,
}

#[derive(Deserialize)]
struct PendingRow {
    id: i64,
    source: String,
    sender: String,
    subject: Option<String>,
    bank: Option<String>,
    kind: String,
    amount_cents: i64,
    currency_code: String,
    merchant: Option<String>,
    card_last4: Option<String>,
    occurred_at: String,
    occurred_time: Option<String>,
    card_wallet_id: Option<i64>,
    suggested_category_id: Option<i64>,
}

#[derive(Deserialize)]
struct CandidateRow {
    id: i64,
    wallet_id: i64,
    wallet_name: String,
    kind: String,
    amount_cents: i64,
    occurred_at: String,
    description: Option<String>,
}

pub async fn list_pending_transactions(db: &D1Database, uid: i64) -> AppResult<Vec<PendingTx>> {
    let rows: Vec<PendingRow> = all(
        db,
        "SELECT p.id, p.source, p.sender, p.subject, p.bank, p.kind, p.amount_cents,
                p.currency_code, p.merchant, p.card_last4, p.occurred_at, p.occurred_time,
                (SELECT c.wallet_id FROM card_wallets c
                   JOIN wallets cw ON cw.id = c.wallet_id AND cw.is_archived = 0
                  WHERE c.user_id = p.user_id AND c.card_last4 = p.card_last4) AS card_wallet_id,
                (SELECT t.category_id FROM transactions t
                   JOIN wallets w ON w.id = t.wallet_id AND w.user_id = p.user_id
                  WHERE p.merchant IS NOT NULL AND t.kind = p.kind
                    AND t.category_id IS NOT NULL
                    AND lower(t.description) = lower(p.merchant)
                  ORDER BY t.occurred_at DESC, t.id DESC LIMIT 1) AS suggested_category_id
         FROM pending_transactions p
         WHERE p.user_id = ?1 AND p.status = 'pending'
         ORDER BY p.occurred_at DESC, p.id DESC
         LIMIT 200",
        jsv![uid],
    )
    .await?;
    if rows.is_empty() {
        return Ok(vec![]);
    }
    let senders = list_senders(db, uid).await?;

    // One query for every duplicate candidate in the window around the inbox.
    let min = rows
        .iter()
        .map(|r| r.occurred_at.as_str())
        .min()
        .unwrap_or_default();
    let max = rows
        .iter()
        .map(|r| r.occurred_at.as_str())
        .max()
        .unwrap_or_default();
    let window = format!("{DUPLICATE_WINDOW_DAYS} days");
    let candidates: Vec<CandidateRow> = all(
        db,
        "SELECT t.id, t.wallet_id, w.name AS wallet_name, t.kind, t.amount_cents,
                t.occurred_at, t.description
         FROM transactions t JOIN wallets w ON w.id = t.wallet_id AND w.user_id = ?1
         WHERE t.occurred_at BETWEEN date(?2, '-' || ?4) AND date(?3, '+' || ?4)",
        jsv![uid, min, max, window],
    )
    .await?;

    Ok(rows
        .into_iter()
        .map(|r| {
            let sender_wallet = senders
                .iter()
                .filter(|s| s.wallet_id.is_some())
                .find(|s| sender_matches(&r.sender, &s.pattern))
                .and_then(|s| s.wallet_id);
            let suggested_wallet_id = r.card_wallet_id.or(sender_wallet);
            let kind = if r.kind == "income" {
                NoticeKind::Income
            } else {
                NoticeKind::Expense
            };
            let duplicate_of = candidates
                .iter()
                .filter(|c| suggested_wallet_id.is_none_or(|w| w == c.wallet_id))
                .find(|c| {
                    looks_like_same_movement(
                        kind,
                        r.amount_cents,
                        &r.occurred_at,
                        &c.kind,
                        c.amount_cents,
                        &c.occurred_at,
                    )
                })
                .map(|c| DuplicateRef {
                    id: c.id,
                    description: c.description.clone(),
                    occurred_at: c.occurred_at.clone(),
                    wallet_name: c.wallet_name.clone(),
                });
            PendingTx {
                id: r.id,
                source: r.source,
                bank_name: r
                    .bank
                    .as_deref()
                    .and_then(|k| KNOWN_BANKS.iter().find(|b| b.key == k))
                    .map(|b| b.name.to_string()),
                sender: r.sender,
                subject: r.subject,
                kind: r.kind,
                amount_cents: r.amount_cents,
                currency_code: r.currency_code,
                merchant: r.merchant,
                card_last4: r.card_last4,
                occurred_at: r.occurred_at,
                occurred_time: r.occurred_time,
                suggested_wallet_id,
                suggested_category_id: r.suggested_category_id,
                duplicate_of,
            }
        })
        .collect())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfirmArgs {
    pub id: i64,
    pub wallet_id: i64,
    pub amount_cents: i64,
    pub category_id: Option<i64>,
    pub description: Option<String>,
    pub occurred_at: String,
    #[serde(default)]
    pub occurred_time: Option<String>,
    /// Remember this card ending → wallet for the next notices.
    #[serde(default)]
    pub remember_card: bool,
}

#[derive(Deserialize)]
struct PendingKindRow {
    kind: String,
    card_last4: Option<String>,
    status: String,
    transaction_id: Option<i64>,
}

/// Books the movement (the user may have corrected any field) and resolves the
/// pending row. Retry-safe: the transaction's client_id is `pending-<id>`, so
/// a confirm whose response got lost books nothing new the second time.
pub async fn confirm_pending_transaction(
    db: &D1Database,
    uid: i64,
    a: ConfirmArgs,
) -> AppResult<i64> {
    let p: PendingKindRow = first(
        db,
        "SELECT kind, card_last4, status, transaction_id FROM pending_transactions
         WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid],
    )
    .await?
    .ok_or(AppError::NotFound("aviso"))?;
    match p.status.as_str() {
        "confirmed" => {
            if let Some(tx) = p.transaction_id {
                return Ok(tx);
            }
            return Err(AppError::InvalidInput("este aviso ya se registró".into()));
        }
        "dismissed" => return Err(AppError::InvalidInput("este aviso se descartó".into())),
        _ => {}
    }
    let tx = SimpleTxArgs {
        wallet_id: a.wallet_id,
        amount_cents: a.amount_cents,
        category_id: a.category_id,
        description: a
            .description
            .map(|d| d.trim().to_string())
            .filter(|d| !d.is_empty()),
        occurred_at: a.occurred_at,
        occurred_time: a.occurred_time,
        client_id: Some(format!("pending-{}", a.id)),
        subscription_id: None,
        refund_of_id: None,
    };
    let tx_id = if p.kind == "income" {
        transactions::add_income(db, uid, tx).await?
    } else {
        transactions::add_expense(db, uid, tx).await?
    };
    exec(
        db,
        "UPDATE pending_transactions
         SET status = 'confirmed', transaction_id = ?3, resolved_at = datetime('now')
         WHERE id = ?1 AND user_id = ?2",
        jsv![a.id, uid, tx_id],
    )
    .await?;
    if a.remember_card {
        if let Some(last4) = p.card_last4 {
            set_card_wallet(
                db,
                uid,
                CardArgs {
                    card_last4: last4,
                    wallet_id: Some(a.wallet_id),
                },
            )
            .await?;
        }
    }
    Ok(tx_id)
}

pub async fn dismiss_pending_transaction(db: &D1Database, uid: i64, a: IdArgs) -> AppResult<()> {
    let res = exec(
        db,
        "UPDATE pending_transactions SET status = 'dismissed', resolved_at = datetime('now')
         WHERE id = ?1 AND user_id = ?2 AND status = 'pending'",
        jsv![a.id, uid],
    )
    .await?;
    if changes(&res) == 0 {
        return Err(AppError::NotFound("aviso"));
    }
    Ok(())
}

// ---------------------------------------------------------------- Gmail --

#[derive(Deserialize)]
struct TokenRow {
    user_id: i64,
    refresh_token_enc: String,
    access_token_enc: Option<String>,
    access_expires_at: Option<i64>,
    history_id: Option<String>,
}

fn now_secs() -> i64 {
    (worker::Date::now().as_millis() / 1000) as i64
}

/// A usable access token: the cached one, or a refreshed one (cached again).
/// None = Google refused the refresh token; the connection is flagged.
async fn access_token(
    db: &D1Database,
    cfg: &GmailConfig,
    c: &TokenRow,
) -> AppResult<Option<String>> {
    if let (Some(enc), Some(exp)) = (&c.access_token_enc, c.access_expires_at) {
        if exp > now_secs() + 60 {
            return Ok(Some(cfg.open(enc).await?));
        }
    }
    let refresh = cfg.open(&c.refresh_token_enc).await?;
    match cfg.refresh(&refresh).await? {
        Ok(t) => {
            let sealed = cfg.seal(&t.access_token).await?;
            exec(
                db,
                "UPDATE gmail_connections
                 SET access_token_enc = ?2, access_expires_at = ?3, needs_reconnect = 0
                 WHERE user_id = ?1",
                jsv![c.user_id, sealed, now_secs() + t.expires_in.unwrap_or(3600)],
            )
            .await?;
            Ok(Some(t.access_token))
        }
        Err(HttpStatus(400 | 401)) => {
            exec(
                db,
                "UPDATE gmail_connections SET needs_reconnect = 1, access_token_enc = NULL
                 WHERE user_id = ?1",
                jsv![c.user_id],
            )
            .await?;
            Ok(None)
        }
        Err(HttpStatus(s)) => Err(AppError::Internal(format!("refresh de Google falló ({s})"))),
    }
}

/// Store a fresh grant: who the mailbox is, where its history starts, and the
/// watch that makes Gmail push to us. Shared by the web redirect flow and the
/// Android server-auth-code flow.
pub async fn save_gmail_grant(
    db: &D1Database,
    cfg: &GmailConfig,
    uid: i64,
    tokens: Tokens,
) -> AppResult<()> {
    if !tokens
        .scope
        .as_deref()
        .is_some_and(|s| s.split(' ').any(|x| x == crate::gmail::SCOPE))
    {
        return Err(AppError::InvalidInput(
            "falta el permiso para leer los correos: marca la casilla de Gmail al autorizar".into(),
        ));
    }
    let refresh = match tokens.refresh_token {
        Some(r) => r,
        None => {
            // Google only hands out a refresh token on first consent; a
            // reconnect keeps the one we already hold.
            #[derive(Deserialize)]
            struct R {
                refresh_token_enc: String,
            }
            let existing: Option<R> = first(
                db,
                "SELECT refresh_token_enc FROM gmail_connections WHERE user_id = ?1",
                jsv![uid],
            )
            .await?;
            match existing {
                Some(r) => cfg.open(&r.refresh_token_enc).await?,
                None => {
                    return Err(AppError::InvalidInput(
                        "Google no dio acceso permanente: quita el acceso de Finanzas en tu cuenta de Google y vuelve a conectar".into(),
                    ))
                }
            }
        }
    };
    let profile = cfg.profile(&tokens.access_token).await?;
    let watch = match cfg.watch(&tokens.access_token).await? {
        Ok(w) => w,
        Err(s) => {
            return Err(AppError::Internal(format!(
                "Gmail no aceptó el aviso automático ({})",
                s.0
            )))
        }
    };
    let watch_exp = watch.expiration.parse::<i64>().unwrap_or(0) / 1000;
    let refresh_enc = cfg.seal(&refresh).await?;
    let access_enc = cfg.seal(&tokens.access_token).await?;
    exec(
        db,
        "INSERT INTO gmail_connections
           (user_id, email, refresh_token_enc, access_token_enc, access_expires_at,
            history_id, watch_expires_at, needs_reconnect)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 0)
         ON CONFLICT (user_id) DO UPDATE SET
           email = excluded.email, refresh_token_enc = excluded.refresh_token_enc,
           access_token_enc = excluded.access_token_enc,
           access_expires_at = excluded.access_expires_at,
           history_id = excluded.history_id, watch_expires_at = excluded.watch_expires_at,
           needs_reconnect = 0",
        jsv![
            uid,
            profile.email_address.to_ascii_lowercase(),
            refresh_enc,
            access_enc,
            now_secs() + tokens.expires_in.unwrap_or(3600),
            watch.history_id,
            watch_exp,
        ],
    )
    .await?;
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GmailCodeArgs {
    pub code: String,
}

/// Android: the app gets a server auth code from Google's AuthorizationClient.
pub async fn connect_gmail_code(
    db: &D1Database,
    cfg: &GmailConfig,
    uid: i64,
    a: GmailCodeArgs,
) -> AppResult<()> {
    let tokens = cfg.exchange_code(&a.code, "").await?;
    save_gmail_grant(db, cfg, uid, tokens).await
}

pub async fn disconnect_gmail(
    db: &D1Database,
    cfg: Option<&GmailConfig>,
    uid: i64,
) -> AppResult<()> {
    if let Some(cfg) = cfg {
        let row: Option<TokenRow> = first(
            db,
            "SELECT user_id, refresh_token_enc, access_token_enc, access_expires_at, history_id
             FROM gmail_connections WHERE user_id = ?1",
            jsv![uid],
        )
        .await?;
        if let Some(c) = row {
            if let Ok(Some(access)) = access_token(db, cfg, &c).await {
                cfg.stop(&access).await;
            }
            if let Ok(refresh) = cfg.open(&c.refresh_token_enc).await {
                cfg.revoke(&refresh).await;
            }
        }
    }
    exec(
        db,
        "DELETE FROM gmail_connections WHERE user_id = ?1",
        jsv![uid],
    )
    .await?;
    Ok(())
}

/// History records one push reads. Each message costs one or two fetches
/// (headers, then the body if it's a bank's), on top of the token refresh and
/// the history call — 10 records keep the worst case well under the free
/// plan's 50 subrequests and 50 D1 queries per invocation.
const PUSH_RECORDS: u32 = 10;

/// What a push did for one mailbox: notices added, and the cursor to store.
struct PushRead {
    added: usize,
    cursor: Option<String>,
}

/// Read one page of the mailbox's history from its stored cursor. The cursor
/// moves record by record: a record counts as read only once all its messages
/// were, so a failure half way is retried from that record by the next push,
/// and nothing before it is read twice (a re-read would be harmless anyway —
/// pending rows are UNIQUE per message).
async fn read_mailbox(
    db: &D1Database,
    cfg: &GmailConfig,
    c: &TokenRow,
    pushed_history: &str,
) -> AppResult<PushRead> {
    let Some(access) = access_token(db, cfg, c).await? else {
        return Ok(PushRead {
            added: 0,
            cursor: None,
        });
    };
    let start = c
        .history_id
        .clone()
        .unwrap_or_else(|| pushed_history.into());
    let page = match cfg.history(&access, &start, PUSH_RECORDS).await? {
        Ok(p) => p,
        // cursor too old to replay: restart from this push
        Err(HttpStatus(404)) => {
            return Ok(PushRead {
                added: 0,
                cursor: Some(pushed_history.to_string()),
            })
        }
        Err(HttpStatus(s)) => {
            return Err(AppError::Internal(format!("history de Gmail falló ({s})")))
        }
    };
    let senders = list_senders(db, c.user_id).await?;
    let mut added = 0;
    let mut cursor = None;
    let mut complete = true;
    'records: for (record_id, ids) in &page.records {
        for id in ids {
            let step: AppResult<bool> = async {
                let Some(meta) = cfg.headers(&access, id).await? else {
                    return Ok(false);
                };
                if meta.is_marketing() || !is_bank_sender(&senders, &meta.from) {
                    return Ok(false);
                }
                let Some(mail) = cfg.message(&access, id).await? else {
                    return Ok(false);
                };
                let received = chrono::DateTime::from_timestamp_millis(
                    mail.internal_date_ms - 6 * 3600 * 1000,
                )
                .map(|d| d.naive_utc());
                let r = ingest(
                    db,
                    c.user_id,
                    "gmail",
                    id,
                    &mail.from,
                    &mail.subject,
                    &mail.body,
                    received,
                )
                .await?;
                Ok(r)
            }
            .await;
            match step {
                Ok(true) => added += 1,
                Ok(false) => {}
                Err(e) => {
                    worker::console_error!("gmail message {id} (user {}): {e}", c.user_id);
                    complete = false;
                    break 'records;
                }
            }
        }
        cursor = Some(record_id.clone());
    }
    // Everything on the page read and nothing after it: jump to the mailbox's
    // own cursor, past records that added nothing to INBOX.
    if complete && !page.more {
        cursor = page.history_id.or(cursor);
    }
    Ok(PushRead { added, cursor })
}

/// One Pub/Sub push for a mailbox. Each connection is read on its own: one
/// failing (a revoked grant, a Gmail hiccup) doesn't stop the others.
pub async fn process_push(
    db: &D1Database,
    cfg: &GmailConfig,
    email: &str,
    pushed_history: &str,
) -> AppResult<usize> {
    let conns: Vec<TokenRow> = all(
        db,
        "SELECT user_id, refresh_token_enc, access_token_enc, access_expires_at, history_id
         FROM gmail_connections WHERE email = ?1 AND needs_reconnect = 0",
        jsv![email],
    )
    .await?;
    let mut added = 0;
    for c in conns {
        match read_mailbox(db, cfg, &c, pushed_history).await {
            Ok(r) => {
                added += r.added;
                exec(
                    db,
                    "UPDATE gmail_connections
                     SET history_id = COALESCE(?2, history_id), last_push_at = datetime('now')
                     WHERE user_id = ?1",
                    jsv![c.user_id, r.cursor],
                )
                .await?;
            }
            Err(e) => worker::console_error!("gmail push for user {} failed: {e}", c.user_id),
        }
    }
    Ok(added)
}

/// Watches renewed per cron run, the soonest to expire first.
const RENEW_PER_RUN: i64 = 3;
/// A watch is renewed once it has less than this left (it lasts 7 days).
const RENEW_WITHIN_SECS: i64 = 2 * 24 * 3600;

/// Runs on the frequent cron: renews the few watches closest to expiring. A
/// handful per run keeps every invocation small (two fetches each), and with a
/// run every 5 minutes there are hundreds of slots a day — a watch with two
/// days left is always renewed long before it lapses.
pub async fn renew_gmail_watches(db: &D1Database, cfg: &GmailConfig) -> AppResult<()> {
    let conns: Vec<TokenRow> = all(
        db,
        "SELECT user_id, refresh_token_enc, access_token_enc, access_expires_at, history_id
         FROM gmail_connections
         WHERE needs_reconnect = 0
           AND (watch_expires_at IS NULL OR watch_expires_at < ?1)
         ORDER BY watch_expires_at
         LIMIT ?2",
        jsv![now_secs() + RENEW_WITHIN_SECS, RENEW_PER_RUN],
    )
    .await?;
    for c in conns {
        let result: AppResult<()> = async {
            let Some(access) = access_token(db, cfg, &c).await? else {
                return Ok(());
            };
            if let Ok(w) = cfg.watch(&access).await? {
                exec(
                    db,
                    "UPDATE gmail_connections SET watch_expires_at = ?2,
                       history_id = COALESCE(history_id, ?3)
                     WHERE user_id = ?1",
                    jsv![
                        c.user_id,
                        w.expiration.parse::<i64>().unwrap_or(0) / 1000,
                        w.history_id
                    ],
                )
                .await?;
            }
            Ok(())
        }
        .await;
        if let Err(e) = result {
            worker::console_error!("gmail watch renewal failed for user {}: {e}", c.user_id);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sender_patterns() {
        assert_eq!(normalize_pattern("Banorte.com").unwrap(), "banorte.com");
        assert_eq!(normalize_pattern("@banorte.com").unwrap(), "banorte.com");
        assert_eq!(
            normalize_pattern("Alertas <ALERTAS@banorte.com>").unwrap(),
            "alertas@banorte.com"
        );
        assert!(normalize_pattern("banorte").is_err());
        assert!(normalize_pattern("a b.com").is_err());
        assert!(normalize_pattern("x@banorte").is_err());
        assert!(normalize_pattern("").is_err());
    }

    #[test]
    fn last4() {
        assert_eq!(validate_last4(" 1234 ").unwrap(), "1234");
        assert!(validate_last4("123").is_err());
        assert!(validate_last4("12a4").is_err());
    }
}
