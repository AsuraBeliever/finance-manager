//! Accounts and cookie sessions.
//!
//! - Registration is gated by an invite code (secret INVITE_CODE) so a public
//!   URL doesn't accumulate strangers' accounts.
//! - Session token: 32 random bytes (hex) in an HttpOnly cookie; D1 stores
//!   only its SHA-256, so a DB leak can't impersonate sessions.
//! - Sliding expiry: 30 days, refreshed on use (at most once a day).
//! - CSRF: SameSite=Lax cookie + Origin check on mutating routes.

pub mod google;
pub mod password;

use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::{D1Database, Request, Response, RouteContext};

use crate::db::{exec, first, random_bytes, sha256_hex};
use crate::error::{db_err, error_response, json_response};
use crate::jsv;

const SESSION_COOKIE: &str = "session";
pub(crate) const SESSION_MAX_AGE_SECS: i64 = 30 * 24 * 3600;

// ---- session plumbing ----

/// Value of a named cookie on the request, if present.
pub(crate) fn cookie(req: &Request, name: &str) -> Option<String> {
    let header = req.headers().get("Cookie").ok()??;
    header.split(';').find_map(|part| {
        let (k, v) = part.trim().split_once('=')?;
        (k == name).then(|| v.to_string())
    })
}

fn cookie_token(req: &Request) -> Option<String> {
    cookie(req, SESSION_COOKIE)
}

/// SHA-256 of the request's session token, as stored in the sessions table.
fn current_token_hash(req: &Request) -> Option<String> {
    cookie_token(req).map(|t| sha256_hex(t.as_bytes()))
}

pub(crate) fn user_agent(req: &Request) -> Option<String> {
    let mut ua = req.headers().get("User-Agent").ok().flatten()?;
    ua.truncate(256);
    Some(ua)
}

#[derive(Deserialize)]
struct SessionRow {
    user_id: i64,
}

/// Resolve the request's session cookie to a user id. The same throttled
/// write (at most ~1/hour per session) refreshes the sliding expiry and the
/// device's last_seen_at.
pub async fn user_from_request(req: &Request, db: &D1Database) -> AppResult<Option<i64>> {
    let Some(token) = cookie_token(req) else {
        return Ok(None);
    };
    let hash = sha256_hex(token.as_bytes());
    let row: Option<SessionRow> = first(
        db,
        "SELECT user_id FROM sessions
         WHERE token_hash = ?1 AND expires_at > datetime('now')",
        jsv![hash],
    )
    .await?;
    let Some(row) = row else { return Ok(None) };
    exec(
        db,
        "UPDATE sessions
         SET expires_at = datetime('now', '+30 days'), last_seen_at = datetime('now')
         WHERE token_hash = ?1
           AND (expires_at < datetime('now', '+29 days')
                OR last_seen_at IS NULL
                OR last_seen_at < datetime('now', '-1 hour'))",
        jsv![hash],
    )
    .await?;
    Ok(Some(row.user_id))
}

pub async fn require_user(req: &Request, db: &D1Database) -> AppResult<i64> {
    user_from_request(req, db)
        .await?
        .ok_or_else(|| AppError::Unauthorized("no autenticado".into()))
}

/// Mutating routes must come from our own origin (defense in depth on top of
/// SameSite=Lax).
pub fn check_origin(req: &Request) -> AppResult<()> {
    let origin = match req.headers().get("Origin").ok().flatten() {
        Some(o) if !o.is_empty() && o != "null" => o,
        _ => return Ok(()), // same-origin fetches may omit it (and curl does)
    };
    let url = req.url().map_err(db_err)?;
    let host = url.host_str().unwrap_or_default();
    let origin_host = origin
        .strip_prefix("https://")
        .or_else(|| origin.strip_prefix("http://"))
        .unwrap_or(&origin)
        .split('/')
        .next()
        .unwrap_or_default()
        // ignore the port: wrangler dev runs on :8787
        .split(':')
        .next()
        .unwrap_or_default();
    let host = host.split(':').next().unwrap_or_default();
    if origin_host == host {
        Ok(())
    } else {
        Err(AppError::Unauthorized("origen no permitido".into()))
    }
}

pub(crate) async fn create_session(
    db: &D1Database,
    user_id: i64,
    user_agent: Option<String>,
) -> AppResult<String> {
    let token = hex::encode(random_bytes(32));
    exec(
        db,
        "INSERT INTO sessions (user_id, token_hash, expires_at, user_agent, last_seen_at)
         VALUES (?1, ?2, datetime('now', '+30 days'), ?3, datetime('now'))",
        jsv![user_id, sha256_hex(token.as_bytes()), user_agent],
    )
    .await?;
    Ok(token)
}

pub(crate) fn session_cookie(token: &str, max_age: i64) -> String {
    format!("{SESSION_COOKIE}={token}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age={max_age}")
}

fn with_cookie(resp: Response, cookie: &str) -> worker::Result<Response> {
    let headers = resp.headers().clone();
    headers.set("Set-Cookie", cookie)?;
    Ok(resp.with_headers(headers))
}

// ---- endpoints ----

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UserInfo {
    id: i64,
    email: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RegisterArgs {
    email: String,
    password: String,
    invite_code: String,
}

fn pbkdf2_iterations(ctx: &RouteContext<()>) -> u32 {
    ctx.env
        .var("PBKDF2_ITERATIONS")
        .ok()
        .and_then(|v| v.to_string().parse().ok())
        .unwrap_or(password::DEFAULT_ITERATIONS)
}

fn invite_code(ctx: &RouteContext<()>) -> AppResult<String> {
    // secret in production; plain var works for local dev (.dev.vars)
    if let Ok(s) = ctx.env.secret("INVITE_CODE") {
        return Ok(s.to_string());
    }
    if let Ok(v) = ctx.env.var("INVITE_CODE") {
        return Ok(v.to_string());
    }
    Err(AppError::Internal(
        "INVITE_CODE no está configurado; regístrate tras configurarlo".into(),
    ))
}

pub async fn register(mut req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    if let Err(e) = check_origin(&req) {
        return error_response(&e);
    }
    let ua = user_agent(&req);
    let args: RegisterArgs = match req.json().await {
        Ok(a) => a,
        Err(_) => return error_response(&AppError::InvalidInput("cuerpo inválido".into())),
    };
    let result = do_register(&ctx, args, ua).await;
    match result {
        Ok((user, token)) => {
            let resp = Response::from_json(&user)?;
            with_cookie(resp, &session_cookie(&token, SESSION_MAX_AGE_SECS))
        }
        Err(e) => error_response(&e),
    }
}

async fn do_register(
    ctx: &RouteContext<()>,
    args: RegisterArgs,
    ua: Option<String>,
) -> AppResult<(UserInfo, String)> {
    let expected = invite_code(ctx)?;
    if args.invite_code.trim() != expected {
        return Err(AppError::Unauthorized(
            "código de invitación incorrecto".into(),
        ));
    }
    let email = args.email.trim().to_lowercase();
    if !email.contains('@') || email.len() < 5 {
        return Err(AppError::InvalidInput("correo inválido".into()));
    }
    if args.password.len() < 8 {
        return Err(AppError::InvalidInput(
            "la contraseña debe tener al menos 8 caracteres".into(),
        ));
    }
    let db = ctx.env.d1("DB").map_err(db_err)?;
    let hash = password::hash_password(&args.password, pbkdf2_iterations(ctx)).await?;
    let res = exec(
        &db,
        "INSERT INTO users (email, password_hash) VALUES (?1, ?2)",
        jsv![email, hash],
    )
    .await
    .map_err(|e| match e {
        AppError::Db(msg) if msg.contains("UNIQUE") => {
            AppError::InvalidInput("ya existe una cuenta con ese correo".into())
        }
        other => other,
    })?;
    let user_id = crate::db::last_row_id(&res)?;
    let token = create_session(&db, user_id, ua).await?;
    Ok((UserInfo { id: user_id, email }, token))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LoginArgs {
    email: String,
    password: String,
}

#[derive(Deserialize)]
struct UserRow {
    id: i64,
    email: String,
    password_hash: String,
}

pub async fn login(mut req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    if let Err(e) = check_origin(&req) {
        return error_response(&e);
    }
    let ua = user_agent(&req);
    let args: LoginArgs = match req.json().await {
        Ok(a) => a,
        Err(_) => return error_response(&AppError::InvalidInput("cuerpo inválido".into())),
    };
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    match do_login(&db, args, ua).await {
        Ok((user, token)) => {
            let resp = Response::from_json(&user)?;
            with_cookie(resp, &session_cookie(&token, SESSION_MAX_AGE_SECS))
        }
        Err(e) => error_response(&e),
    }
}

async fn do_login(
    db: &D1Database,
    args: LoginArgs,
    ua: Option<String>,
) -> AppResult<(UserInfo, String)> {
    // uniform error: never reveal whether the email exists
    let bad = || AppError::Unauthorized("correo o contraseña incorrectos".into());
    let email = args.email.trim().to_lowercase();
    let user: UserRow = first(
        db,
        "SELECT id, email, password_hash FROM users WHERE email = ?1 AND id != 0",
        jsv![email],
    )
    .await?
    .ok_or_else(bad)?;
    if !password::verify_password(&args.password, &user.password_hash).await? {
        return Err(bad());
    }
    let token = create_session(db, user.id, ua).await?;
    Ok((
        UserInfo {
            id: user.id,
            email: user.email,
        },
        token,
    ))
}

pub async fn logout(req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    if let Err(e) = check_origin(&req) {
        return error_response(&e);
    }
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    if let Some(token) = cookie_token(&req) {
        let hash = sha256_hex(token.as_bytes());
        if let Err(e) = exec(
            &db,
            "DELETE FROM sessions WHERE token_hash = ?1",
            jsv![hash],
        )
        .await
        {
            return error_response(&e);
        }
    }
    let resp = Response::from_json(&serde_json::json!({ "ok": true }))?;
    with_cookie(resp, &session_cookie("", 0)) // expire the cookie
}

pub async fn me(req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    let result: AppResult<UserInfo> = async {
        let uid = require_user(&req, &db).await?;
        let user: UserRow = first(
            &db,
            "SELECT id, email, password_hash FROM users WHERE id = ?1",
            jsv![uid],
        )
        .await?
        .ok_or(AppError::NotFound("usuario"))?;
        Ok(UserInfo {
            id: user.id,
            email: user.email,
        })
    }
    .await;
    json_response(result)
}

// ---- account management ----

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SessionInfo {
    id: i64,
    created_at: String,
    last_seen_at: Option<String>,
    user_agent: Option<String>,
    current: bool,
}

#[derive(Deserialize)]
struct SessionListRow {
    id: i64,
    token_hash: String,
    created_at: String,
    last_seen_at: Option<String>,
    user_agent: Option<String>,
}

/// Devices with an active session on this account.
pub async fn sessions(req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    let result: AppResult<Vec<SessionInfo>> = async {
        let uid = require_user(&req, &db).await?;
        let current_hash = current_token_hash(&req).unwrap_or_default();
        let rows: Vec<SessionListRow> = crate::db::all(
            &db,
            "SELECT id, token_hash, created_at, last_seen_at, user_agent FROM sessions
             WHERE user_id = ?1 AND expires_at > datetime('now')
             ORDER BY COALESCE(last_seen_at, created_at) DESC",
            jsv![uid],
        )
        .await?;
        Ok(rows
            .into_iter()
            .map(|r| SessionInfo {
                id: r.id,
                created_at: r.created_at,
                last_seen_at: r.last_seen_at,
                user_agent: r.user_agent,
                current: r.token_hash == current_hash,
            })
            .collect())
    }
    .await;
    json_response(result)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RevokeArgs {
    id: i64,
}

/// Close another device's session. The current one is closed via logout, so
/// revoking it here is a 404 — the UI never offers it.
pub async fn revoke_session(mut req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    if let Err(e) = check_origin(&req) {
        return error_response(&e);
    }
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    let result: AppResult<serde_json::Value> = async {
        let uid = require_user(&req, &db).await?;
        let args: RevokeArgs = req
            .json()
            .await
            .map_err(|_| AppError::InvalidInput("cuerpo inválido".into()))?;
        let current_hash = current_token_hash(&req).unwrap_or_default();
        let res = exec(
            &db,
            "DELETE FROM sessions WHERE id = ?1 AND user_id = ?2 AND token_hash != ?3",
            jsv![args.id, uid, current_hash],
        )
        .await?;
        if crate::db::changes(&res) == 0 {
            return Err(AppError::NotFound("sesión"));
        }
        Ok(serde_json::json!({ "ok": true }))
    }
    .await;
    json_response(result)
}

/// Close every session except the current one.
pub async fn revoke_other_sessions(
    req: Request,
    ctx: RouteContext<()>,
) -> worker::Result<Response> {
    if let Err(e) = check_origin(&req) {
        return error_response(&e);
    }
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    let result: AppResult<serde_json::Value> = async {
        let uid = require_user(&req, &db).await?;
        let current_hash = current_token_hash(&req).unwrap_or_default();
        let res = exec(
            &db,
            "DELETE FROM sessions WHERE user_id = ?1 AND token_hash != ?2",
            jsv![uid, current_hash],
        )
        .await?;
        Ok(serde_json::json!({ "revoked": crate::db::changes(&res) }))
    }
    .await;
    json_response(result)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ChangePasswordArgs {
    current_password: String,
    new_password: String,
}

/// Change the password (requires the current one) and revoke every other
/// session — standard hygiene after a credential change.
pub async fn change_password(mut req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    if let Err(e) = check_origin(&req) {
        return error_response(&e);
    }
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    let result: AppResult<serde_json::Value> = async {
        let uid = require_user(&req, &db).await?;
        let args: ChangePasswordArgs = req
            .json()
            .await
            .map_err(|_| AppError::InvalidInput("cuerpo inválido".into()))?;
        if args.new_password.len() < 8 {
            return Err(AppError::InvalidInput(
                "la contraseña debe tener al menos 8 caracteres".into(),
            ));
        }
        let user: UserRow = first(
            &db,
            "SELECT id, email, password_hash FROM users WHERE id = ?1",
            jsv![uid],
        )
        .await?
        .ok_or(AppError::NotFound("usuario"))?;
        if !password::verify_password(&args.current_password, &user.password_hash).await? {
            return Err(AppError::Unauthorized(
                "contraseña actual incorrecta".into(),
            ));
        }
        let iterations = pbkdf2_iterations(&ctx);
        let hash = password::hash_password(&args.new_password, iterations).await?;
        exec(
            &db,
            "UPDATE users SET password_hash = ?2 WHERE id = ?1",
            jsv![uid, hash],
        )
        .await?;
        let current_hash = current_token_hash(&req).unwrap_or_default();
        exec(
            &db,
            "DELETE FROM sessions WHERE user_id = ?1 AND token_hash != ?2",
            jsv![uid, current_hash],
        )
        .await?;
        Ok(serde_json::json!({ "ok": true }))
    }
    .await;
    json_response(result)
}
