//! RPC dispatcher: POST /api/rpc/<commandName> with a JSON body whose shape
//! matches the old Tauri invoke payload (camelCase args), so src/lib/api.ts
//! only swaps the transport. Every command requires a session.

use finanzas_core::error::{AppError, AppResult};
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde_json::Value;
use worker::{D1Database, Request, Response, RouteContext};

use crate::error::{db_err, error_response};
use crate::handlers::{dashboard, investments, settings, transactions, wallets};
use crate::market;

fn args<A: DeserializeOwned>(body: Value) -> AppResult<A> {
    serde_json::from_value(body)
        .map_err(|e| AppError::InvalidInput(format!("argumentos inválidos: {e}")))
}

fn out<T: Serialize>(v: T) -> AppResult<Value> {
    serde_json::to_value(v).map_err(|e| AppError::Internal(e.to_string()))
}

pub async fn handle(mut req: Request, ctx: RouteContext<()>) -> worker::Result<Response> {
    let name = ctx.param("name").cloned().unwrap_or_default();
    let db = match ctx.env.d1("DB") {
        Ok(d) => d,
        Err(e) => return error_response(&db_err(e)),
    };
    if let Err(e) = crate::auth::check_origin(&req) {
        return error_response(&e);
    }
    let uid = match crate::auth::require_user(&req, &db).await {
        Ok(u) => u,
        Err(e) => return error_response(&e),
    };
    let body: Value = req.json().await.unwrap_or(Value::Null);
    let body = if body.is_null() {
        Value::Object(Default::default())
    } else {
        body
    };
    match dispatch(&name, body, &db, uid).await {
        Ok(v) => Response::from_json(&v),
        Err(e) => error_response(&e),
    }
}

async fn dispatch(name: &str, body: Value, db: &D1Database, uid: i64) -> AppResult<Value> {
    match name {
        // ---- settings ----
        "list_currencies" => out(settings::list_currencies(db).await?),
        "list_wallet_categories" => out(settings::list_wallet_categories(db).await?),
        "get_exchange_rates" => out(settings::get_exchange_rates(db).await?),
        "set_exchange_rate" => out(settings::set_exchange_rate(db, args(body)?).await?),
        "fetch_exchange_rates" => out(market::fetch_and_store_rates(db, true).await?),
        "add_currency" => out(settings::add_currency(db, args(body)?).await?),
        "fetch_banxico_rate" => {
            #[derive(serde::Deserialize)]
            struct KindArgs {
                kind: String,
            }
            let a: KindArgs = args(body)?;
            out(market::fetch_rate_tokenless(&a.kind).await?)
        }
        "refresh_market_data_cmd" => out(market::refresh_market_data(db).await?),
        "get_setting" => out(settings::get_setting(db, uid, args(body)?).await?),
        "set_setting" => out(settings::set_setting(db, uid, args(body)?).await?),

        // ---- wallets ----
        "list_wallets" => out(wallets::list_wallets(db, uid, args(body)?).await?),
        "get_wallet" => out(wallets::get_wallet(db, uid, args(body)?).await?),
        "create_wallet" => out(wallets::create_wallet(db, uid, args(body)?).await?),
        "update_wallet" => out(wallets::update_wallet(db, uid, args(body)?).await?),
        "archive_wallet" => out(wallets::archive_wallet(db, uid, args(body)?).await?),
        "delete_wallet" => out(wallets::delete_wallet(db, uid, args(body)?).await?),

        // ---- transactions ----
        "add_income" => out(transactions::add_income(db, uid, args(body)?).await?),
        "add_expense" => out(transactions::add_expense(db, uid, args(body)?).await?),
        "add_transfer" => out(transactions::add_transfer(db, uid, args(body)?).await?),
        "list_transactions" => out(transactions::list_transactions(db, uid, args(body)?).await?),
        "delete_transaction" => out(transactions::delete_transaction(db, uid, args(body)?).await?),
        "list_transaction_categories" => {
            out(transactions::list_transaction_categories(db, uid).await?)
        }
        "create_transaction_category" => {
            out(transactions::create_transaction_category(db, uid, args(body)?).await?)
        }

        // ---- dashboard ----
        "get_dashboard_summary" => out(dashboard::get_dashboard_summary(db, uid).await?),

        // ---- investments ----
        "list_calculators" => out(investments::list_calculators()),
        "get_investment_catalog" => out(investments::get_investment_catalog().await),
        "list_investments" => out(investments::list_investments(db, uid, args(body)?).await?),
        "create_investment" => out(investments::create_investment(db, uid, args(body)?).await?),
        "update_investment" => out(investments::update_investment(db, uid, args(body)?).await?),
        "close_investment" => out(investments::close_investment(db, uid, args(body)?).await?),
        "delete_investment" => out(investments::delete_investment(db, uid, args(body)?).await?),
        "add_snapshot" => out(investments::add_snapshot(db, uid, args(body)?).await?),
        "get_investment_detail" => {
            out(investments::get_investment_detail(db, uid, args(body)?).await?)
        }
        "add_investment_movement" => {
            out(investments::add_investment_movement(db, uid, args(body)?).await?)
        }
        "delete_investment_movement" => {
            out(investments::delete_investment_movement(db, uid, args(body)?).await?)
        }

        _ => Err(AppError::NotFound("comando")),
    }
}
