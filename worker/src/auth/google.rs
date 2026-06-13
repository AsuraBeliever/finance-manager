//! Sign in with Google (OAuth 2.0 Authorization Code, server-side).
//!
//! Flow: /start sets a CSRF `state` cookie and 302s to Google's consent
//! screen; Google redirects to /callback with the code; we exchange it for an
//! id_token directly from Google's token endpoint over TLS using our client
//! secret. Because the token comes straight from that endpoint we trust it
//! without verifying the JWT signature (per Google's OIDC guidance) — we just
//! decode the payload for `sub` / `email` / `email_verified`.
//!
//! Google sign-in is open (no invite code): an unknown verified email creates
//! an account; a known email links Google to the existing password account.

use finanzas_core::error::{AppError, AppResult};
use serde::Deserialize;
use worker::{Fetch, Headers, Method, Request, RequestInit, Response, RouteContext};

use super::{cookie, create_session, session_cookie, user_agent, SESSION_MAX_AGE_SECS};
use crate::db::{exec, first, random_bytes};
use crate::error::db_err;
use crate::jsv;

const STATE_COOKIE: &str = "oauth_state";
const AUTH_URL: &str = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_URL: &str = "https://oauth2.googleapis.com/token";

fn client_id(ctx: &RouteContext<()>) -> AppResult<String> {
    ctx.env
        .var("GOOGLE_CLIENT_ID")
        .map(|v| v.to_string())
        .map_err(|_| AppError::Internal("GOOGLE_CLIENT_ID no configurado".into()))
}

fn client_secret(ctx: &RouteContext<()>) -> AppResult<String> {
    if let Ok(s) = ctx.env.secret("GOOGLE_CLIENT_SECRET") {
        return Ok(s.to_string());
    }
    ctx.env
        .var("GOOGLE_CLIENT_SECRET")
        .map(|v| v.to_string())
        .map_err(|_| AppError::Internal("GOOGLE_CLIENT_SECRET no configurado".into()))
}

/// `{scheme}://{host}` of the incoming request, so the redirect_uri matches
/// whatever origin served the page (prod or localhost) — both registered with
/// Google.
fn origin(req: &Request) -> AppResult<String> {
    let url = req.url().map_err(db_err)?;
    let scheme = url.scheme();
    let host = url
        .host_str()
        .ok_or_else(|| AppError::Internal("sin host".into()))?;
    Ok(match url.port() {
        Some(p) => format!("{scheme}://{host}:{p}"),
        None => format!("{scheme}://{host}"),
    })
}

fn redirect_uri(req: &Request) -> AppResult<String> {
    Ok(format!("{}/api/auth/google/callback", origin(req)?))
}

/// Percent-encode a query/form value (unreserved chars pass through).
fn enc(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// 302 with exactly one Set-Cookie. Cloudflare's Headers folds multiple
/// Set-Cookie into one comma-joined header that browsers reject, so we never
/// emit more than one — the session cookie on success, or the state-clear on
/// failure (the state cookie also self-expires via Max-Age).
fn redirect(location: &str, set_cookie: &str) -> worker::Result<Response> {
    let headers = Headers::new();
    headers.set("Location", location)?;
    headers.set("Set-Cookie", set_cookie)?;
    Ok(Response::empty()?.with_status(302).with_headers(headers))
}

fn state_cookie(value: &str, max_age: i64) -> String {
    format!("{STATE_COOKIE}={value}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age={max_age}")
}

pub async fn start(req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    let cid = match client_id(&ctx) {
        Ok(v) => v,
        Err(e) => return crate::error::error_response(&e),
    };
    let redirect_url = match redirect_uri(&req) {
        Ok(v) => v,
        Err(e) => return crate::error::error_response(&e),
    };
    let state = hex::encode(random_bytes(16));
    let url = format!(
        "{AUTH_URL}?client_id={}&redirect_uri={}&response_type=code&scope={}&state={}&prompt=select_account",
        enc(&cid),
        enc(&redirect_url),
        enc("openid email profile"),
        enc(&state),
    );
    redirect(&url, &state_cookie(&state, 600))
}

#[derive(Deserialize)]
struct TokenResponse {
    id_token: Option<String>,
}

#[derive(Deserialize)]
struct Claims {
    sub: String,
    email: Option<String>,
    email_verified: Option<bool>,
}

/// base64url (no padding) → bytes.
fn b64url_decode(input: &str) -> Option<Vec<u8>> {
    const fn val(c: u8) -> Option<u8> {
        match c {
            b'A'..=b'Z' => Some(c - b'A'),
            b'a'..=b'z' => Some(c - b'a' + 26),
            b'0'..=b'9' => Some(c - b'0' + 52),
            b'-' => Some(62),
            b'_' => Some(63),
            _ => None,
        }
    }
    let mut out = Vec::with_capacity(input.len() * 3 / 4);
    let mut buf = 0u32;
    let mut bits = 0u32;
    for &c in input.as_bytes() {
        let v = val(c)? as u32;
        buf = (buf << 6) | v;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((buf >> bits) as u8);
        }
    }
    Some(out)
}

/// Decode the (trusted) id_token payload without signature verification.
fn parse_id_token(id_token: &str) -> AppResult<Claims> {
    let payload = id_token
        .split('.')
        .nth(1)
        .ok_or_else(|| AppError::Internal("id_token malformado".into()))?;
    let bytes = b64url_decode(payload)
        .ok_or_else(|| AppError::Internal("id_token no decodifica".into()))?;
    serde_json::from_slice(&bytes)
        .map_err(|e| AppError::Internal(format!("id_token inválido: {e}")))
}

async fn exchange_code(ctx: &RouteContext<()>, code: &str, redirect: &str) -> AppResult<String> {
    let body = format!(
        "code={}&client_id={}&client_secret={}&redirect_uri={}&grant_type=authorization_code",
        enc(code),
        enc(&client_id(ctx)?),
        enc(&client_secret(ctx)?),
        enc(redirect),
    );
    let headers = Headers::new();
    headers
        .set("Content-Type", "application/x-www-form-urlencoded")
        .map_err(db_err)?;
    let mut init = RequestInit::new();
    init.with_method(Method::Post)
        .with_headers(headers)
        .with_body(Some(body.into()));
    let req = Request::new_with_init(TOKEN_URL, &init).map_err(db_err)?;
    let mut resp = Fetch::Request(req)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("intercambio con Google falló: {e}")))?;
    let token: TokenResponse = resp
        .json()
        .await
        .map_err(|e| AppError::Internal(format!("respuesta de Google inválida: {e}")))?;
    token
        .id_token
        .ok_or_else(|| AppError::Internal("Google no regresó id_token".into()))
}

/// Find by google_sub, else link to the verified email, else create.
async fn find_or_create_user(db: &worker::D1Database, sub: &str, email: &str) -> AppResult<i64> {
    #[derive(Deserialize)]
    struct IdRow {
        id: i64,
    }
    if let Some(row) =
        first::<IdRow>(db, "SELECT id FROM users WHERE google_sub = ?1", jsv![sub]).await?
    {
        return Ok(row.id);
    }
    if let Some(row) = first::<IdRow>(
        db,
        "SELECT id FROM users WHERE email = ?1 AND id != 0",
        jsv![email],
    )
    .await?
    {
        // verified Google email matches an existing account → link them
        exec(
            db,
            "UPDATE users SET google_sub = ?2 WHERE id = ?1",
            jsv![row.id, sub],
        )
        .await?;
        return Ok(row.id);
    }
    let res = exec(
        db,
        "INSERT INTO users (email, password_hash, google_sub) VALUES (?1, '!', ?2)",
        jsv![email, sub],
    )
    .await?;
    crate::db::last_row_id(&res)
}

pub async fn callback(req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    let fail = || redirect("/?authError=google", &state_cookie("", 0));

    let url = match req.url() {
        Ok(u) => u,
        Err(_) => return fail(),
    };
    let mut code = None;
    let mut state = None;
    for (k, v) in url.query_pairs() {
        match k.as_ref() {
            "code" => code = Some(v.into_owned()),
            "state" => state = Some(v.into_owned()),
            _ => {}
        }
    }
    let (Some(code), Some(state)) = (code, state) else {
        worker::console_error!("google callback: faltan code/state en el query");
        return fail();
    };
    // CSRF: the state must match the cookie we set in /start
    if cookie(&req, STATE_COOKIE).as_deref() != Some(state.as_str()) {
        worker::console_error!(
            "google callback: state no coincide (cookie {:?})",
            cookie(&req, STATE_COOKIE).is_some()
        );
        return fail();
    }

    let result: AppResult<(i64, String)> = async {
        let redirect_url = redirect_uri(&req)?;
        let id_token = exchange_code(&ctx, &code, &redirect_url).await?;
        let claims = parse_id_token(&id_token)?;
        if claims.email_verified != Some(true) {
            return Err(AppError::Unauthorized(
                "correo de Google no verificado".into(),
            ));
        }
        let email = claims
            .email
            .ok_or_else(|| AppError::Unauthorized("Google no compartió el correo".into()))?
            .trim()
            .to_lowercase();
        let db = ctx.env.d1("DB").map_err(db_err)?;
        let uid = find_or_create_user(&db, &claims.sub, &email).await?;
        let token = create_session(&db, uid, user_agent(&req)).await?;
        Ok((uid, token))
    }
    .await;

    match result {
        // one Set-Cookie only: the session. oauth_state self-expires (Max-Age).
        Ok((_, token)) => redirect("/", &session_cookie(&token, SESSION_MAX_AGE_SECS)),
        Err(e) => {
            worker::console_error!("google callback falló: {e}");
            fail()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_id_token_payload() {
        // header.payload.signature — only the payload (segment 1) is read
        let payload = r#"{"sub":"12345","email":"a@b.com","email_verified":true}"#;
        let b64 = {
            // encode as base64url no-pad
            const ABC: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
            let mut out = String::new();
            let mut buf = 0u32;
            let mut bits = 0;
            for &b in payload.as_bytes() {
                buf = (buf << 8) | b as u32;
                bits += 8;
                while bits >= 6 {
                    bits -= 6;
                    out.push(ABC[((buf >> bits) & 0x3f) as usize] as char);
                }
            }
            if bits > 0 {
                out.push(ABC[((buf << (6 - bits)) & 0x3f) as usize] as char);
            }
            out
        };
        let token = format!("HEADER.{b64}.SIG");
        let claims = parse_id_token(&token).unwrap();
        assert_eq!(claims.sub, "12345");
        assert_eq!(claims.email.as_deref(), Some("a@b.com"));
        assert_eq!(claims.email_verified, Some(true));
    }

    #[test]
    fn enc_encodes_reserved() {
        assert_eq!(enc("openid email profile"), "openid%20email%20profile");
        assert_eq!(enc("https://x.dev/cb"), "https%3A%2F%2Fx.dev%2Fcb");
        assert_eq!(enc("aZ0-_.~"), "aZ0-_.~");
    }
}
