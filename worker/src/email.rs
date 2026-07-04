//! Notification emails over plain SMTP, spoken directly through a TCP socket
//! (`worker::Socket`) — Workers can't use an SMTP crate, but the protocol is
//! a short line dialogue. One digest email per user per cron run.
//!
//! Configuration lives in secrets/vars (never in code — the repo is public):
//!   SMTP_HOST, SMTP_PORT, SMTP_SECURE ('on' = implicit TLS e.g. 465,
//!   'starttls' = upgrade e.g. 587, 'off' = local test sink), SMTP_USERNAME,
//!   SMTP_PASSWORD (empty user skips AUTH), SMTP_FROM, APP_URL.
//! Missing host/from simply disables the channel (logged once per run).
//!
//! The email body is rendered server-side in Spanish (`render_es`) from the
//! same kind + params the bell stores; the client-side i18n templates stay
//! the source of truth for the UI, this mirrors them for mail only.

use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use finanzas_core::error::{AppError, AppResult};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use worker::{ConnectionBuilder, Env, SecureTransport, Socket};

pub struct EmailConfig {
    pub host: String,
    pub port: u16,
    pub secure: SecureTransport,
    pub username: String,
    pub password: String,
    pub from: String,
    pub app_url: String,
}

impl EmailConfig {
    /// Read the SMTP settings; None when the channel isn't configured.
    /// Secrets win over plain vars so production uses `wrangler secret put`
    /// while local dev can keep everything in .dev.vars.
    pub fn from_env(env: &Env) -> Option<EmailConfig> {
        let get = |k: &str| {
            env.secret(k)
                .map(|s| s.to_string())
                .or_else(|_| env.var(k).map(|v| v.to_string()))
                .unwrap_or_default()
        };
        let host = get("SMTP_HOST");
        let from = get("SMTP_FROM");
        if host.is_empty() || from.is_empty() {
            return None;
        }
        let secure = match get("SMTP_SECURE").as_str() {
            "off" => SecureTransport::Off,
            "starttls" => SecureTransport::StartTls,
            _ => SecureTransport::On,
        };
        Some(EmailConfig {
            port: get("SMTP_PORT").parse().unwrap_or(465),
            host,
            secure,
            username: get("SMTP_USERNAME"),
            password: get("SMTP_PASSWORD"),
            from,
            app_url: get("APP_URL"),
        })
    }
}

/// One SMTP conversation: connect, greet, auth, send one message, quit.
pub async fn send(cfg: &EmailConfig, to: &str, subject: &str, html_body: &str) -> AppResult<()> {
    let is_starttls = matches!(cfg.secure, SecureTransport::StartTls);
    let mut socket = ConnectionBuilder::new()
        .secure_transport(match cfg.secure {
            SecureTransport::On => SecureTransport::On,
            SecureTransport::StartTls => SecureTransport::StartTls,
            _ => SecureTransport::Off,
        })
        .connect(&cfg.host, cfg.port)
        .map_err(|e| AppError::Internal(format!("smtp connect: {e}")))?;

    expect(&mut socket, "220").await?; // server greeting
    command(&mut socket, "EHLO finanzas", "250").await?;
    if is_starttls {
        command(&mut socket, "STARTTLS", "220").await?;
        socket = socket.start_tls();
        command(&mut socket, "EHLO finanzas", "250").await?;
    }
    if !cfg.username.is_empty() {
        let creds = B64.encode(format!("\0{}\0{}", cfg.username, cfg.password));
        command(&mut socket, &format!("AUTH PLAIN {creds}"), "235").await?;
    }
    command(&mut socket, &format!("MAIL FROM:<{}>", cfg.from), "250").await?;
    command(&mut socket, &format!("RCPT TO:<{to}>"), "250").await?;
    command(&mut socket, "DATA", "354").await?;

    let message = build_message(&cfg.from, to, subject, html_body);
    socket
        .write_all(message.as_bytes())
        .await
        .map_err(|e| AppError::Internal(format!("smtp write: {e}")))?;
    command(&mut socket, "\r\n.", "250").await?;
    // Best-effort goodbye; the message is already accepted.
    let _ = socket.write_all(b"QUIT\r\n").await;
    let _ = socket.close().await;
    Ok(())
}

/// RFC 5322 message with an HTML body. Subject goes RFC 2047 base64-encoded
/// so accents/emoji survive; body lines are dot-stuffed per SMTP.
fn build_message(from: &str, to: &str, subject: &str, html_body: &str) -> String {
    let subject_b64 = B64.encode(subject.as_bytes());
    let body = html_body.replace("\n.", "\n..");
    format!(
        "From: Finanzas <{from}>\r\n\
         To: <{to}>\r\n\
         Subject: =?UTF-8?B?{subject_b64}?=\r\n\
         MIME-Version: 1.0\r\n\
         Content-Type: text/html; charset=utf-8\r\n\
         \r\n\
         {body}"
    )
}

/// Send one command line and require the reply to start with `code`.
async fn command(socket: &mut Socket, line: &str, code: &str) -> AppResult<()> {
    socket
        .write_all(format!("{line}\r\n").as_bytes())
        .await
        .map_err(|e| AppError::Internal(format!("smtp write: {e}")))?;
    expect(socket, code).await
}

/// Read one SMTP reply (multi-line replies end at "NNN " without a dash)
/// and require it to start with `code`.
async fn expect(socket: &mut Socket, code: &str) -> AppResult<()> {
    let mut buf = Vec::with_capacity(512);
    let mut chunk = [0u8; 256];
    loop {
        let n = socket
            .read(&mut chunk)
            .await
            .map_err(|e| AppError::Internal(format!("smtp read: {e}")))?;
        if n == 0 {
            break;
        }
        buf.extend_from_slice(&chunk[..n]);
        if reply_complete(&buf) {
            break;
        }
        if buf.len() > 16 * 1024 {
            break; // a well-behaved server never gets here
        }
    }
    let reply = String::from_utf8_lossy(&buf);
    let last = reply.lines().last().unwrap_or_default();
    if last.starts_with(code) {
        Ok(())
    } else {
        Err(AppError::Internal(format!(
            "smtp: se esperaba {code}, llegó: {}",
            last.chars().take(120).collect::<String>()
        )))
    }
}

/// True once the buffer holds a complete reply: its final line is
/// "NNN<space>..." (a dash after the code means more lines follow).
fn reply_complete(buf: &[u8]) -> bool {
    if !buf.ends_with(b"\n") {
        return false;
    }
    let text = String::from_utf8_lossy(buf);
    let last = text.trim_end().lines().last().unwrap_or_default();
    last.len() >= 4 && last.as_bytes()[3] == b' '
}
