pub(crate) mod commands;
mod db;

// Pure financial logic lives in the shared finanzas-core crate (also used by
// the Cloudflare Worker); re-exported so internal `crate::` paths keep working.
pub(crate) use finanzas_core::error;
pub(crate) use finanzas_core::investments;
pub(crate) use finanzas_core::models;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // WebKitGTK's DMABUF renderer is unstable on NVIDIA + Wayland: GBM
    // buffer failures escalate to a Wayland protocol error (71) that kills
    // the app at startup. Must be set before the webview is created.
    if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
        std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
    }

    // v2.0.0: the window loads the deployed cloud app (tauri.conf.json) and
    // talks to the Worker over HTTP — nothing invokes these commands anymore.
    // The local backend stays compiled but DORMANT: the local finanzas.db is
    // the pre-migration read-only backup and must never be opened for writing
    // (no db open, no managed state, no startup market refresh).
    tauri::Builder::default()
        // NOTE: tauri-plugin-single-instance was tried and removed — its DBus
        // signaling crashes the running instance under GTK3/Wayland (protocol
        // error 71). Single-instance behavior lives in scripts/finanzas-open
        // (pgrep guard); launching the bare binary twice is unsupported.
        .plugin(tauri_plugin_opener::init())
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
