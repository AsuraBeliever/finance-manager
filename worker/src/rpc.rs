//! RPC dispatcher: POST /api/rpc/<commandName> with a JSON body whose shape
//! matches the old Tauri invoke payload (camelCase args), so src/lib/api.ts
//! only swaps the transport. Every command requires a session.

use finanzas_core::error::{AppError, AppResult};
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde_json::Value;
use worker::{D1Database, Request, Response, RouteContext};

use crate::error::{db_err, error_response};
use crate::handlers::{
    analytics, budgets, credit, dashboard, goals, investments, notifications, settings,
    subscriptions, transactions, wallets,
};
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
        "get_exchange_rates" => out(settings::get_exchange_rates(db, uid).await?),
        "set_exchange_rate" => out(settings::set_exchange_rate(db, uid, args(body)?).await?),
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
        "reorder_wallets" => out(wallets::reorder_wallets(db, uid, args(body)?).await?),
        "delete_wallet" => out(wallets::delete_wallet(db, uid, args(body)?).await?),

        // ---- credit cards ----
        "get_credit_card_summary" => {
            out(credit::get_credit_card_summary(db, uid, args(body)?).await?)
        }
        "preview_msi_plan" => out(credit::preview_msi_plan(db, uid, args(body)?).await?),
        "create_msi_plan" => out(credit::create_msi_plan(db, uid, args(body)?).await?),
        "delete_msi_plan" => out(credit::delete_msi_plan(db, uid, args(body)?).await?),

        // ---- transactions ----
        "add_income" => out(transactions::add_income(db, uid, args(body)?).await?),
        "add_expense" => out(transactions::add_expense(db, uid, args(body)?).await?),
        "add_transfer" => out(transactions::add_transfer(db, uid, args(body)?).await?),
        "list_transactions" => out(transactions::list_transactions(db, uid, args(body)?).await?),
        "update_transaction" => out(transactions::update_transaction(db, uid, args(body)?).await?),
        "delete_transaction" => out(transactions::delete_transaction(db, uid, args(body)?).await?),
        "list_transaction_categories" => {
            out(transactions::list_transaction_categories(db, uid, args(body)?).await?)
        }
        "list_manage_categories" => out(transactions::list_manage_categories(db, uid).await?),
        "create_transaction_category" => {
            out(transactions::create_transaction_category(db, uid, args(body)?).await?)
        }
        "update_transaction_category" => {
            out(transactions::update_transaction_category(db, uid, args(body)?).await?)
        }
        "delete_transaction_category" => {
            out(transactions::delete_transaction_category(db, uid, args(body)?).await?)
        }
        "restore_transaction_category" => {
            out(transactions::restore_transaction_category(db, uid, args(body)?).await?)
        }
        "reorder_transaction_categories" => {
            out(transactions::reorder_transaction_categories(db, uid, args(body)?).await?)
        }

        // ---- dashboard / analytics ----
        "get_dashboard_summary" => {
            out(dashboard::get_dashboard_summary(db, uid, args(body)?).await?)
        }
        "get_category_breakdown" => {
            out(analytics::get_category_breakdown(db, uid, args(body)?).await?)
        }
        "get_spending_trends" => out(analytics::get_spending_trends(db, uid, args(body)?).await?),

        // ---- savings goals ----
        "list_savings_goals" => out(goals::list_savings_goals(db, uid, args(body)?).await?),
        "create_savings_goal" => out(goals::create_savings_goal(db, uid, args(body)?).await?),
        "update_savings_goal" => out(goals::update_savings_goal(db, uid, args(body)?).await?),
        "contribute_savings_goal" => {
            out(goals::contribute_savings_goal(db, uid, args(body)?).await?)
        }
        "delete_savings_goal" => out(goals::delete_savings_goal(db, uid, args(body)?).await?),
        "use_savings_goal" => out(goals::use_savings_goal(db, uid, args(body)?).await?),
        "convert_goal_to_wallet" => out(goals::convert_goal_to_wallet(db, uid, args(body)?).await?),
        "reorder_savings_goals" => out(goals::reorder_savings_goals(db, uid, args(body)?).await?),
        "update_goal_contribution" => {
            out(goals::update_goal_contribution(db, uid, args(body)?).await?)
        }
        "delete_goal_contribution" => {
            out(goals::delete_goal_contribution(db, uid, args(body)?).await?)
        }

        // ---- budgets ----
        "list_budgets" => out(budgets::list_budgets(db, uid, args(body)?).await?),
        "set_budget" => out(budgets::set_budget(db, uid, args(body)?).await?),
        "delete_budget" => out(budgets::delete_budget(db, uid, args(body)?).await?),

        // ---- subscriptions ----
        "list_subscriptions" => out(subscriptions::list_subscriptions(db, uid, args(body)?).await?),
        "create_subscription" => {
            out(subscriptions::create_subscription(db, uid, args(body)?).await?)
        }
        "update_subscription" => {
            out(subscriptions::update_subscription(db, uid, args(body)?).await?)
        }
        "set_subscription_active" => {
            out(subscriptions::set_subscription_active(db, uid, args(body)?).await?)
        }
        "register_subscription_payment" => {
            out(subscriptions::register_subscription_payment(db, uid, args(body)?).await?)
        }
        "delete_subscription" => {
            out(subscriptions::delete_subscription(db, uid, args(body)?).await?)
        }

        // ---- notifications ----
        "list_notifications" => out(notifications::list_notifications(db, uid, args(body)?).await?),
        "mark_notifications_read" => {
            out(notifications::mark_notifications_read(db, uid, args(body)?).await?)
        }
        "list_investment_reminders" => {
            out(notifications::list_investment_reminders(db, uid).await?)
        }
        "set_investment_reminder" => {
            out(notifications::set_investment_reminder(db, uid, args(body)?).await?)
        }

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
        "simulate_investment" => out(investments::simulate_investment(args(body)?)?),
        "solve_contribution" => out(investments::solve_contribution(args(body)?)?),
        "get_portfolio" => out(investments::get_portfolio(db, uid).await?),
        "project_investment" => out(investments::project_investment(db, uid, args(body)?).await?),

        _ => Err(AppError::NotFound("comando")),
    }
}
