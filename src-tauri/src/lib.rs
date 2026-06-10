mod commands;
mod db;
mod error;
mod models;

use std::fs;
use std::sync::Mutex;
use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let data_dir = app.path().app_data_dir()?;
            fs::create_dir_all(&data_dir)?;
            let conn = db::open(&data_dir.join("finanzas.db"))
                .map_err(|e| format!("failed to open database: {e}"))?;
            app.manage(db::Db(Mutex::new(conn)));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::settings::list_currencies,
            commands::settings::list_wallet_categories,
            commands::settings::get_exchange_rates,
            commands::settings::set_exchange_rate,
            commands::settings::add_currency,
            commands::wallets::list_wallets,
            commands::wallets::get_wallet,
            commands::wallets::create_wallet,
            commands::wallets::update_wallet,
            commands::wallets::archive_wallet,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
