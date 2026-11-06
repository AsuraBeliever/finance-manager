//! Cloudflare Worker entry points.
//!
//! Static assets (../dist) are served by the Workers assets layer; only
//! /api/* reaches this code (wrangler.toml: run_worker_first = ["/api/*"]).

mod auth;
mod db;
mod error;
mod gmail;
mod handlers;
mod market;
mod rpc;

use worker::{
    console_error, event, Context, Env, Request, Response, Result, Router, ScheduleContext,
    ScheduledEvent,
};

#[event(fetch)]
pub async fn fetch(req: Request, env: Env, _ctx: Context) -> Result<Response> {
    console_error_panic_hook::set_once();
    Router::new()
        .post_async("/api/auth/register", auth::register)
        .post_async("/api/auth/login", auth::login)
        .post_async("/api/auth/logout", auth::logout)
        .get_async("/api/auth/me", auth::me)
        .get_async("/api/auth/google/start", auth::google::start)
        .get_async("/api/auth/google/callback", auth::google::callback)
        // Native clients cannot use the redirect flow: the cookie would land in
        // the browser, not in the app's HTTP client.
        .post_async("/api/auth/google/token", auth::google::token_sign_in)
        .get_async("/api/auth/sessions", auth::sessions)
        .post_async("/api/auth/revoke_session", auth::revoke_session)
        .post_async(
            "/api/auth/revoke_other_sessions",
            auth::revoke_other_sessions,
        )
        .post_async("/api/auth/change_password", auth::change_password)
        .post_async("/api/rpc/:name", rpc::handle)
        // Bank notices from Gmail: the browser connect flow (Android posts a
        // server auth code over RPC instead) and the Pub/Sub push endpoint.
        .get_async("/api/gmail/connect", auth::google::gmail_start)
        .post_async("/api/gmail/push", gmail_push)
        .or_else_any_method_async("/*catchall", |_, _| async {
            Response::error("Not found", 404)
        })
        .run(req, env)
        .await
}

/// Pub/Sub push for a Gmail mailbox change. Authenticated by the shared
/// GMAIL_PUSH_TOKEN in the subscription's URL (`?token=`). Always 204 once
/// authenticated: a failure leaves the history cursor where it was, so the
/// next push picks the messages up — answering 5xx would only make Pub/Sub
/// redeliver the same notification.
async fn gmail_push(mut req: Request, ctx: worker::RouteContext<()>) -> Result<Response> {
    let Some(cfg) = gmail::GmailConfig::from_env(&ctx.env) else {
        return Response::error("Not found", 404);
    };
    let token = req.url().ok().and_then(|u| {
        u.query_pairs()
            .find(|(k, _)| k == "token")
            .map(|(_, v)| v.into_owned())
    });
    let authorized = match (&cfg.push_token, token) {
        (Some(expected), Some(got)) => gmail::same_token(expected, &got),
        _ => false,
    };
    if !authorized {
        return Response::error("Unauthorized", 401);
    }
    let body: serde_json::Value = req.json().await.unwrap_or_default();
    let Some((email, history)) = gmail::parse_push(&body) else {
        return Ok(Response::empty()?.with_status(204));
    };
    match ctx.env.d1("DB") {
        Ok(db) => {
            if let Err(e) = handlers::bank_notices::process_push(&db, &cfg, &email, &history).await
            {
                console_error!("gmail push failed: {e}");
            }
        }
        Err(e) => console_error!("gmail push: no DB binding: {e}"),
    }
    Ok(Response::empty()?.with_status(204))
}

/// The frequent cron in wrangler.toml: books automatic subscription charges, so
/// a charge lands within minutes of its chosen time, and renews the Gmail
/// watches about to lapse.
const SUBSCRIPTION_CRON: &str = "*/5 * * * *";

/// Daily market-data refresh (cron, UTC) — replaces the desktop's
/// on-startup fetch — plus the frequent subscription-charge pass. Failures are
/// logged (visible via `wrangler tail`) and otherwise silent, same semantics as
/// the desktop app.
#[event(scheduled)]
pub async fn scheduled(event: ScheduledEvent, env: Env, _ctx: ScheduleContext) {
    console_error_panic_hook::set_once();
    let db = match env.d1("DB") {
        Ok(d) => d,
        Err(e) => {
            console_error!("scheduled: no DB binding: {e}");
            return;
        }
    };
    if event.cron() == SUBSCRIPTION_CRON {
        if let Err(e) = handlers::subscriptions::post_due_subscriptions(&db).await {
            console_error!("subscription auto-charge failed: {e}");
        }
        // Gmail watches last 7 days. Renewed a few at a time here rather than
        // in the daily run, which already spends its subrequests on market data.
        if let Some(cfg) = gmail::GmailConfig::from_env(&env) {
            if let Err(e) = handlers::bank_notices::renew_gmail_watches(&db, &cfg).await {
                console_error!("gmail watch renewal failed: {e}");
            }
        }
        return;
    }
    if let Err(e) = market::fetch_and_store_rates(&db, false).await {
        console_error!("exchange rate auto-update failed: {e}");
    }
    if let Err(e) = market::refresh_market_data(&db).await {
        console_error!("market data auto-update failed: {e}");
    }
    if let Err(e) = handlers::wallet_yield::accrue_yield(&db).await {
        console_error!("wallet yield accrual failed: {e}");
    }
    if let Err(e) = handlers::credit::post_msi_installments(&db).await {
        console_error!("msi installment posting failed: {e}");
    }
    if let Err(e) = handlers::goals::snapshot_all_goals(&db).await {
        console_error!("goal snapshot failed: {e}");
    }
    if let Err(e) = db::exec(
        &db,
        "DELETE FROM sessions WHERE expires_at < datetime('now')",
        vec![],
    )
    .await
    {
        console_error!("session cleanup failed: {e}");
    }
    if let Err(e) = db::exec(
        &db,
        "DELETE FROM auth_attempts WHERE window_start < datetime('now', '-1 day')",
        vec![],
    )
    .await
    {
        console_error!("auth_attempts cleanup failed: {e}");
    }
}
