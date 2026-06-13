//! Cloudflare Worker entry points.
//!
//! Static assets (../dist) are served by the Workers assets layer; only
//! /api/* reaches this code (wrangler.toml: run_worker_first = ["/api/*"]).

mod auth;
mod db;
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

/// Daily market-data refresh (cron, UTC) — replaces the desktop's
/// on-startup fetch. Failures are logged (visible via `wrangler tail`) and
/// otherwise silent, same semantics as the desktop app.
#[event(scheduled)]
pub async fn scheduled(_event: ScheduledEvent, env: Env, _ctx: ScheduleContext) {
    console_error_panic_hook::set_once();
    let db = match env.d1("DB") {
        Ok(d) => d,
        Err(e) => {
            console_error!("scheduled: no DB binding: {e}");
            return;
        }
    };
    if let Err(e) = market::fetch_and_store_rates(&db, false).await {
        console_error!("exchange rate auto-update failed: {e}");
    }
    if let Err(e) = market::refresh_market_data(&db).await {
        console_error!("market data auto-update failed: {e}");
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
}
