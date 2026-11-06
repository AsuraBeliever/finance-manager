//! Gmail API client for bank notices (FIN-5): token exchange/refresh, the
//! push `watch`, `history.list` and reading one message. Plus AES-GCM for the
//! tokens at rest, via the runtime's native SubtleCrypto (same reason as
//! PBKDF2: a pure-Rust cipher in WASM would eat the CPU budget and the size).
//!
//! Push, not polling: `users.watch` makes Gmail publish to a Google Pub/Sub
//! topic on every mailbox change; the topic's push subscription POSTs to
//! /api/gmail/push (lib.rs), which reads what came after the stored
//! `historyId`. A watch lasts 7 days; the daily cron renews it.
//!
//! Config (wrangler vars / secrets):
//! - GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET — the same OAuth client as sign-in
//! - GMAIL_TOKEN_KEY (secret) — base64 of 32 random bytes, the AES-256 key
//! - GMAIL_PUBSUB_TOPIC — `projects/<project>/topics/<topic>`
//! - GMAIL_PUSH_TOKEN (secret) — shared token in the push endpoint URL
//! - GMAIL_API_BASE / GOOGLE_TOKEN_URL — overrides for local testing only

use base64::engine::general_purpose::{STANDARD, URL_SAFE, URL_SAFE_NO_PAD};
use base64::Engine;
use finanzas_core::error::{AppError, AppResult};
use js_sys::{Array, Object, Promise, Reflect, Uint8Array};
use serde::Deserialize;
use serde_json::Value;
use wasm_bindgen::prelude::wasm_bindgen;
use wasm_bindgen::JsValue;
use wasm_bindgen_futures::JsFuture;
use worker::{Env, Fetch, Headers, Method, Request, RequestInit};

use crate::db::random_bytes;
use crate::error::db_err;

pub const SCOPE: &str = "https://www.googleapis.com/auth/gmail.readonly";
const DEFAULT_API_BASE: &str = "https://gmail.googleapis.com";
const DEFAULT_TOKEN_URL: &str = "https://oauth2.googleapis.com/token";
const REVOKE_URL: &str = "https://oauth2.googleapis.com/revoke";

pub struct GmailConfig {
    pub client_id: String,
    client_secret: String,
    token_key: Vec<u8>,
    pub topic: Option<String>,
    pub push_token: Option<String>,
    api_base: String,
    token_url: String,
}

fn secret_or_var(env: &Env, name: &str) -> Option<String> {
    env.secret(name)
        .map(|s| s.to_string())
        .or_else(|_| env.var(name).map(|v| v.to_string()))
        .ok()
        .filter(|s| !s.trim().is_empty())
}

impl GmailConfig {
    /// None when this deployment has no Gmail set up (the feature is then
    /// offered as "paste a notice" only).
    pub fn from_env(env: &Env) -> Option<Self> {
        let token_key = STANDARD
            .decode(secret_or_var(env, "GMAIL_TOKEN_KEY")?.trim())
            .ok()
            .filter(|k| k.len() == 32)?;
        Some(Self {
            client_id: secret_or_var(env, "GOOGLE_CLIENT_ID")?,
            client_secret: secret_or_var(env, "GOOGLE_CLIENT_SECRET")?,
            token_key,
            topic: secret_or_var(env, "GMAIL_PUBSUB_TOPIC"),
            push_token: secret_or_var(env, "GMAIL_PUSH_TOKEN"),
            api_base: secret_or_var(env, "GMAIL_API_BASE")
                .unwrap_or_else(|| DEFAULT_API_BASE.into()),
            token_url: secret_or_var(env, "GOOGLE_TOKEN_URL")
                .unwrap_or_else(|| DEFAULT_TOKEN_URL.into()),
        })
    }

    pub fn require(env: &Env) -> AppResult<Self> {
        Self::from_env(env).ok_or_else(|| {
            AppError::InvalidInput("la conexión con Gmail no está disponible todavía".into())
        })
    }
}

// ------------------------------------------------------------- AES-GCM ----

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = ["crypto", "subtle"], js_name = importKey)]
    fn js_import_key(
        format: &str,
        key_data: Uint8Array,
        algorithm: &JsValue,
        extractable: bool,
        usages: &JsValue,
    ) -> Promise;

    #[wasm_bindgen(js_namespace = ["crypto", "subtle"], js_name = encrypt)]
    fn js_encrypt(algorithm: &JsValue, key: &JsValue, data: Uint8Array) -> Promise;

    #[wasm_bindgen(js_namespace = ["crypto", "subtle"], js_name = decrypt)]
    fn js_decrypt(algorithm: &JsValue, key: &JsValue, data: Uint8Array) -> Promise;
}

fn js_err(e: JsValue) -> AppError {
    AppError::Internal(format!("error de crypto: {e:?}"))
}

async fn aes_key(raw: &[u8]) -> AppResult<JsValue> {
    let usages: JsValue = Array::of2(&"encrypt".into(), &"decrypt".into()).into();
    JsFuture::from(js_import_key(
        "raw",
        Uint8Array::from(raw),
        &JsValue::from_str("AES-GCM"),
        false,
        &usages,
    ))
    .await
    .map_err(js_err)
}

fn gcm_params(iv: &[u8]) -> AppResult<JsValue> {
    let algo = Object::new();
    Reflect::set(&algo, &"name".into(), &"AES-GCM".into()).map_err(js_err)?;
    Reflect::set(&algo, &"iv".into(), &Uint8Array::from(iv).into()).map_err(js_err)?;
    Ok(algo.into())
}

impl GmailConfig {
    /// base64(iv || ciphertext+tag), fresh 12-byte IV each time.
    pub async fn seal(&self, plain: &str) -> AppResult<String> {
        let key = aes_key(&self.token_key).await?;
        let iv = random_bytes(12);
        let ct = JsFuture::from(js_encrypt(
            &gcm_params(&iv)?,
            &key,
            Uint8Array::from(plain.as_bytes()),
        ))
        .await
        .map_err(js_err)?;
        let mut out = iv;
        out.extend(Uint8Array::new(&ct).to_vec());
        Ok(STANDARD.encode(out))
    }

    pub async fn open(&self, sealed: &str) -> AppResult<String> {
        let bytes = STANDARD
            .decode(sealed)
            .map_err(|_| AppError::Internal("token cifrado corrupto".into()))?;
        if bytes.len() < 13 {
            return Err(AppError::Internal("token cifrado corrupto".into()));
        }
        let key = aes_key(&self.token_key).await?;
        let pt = JsFuture::from(js_decrypt(
            &gcm_params(&bytes[..12])?,
            &key,
            Uint8Array::from(&bytes[12..]),
        ))
        .await
        .map_err(js_err)?;
        String::from_utf8(Uint8Array::new(&pt).to_vec())
            .map_err(|_| AppError::Internal("token descifrado inválido".into()))
    }
}

// ---------------------------------------------------------------- HTTP ----

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

/// Gmail answered with an HTTP error. 401 = the grant was revoked; 404 on
/// history = the cursor is too old.
#[derive(Debug)]
pub struct HttpStatus(pub u16);

async fn send(
    method: Method,
    url: &str,
    bearer: Option<&str>,
    form: Option<String>,
    json: Option<Value>,
) -> AppResult<Result<Value, HttpStatus>> {
    let headers = Headers::new();
    if let Some(t) = bearer {
        headers
            .set("Authorization", &format!("Bearer {t}"))
            .map_err(db_err)?;
    }
    let body = if let Some(f) = form {
        headers
            .set("Content-Type", "application/x-www-form-urlencoded")
            .map_err(db_err)?;
        Some(f)
    } else if let Some(j) = json {
        headers
            .set("Content-Type", "application/json")
            .map_err(db_err)?;
        Some(j.to_string())
    } else {
        None
    };
    let mut init = RequestInit::new();
    init.with_method(method).with_headers(headers);
    if let Some(b) = body {
        init.with_body(Some(b.into()));
    }
    let req = Request::new_with_init(url, &init).map_err(db_err)?;
    let mut resp = Fetch::Request(req)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("Google no respondió: {e}")))?;
    let status = resp.status_code();
    if !(200..300).contains(&status) {
        let text = resp.text().await.unwrap_or_default();
        worker::console_error!(
            "google {url} → {status}: {}",
            text.chars().take(300).collect::<String>()
        );
        return Ok(Err(HttpStatus(status)));
    }
    let text = resp.text().await.map_err(db_err)?;
    if text.trim().is_empty() {
        return Ok(Ok(Value::Null));
    }
    serde_json::from_str(&text)
        .map(Ok)
        .map_err(|e| AppError::Internal(format!("respuesta de Google inválida: {e}")))
}

fn google_failed(what: &str, s: HttpStatus) -> AppError {
    if s.0 == 400 || s.0 == 401 {
        AppError::InvalidInput(format!("Google rechazó {what}: vuelve a conectar Gmail"))
    } else {
        AppError::Internal(format!("Google falló en {what} ({})", s.0))
    }
}

#[derive(Deserialize)]
pub struct Tokens {
    pub access_token: String,
    pub expires_in: Option<i64>,
    pub refresh_token: Option<String>,
    pub scope: Option<String>,
}

impl GmailConfig {
    /// Authorization code → tokens. `redirect_uri` must be the one the code
    /// was issued for; empty for the Android app's server auth code.
    pub async fn exchange_code(&self, code: &str, redirect_uri: &str) -> AppResult<Tokens> {
        let form = format!(
            "code={}&client_id={}&client_secret={}&redirect_uri={}&grant_type=authorization_code",
            enc(code),
            enc(&self.client_id),
            enc(&self.client_secret),
            enc(redirect_uri),
        );
        let v = send(Method::Post, &self.token_url, None, Some(form), None)
            .await?
            .map_err(|s| google_failed("la autorización", s))?;
        serde_json::from_value(v).map_err(|e| AppError::Internal(format!("tokens inválidos: {e}")))
    }

    /// Fresh access token from the refresh token. Err(HttpStatus) on a revoked grant.
    pub async fn refresh(&self, refresh_token: &str) -> AppResult<Result<Tokens, HttpStatus>> {
        let form = format!(
            "refresh_token={}&client_id={}&client_secret={}&grant_type=refresh_token",
            enc(refresh_token),
            enc(&self.client_id),
            enc(&self.client_secret),
        );
        Ok(
            match send(Method::Post, &self.token_url, None, Some(form), None).await? {
                Ok(v) => Ok(serde_json::from_value(v)
                    .map_err(|e| AppError::Internal(format!("tokens inválidos: {e}")))?),
                Err(s) => Err(s),
            },
        )
    }

    /// Best effort: the grant disappears from the user's Google account.
    pub async fn revoke(&self, token: &str) {
        let form = format!("token={}", enc(token));
        if let Err(e) = send(Method::Post, REVOKE_URL, None, Some(form), None).await {
            worker::console_error!("gmail revoke failed: {e}");
        }
    }

    fn api(&self, path: &str) -> String {
        format!("{}/gmail/v1/users/me/{path}", self.api_base)
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Profile {
    pub email_address: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Watch {
    pub history_id: String,
    /// Unix milliseconds, as a string.
    pub expiration: String,
}

/// A page of `history.list`: each record's id with the message ids it added.
#[derive(Debug, PartialEq)]
pub struct HistoryPage {
    pub records: Vec<(String, Vec<String>)>,
    /// The mailbox's current history id — the cursor once everything is read.
    pub history_id: Option<String>,
    /// More records remain after this page.
    pub more: bool,
}

fn parse_history(v: &Value) -> HistoryPage {
    let records = v
        .get("history")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|h| {
            let id = match h.get("id")? {
                Value::String(s) => s.clone(),
                Value::Number(n) => n.to_string(),
                _ => return None,
            };
            let ids = h
                .get("messagesAdded")
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
                .filter_map(|m| m.pointer("/message/id").and_then(Value::as_str))
                .map(str::to_string)
                .collect();
            Some((id, ids))
        })
        .collect();
    HistoryPage {
        records,
        history_id: v
            .get("historyId")
            .and_then(Value::as_str)
            .map(str::to_string),
        more: v.get("nextPageToken").and_then(Value::as_str).is_some(),
    }
}

/// A message's sender and Gmail labels.
pub struct MailMeta {
    pub from: String,
    pub labels: Vec<String>,
}

impl MailMeta {
    /// Gmail already filed it as marketing or spam. Banks send promotions
    /// from the same domains as their notices (BBVA: email.bbva.mx), often
    /// with subjects that read like a movement ("$600 de regalo…").
    pub fn is_marketing(&self) -> bool {
        self.labels
            .iter()
            .any(|l| l == "CATEGORY_PROMOTIONS" || l == "SPAM")
    }
}

/// One message, reduced to what the notice reader needs.
pub struct Mail {
    pub from: String,
    pub subject: String,
    pub body: String,
    /// Unix milliseconds Gmail received it.
    pub internal_date_ms: i64,
}

impl GmailConfig {
    pub async fn profile(&self, access: &str) -> AppResult<Profile> {
        let v = send(Method::Get, &self.api("profile"), Some(access), None, None)
            .await?
            .map_err(|s| google_failed("el perfil", s))?;
        serde_json::from_value(v).map_err(|e| AppError::Internal(format!("perfil inválido: {e}")))
    }

    pub async fn watch(&self, access: &str) -> AppResult<Result<Watch, HttpStatus>> {
        let Some(topic) = &self.topic else {
            return Err(AppError::Internal(
                "GMAIL_PUBSUB_TOPIC no configurado".into(),
            ));
        };
        let body = serde_json::json!({
            "topicName": topic,
            "labelIds": ["INBOX"],
            "labelFilterBehavior": "include",
        });
        Ok(
            match send(
                Method::Post,
                &self.api("watch"),
                Some(access),
                None,
                Some(body),
            )
            .await?
            {
                Ok(v) => Ok(serde_json::from_value(v)
                    .map_err(|e| AppError::Internal(format!("watch inválido: {e}")))?),
                Err(s) => Err(s),
            },
        )
    }

    pub async fn stop(&self, access: &str) {
        let _ = send(
            Method::Post,
            &self.api("stop"),
            Some(access),
            None,
            Some(serde_json::json!({})),
        )
        .await;
    }

    /// One page of what was added to INBOX after `start`: at most
    /// `max_records` history records, oldest first. Err(404) = the cursor is
    /// too old to replay.
    ///
    /// One page only, on purpose: a free-plan invocation gets 50 subrequests,
    /// and each message costs one or two more. What doesn't fit is read by the
    /// next push, from the last record this one finished.
    pub async fn history(
        &self,
        access: &str,
        start: &str,
        max_records: u32,
    ) -> AppResult<Result<HistoryPage, HttpStatus>> {
        let url = self.api(&format!(
            "history?startHistoryId={}&historyTypes=messageAdded&labelId=INBOX&maxResults={max_records}",
            enc(start)
        ));
        let v = match send(Method::Get, &url, Some(access), None, None).await? {
            Ok(v) => v,
            Err(s) => return Ok(Err(s)),
        };
        Ok(Ok(parse_history(&v)))
    }

    /// From and Gmail's own labels — cheap check before reading a whole
    /// message. None = the message is gone.
    pub async fn headers(&self, access: &str, id: &str) -> AppResult<Option<MailMeta>> {
        let url = self.api(&format!(
            "messages/{}?format=metadata&metadataHeaders=From&metadataHeaders=Subject",
            enc(id)
        ));
        let v = match send(Method::Get, &url, Some(access), None, None).await? {
            Ok(v) => v,
            Err(HttpStatus(404)) => return Ok(None), // deleted since
            Err(s) => return Err(google_failed("el correo", s)),
        };
        let hs = v
            .pointer("/payload/headers")
            .cloned()
            .unwrap_or(Value::Null);
        let labels = v
            .get("labelIds")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(Value::as_str)
            .map(str::to_string)
            .collect();
        Ok(Some(MailMeta {
            from: header(&hs, "From"),
            labels,
        }))
    }

    pub async fn message(&self, access: &str, id: &str) -> AppResult<Option<Mail>> {
        let url = self.api(&format!("messages/{}?format=full", enc(id)));
        let v = match send(Method::Get, &url, Some(access), None, None).await? {
            Ok(v) => v,
            Err(HttpStatus(404)) => return Ok(None),
            Err(s) => return Err(google_failed("el correo", s)),
        };
        let payload = v.get("payload").cloned().unwrap_or(Value::Null);
        let hs = payload.get("headers").cloned().unwrap_or(Value::Null);
        let internal_date_ms = v
            .get("internalDate")
            .and_then(Value::as_str)
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);
        Ok(Some(Mail {
            from: header(&hs, "From"),
            subject: header(&hs, "Subject"),
            body: body_text(&payload),
            internal_date_ms,
        }))
    }
}

fn header(headers: &Value, name: &str) -> String {
    headers
        .as_array()
        .into_iter()
        .flatten()
        .find(|h| {
            h.get("name")
                .and_then(Value::as_str)
                .is_some_and(|n| n.eq_ignore_ascii_case(name))
        })
        .and_then(|h| h.get("value").and_then(Value::as_str))
        .unwrap_or_default()
        .to_string()
}

fn decode_part(part: &Value) -> Option<String> {
    let data = part.pointer("/body/data")?.as_str()?;
    let bytes = URL_SAFE_NO_PAD
        .decode(data.trim_end_matches('='))
        .or_else(|_| URL_SAFE.decode(data))
        .ok()?;
    Some(String::from_utf8_lossy(&bytes).into_owned())
}

/// The message text: the first text/plain part, else the first text/html
/// part reduced to text.
fn body_text(payload: &Value) -> String {
    fn walk<'a>(p: &'a Value, mime: &str, out: &mut Option<&'a Value>) {
        if out.is_some() {
            return;
        }
        if p.get("mimeType").and_then(Value::as_str) == Some(mime) {
            *out = Some(p);
            return;
        }
        for c in p
            .get("parts")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
        {
            walk(c, mime, out);
        }
    }
    let mut plain = None;
    walk(payload, "text/plain", &mut plain);
    if let Some(text) = plain.and_then(decode_part).filter(|t| !t.trim().is_empty()) {
        return text;
    }
    let mut html = None;
    walk(payload, "text/html", &mut html);
    html.and_then(decode_part)
        .map(|h| finanzas_core::bank_notice::html_to_text(&h))
        .unwrap_or_default()
}

/// Pub/Sub push body → (emailAddress, historyId).
pub fn parse_push(body: &Value) -> Option<(String, String)> {
    let data = body.pointer("/message/data")?.as_str()?;
    let bytes = STANDARD
        .decode(data)
        .or_else(|_| URL_SAFE.decode(data))
        .ok()?;
    let v: Value = serde_json::from_slice(&bytes).ok()?;
    let email = v.get("emailAddress")?.as_str()?.to_ascii_lowercase();
    let history = match v.get("historyId")? {
        Value::String(s) => s.clone(),
        Value::Number(n) => n.to_string(),
        _ => return None,
    };
    Some((email, history))
}

/// Constant-time string equality for the push token.
pub fn same_token(a: &str, b: &str) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.bytes().zip(b.bytes()).fold(0u8, |d, (x, y)| d | (x ^ y)) == 0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn push_body_decodes() {
        // {"emailAddress":"Ana@Gmail.com","historyId":9876543210}
        let data = STANDARD.encode(br#"{"emailAddress":"Ana@Gmail.com","historyId":9876543210}"#);
        let body =
            serde_json::json!({"message": {"data": data, "messageId": "1"}, "subscription": "s"});
        assert_eq!(
            parse_push(&body),
            Some(("ana@gmail.com".into(), "9876543210".into()))
        );
        assert_eq!(parse_push(&serde_json::json!({})), None);
    }

    #[test]
    fn prefers_plain_text_part() {
        let plain = URL_SAFE_NO_PAD.encode("Compra por $10.00");
        let html = URL_SAFE_NO_PAD.encode("<p>otro</p>");
        let payload = serde_json::json!({
            "mimeType": "multipart/alternative",
            "parts": [
                {"mimeType": "text/html", "body": {"data": html}},
                {"mimeType": "text/plain", "body": {"data": plain}},
            ]
        });
        assert_eq!(body_text(&payload), "Compra por $10.00");
    }

    #[test]
    fn falls_back_to_html() {
        let html = URL_SAFE_NO_PAD.encode("<div>Compra</div><div>$10.00</div>");
        let payload = serde_json::json!({"mimeType": "text/html", "body": {"data": html}});
        assert_eq!(body_text(&payload), "Compra\n$10.00");
    }

    #[test]
    fn history_page() {
        let v = serde_json::json!({
            "history": [
                {"id": "101", "messagesAdded": [{"message": {"id": "a"}}]},
                {"id": "102", "messages": [{"id": "x"}]},
                {"id": "103", "messagesAdded": [{"message": {"id": "b"}}, {"message": {"id": "c"}}]},
            ],
            "historyId": "110",
            "nextPageToken": "t",
        });
        assert_eq!(
            parse_history(&v),
            HistoryPage {
                records: vec![
                    ("101".into(), vec!["a".into()]),
                    ("102".into(), vec![]),
                    ("103".into(), vec!["b".into(), "c".into()]),
                ],
                history_id: Some("110".into()),
                more: true,
            }
        );
        let empty = parse_history(&serde_json::json!({"historyId": "7"}));
        assert!(empty.records.is_empty() && !empty.more);
    }

    #[test]
    fn marketing_labels() {
        let meta = |labels: &[&str]| MailMeta {
            from: "clientes@email.bbva.mx".into(),
            labels: labels.iter().map(|l| l.to_string()).collect(),
        };
        assert!(meta(&["CATEGORY_PROMOTIONS", "UNREAD", "INBOX"]).is_marketing());
        assert!(meta(&["SPAM"]).is_marketing());
        assert!(!meta(&["CATEGORY_UPDATES", "INBOX"]).is_marketing());
    }

    #[test]
    fn token_compare() {
        assert!(same_token("abc", "abc"));
        assert!(!same_token("abc", "abd"));
        assert!(!same_token("abc", "ab"));
    }

    #[test]
    fn header_lookup_is_case_insensitive() {
        let hs = serde_json::json!([{"name": "from", "value": "a@b.mx"}]);
        assert_eq!(header(&hs, "From"), "a@b.mx");
        assert_eq!(header(&hs, "Subject"), "");
    }
}
