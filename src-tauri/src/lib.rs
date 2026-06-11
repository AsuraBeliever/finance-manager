pub(crate) mod commands;
mod db;
mod error;
mod investments;
mod models;

use std::fs;
use std::sync::Mutex;
use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // WebKitGTK's DMABUF renderer is unstable on NVIDIA + Wayland: GBM
    // buffer failures escalate to a Wayland protocol error (71) that kills
    // the app at startup. Must be set before the webview is created.
    if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
        std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
    }

    tauri::Builder::default()
        // NOTE: tauri-plugin-single-instance was tried and removed — its DBus
        // signaling crashes the running instance under GTK3/Wayland (protocol
        // error 71). Single-instance behavior lives in scripts/finanzas-open
        // (pgrep guard); launching the bare binary twice is unsupported.
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let data_dir = app.path().app_data_dir()?;
            fs::create_dir_all(&data_dir)?;
            let conn = db::open(&data_dir.join("finanzas.db"))
                .map_err(|e| format!("failed to open database: {e}"))?;
            app.manage(db::Db(Mutex::new(conn)));

            // Refresh market data in the background on startup, silent on
            // failure (offline keeps the last cached values): fx rates,
            // Banxico target-rate history (bonddia) and crypto prices.
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                let db = handle.state::<db::Db>();
                if let Err(e) = commands::settings::fetch_and_store_rates(&db, false).await {
                    eprintln!("exchange rate auto-update failed: {e}");
                }
                if let Err(e) = commands::settings::refresh_market_data(&db).await {
                    eprintln!("market data auto-update failed: {e}");
                }
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::settings::list_currencies,
            commands::settings::list_wallet_categories,
            commands::settings::get_exchange_rates,
            commands::settings::set_exchange_rate,
            commands::settings::fetch_exchange_rates,
            commands::settings::add_currency,
            commands::settings::fetch_banxico_rate,
            commands::settings::refresh_market_data_cmd,
            commands::settings::get_setting,
            commands::settings::set_setting,
            commands::wallets::list_wallets,
            commands::wallets::get_wallet,
            commands::wallets::create_wallet,
            commands::wallets::update_wallet,
            commands::wallets::archive_wallet,
            commands::wallets::delete_wallet,
            commands::transactions::add_income,
            commands::transactions::add_expense,
            commands::transactions::add_transfer,
            commands::transactions::list_transactions,
            commands::transactions::delete_transaction,
            commands::transactions::list_transaction_categories,
            commands::transactions::create_transaction_category,
            commands::dashboard::get_dashboard_summary,
            commands::investments::list_calculators,
            commands::investments::get_investment_catalog,
            commands::investments::list_investments,
            commands::investments::create_investment,
            commands::investments::update_investment,
            commands::investments::close_investment,
            commands::investments::delete_investment,
            commands::investments::add_snapshot,
            commands::investments::get_investment_detail,
            commands::investments::add_investment_movement,
            commands::investments::delete_investment_movement,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
