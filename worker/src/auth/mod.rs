//! Accounts and cookie sessions.
//!
//! - Registration is gated by an invite code (secret INVITE_CODE) so a public
//!   URL doesn't accumulate strangers' accounts.
//! - Session token: 32 random bytes (hex) in an HttpOnly cookie; D1 stores
//!   only its SHA-256, so a DB leak can't impersonate sessions.
//! - Sliding expiry: 30 days, refreshed on use (at most once a day).
//! - CSRF: SameSite=Lax cookie + Origin check on mutating routes.

pub mod password;

use finanzas_core::error::{AppError, AppResult};
use serde::{Deserialize, Serialize};
use worker::{D1Database, Request, Response, RouteContext};

use crate::db::{exec, first, random_bytes, sha256_hex};
use crate::error::{db_err, error_response, json_response};
use crate::jsv;

const SESSION_COOKIE: &str = "session";
const SESSION_MAX_AGE_SECS: i64 = 30 * 24 * 3600;

// ---- session plumbing ----

fn cookie_token(req: &Request) -> Option<String> {
    let header = req.headers().get("Cookie").ok()??;
    header.split(';').find_map(|part| {
        let (name, value) = part.trim().split_once('=')?;
        (name == SESSION_COOKIE).then(|| value.to_string())
    })
}

#[derive(Deserialize)]
struct SessionRow {
    user_id: i64,
}

/// Resolve the request's session cookie to a user id, refreshing the sliding
/// expiry at most once a day.
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
        "UPDATE sessions SET expires_at = datetime('now', '+30 days')
         WHERE token_hash = ?1 AND expires_at < datetime('now', '+29 days')",
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

async fn create_session(db: &D1Database, user_id: i64) -> AppResult<String> {
    let token = hex::encode(random_bytes(32));
    exec(
        db,
        "INSERT INTO sessions (user_id, token_hash, expires_at)
         VALUES (?1, ?2, datetime('now', '+30 days'))",
        jsv![user_id, sha256_hex(token.as_bytes())],
    )
    .await?;
    Ok(token)
}

fn session_cookie(token: &str, max_age: i64) -> String {
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
    let args: RegisterArgs = match req.json().await {
        Ok(a) => a,
        Err(_) => return error_response(&AppError::InvalidInput("cuerpo inválido".into())),
    };
    let result = do_register(&ctx, args).await;
    match result {
        Ok((user, token)) => {
            let resp = Response::from_json(&user)?;
            with_cookie(resp, &session_cookie(&token, SESSION_MAX_AGE_SECS))
        }
        Err(e) => error_response(&e),
    }
}

async fn do_register(ctx: &RouteContext<()>, args: RegisterArgs) -> AppResult<(UserInfo, String)> {
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
    let token = create_session(&db, user_id).await?;
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
    let args: LoginArgs = match req.json().await {
        Ok(a) => a,
        Err(_) => return error_response(&AppError::InvalidInput("cuerpo inválido".into())),
    };
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    match do_login(&db, args).await {
        Ok((user, token)) => {
            let resp = Response::from_json(&user)?;
            with_cookie(resp, &session_cookie(&token, SESSION_MAX_AGE_SECS))
        }
        Err(e) => error_response(&e),
    }
}

async fn do_login(db: &D1Database, args: LoginArgs) -> AppResult<(UserInfo, String)> {
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
    let token = create_session(db, user.id).await?;
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
