//! Cloudflare Worker entry points.
//!
//! Static assets (../dist) are served by the Workers assets layer; only
//! /api/* reaches this code (wrangler.toml: run_worker_first = ["/api/*"]).

mod auth;
mod db;
mod email;
mod email_text;
mod error;
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
        .get_async("/api/auth/sessions", auth::sessions)
        .post_async("/api/auth/revoke_session", auth::revoke_session)
        .post_async(
            "/api/auth/revoke_other_sessions",
            auth::revoke_other_sessions,
        )
        .post_async("/api/auth/change_password", auth::change_password)
        .post_async("/api/rpc/:name", rpc::handle)
        .or_else_any_method_async("/*catchall", |_, _| async {
            Response::error("Not found", 404)
        })
        .run(req, env)
        .await
}

/// Daily crons (UTC). 07:00 (01:00 CDMX) refreshes market data and posts the
/// money the night owes (yield, MSI, snapshots); 14:00 (08:00 CDMX) evaluates
/// notification rules over those fresh numbers, at an hour a human reads them.
/// Failures are logged (visible via `wrangler tail`) and otherwise silent.
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
    if event.cron() == "0 14 * * *" {
        if let Err(e) = handlers::notifications::generate_all(&db).await {
            console_error!("notification generation failed: {e}");
        }
        // Email digest right after: alerts marked 'pending' go out in one
        // message per user. Without SMTP config the channel just sleeps.
        match email::EmailConfig::from_env(&env) {
            Some(cfg) => {
                if let Err(e) = handlers::notifications::send_pending_emails(&db, &cfg).await {
                    console_error!("notification emails failed: {e}");
                }
            }
            None => worker::console_log!("smtp not configured; skipping notification emails"),
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
