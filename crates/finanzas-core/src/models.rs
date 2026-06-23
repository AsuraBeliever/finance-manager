use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Currency {
    pub code: String,
    pub name: String,
    pub symbol: String,
    pub decimals: i64,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WalletCategory {
    pub id: i64,
    pub name: String,
    pub icon: Option<String>,
    pub is_system: bool,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Wallet {
    pub id: i64,
    pub name: String,
    pub category_id: i64,
    pub category_name: String,
    pub currency_code: String,
    pub initial_balance_cents: i64,
    /// Computed: initial balance + signed sum of transactions.
    pub balance_cents: i64,
    /// Computed: total earmarked in active goal "apartados" on this wallet. The
    /// available balance is `balance_cents - reserved_cents`.
    #[serde(default)]
    pub reserved_cents: i64,
    pub color: Option<String>,
    /// Card skin: a catalog id ("oro"), a custom gradient, or an imported image.
    pub skin: Option<String>,
    pub notes: Option<String>,
    pub is_archived: bool,
    /// Yield: annual rate in basis points, or None when the wallet earns nothing.
    pub yield_rate_bps: Option<i64>,
    /// Payout cadence ('weekly' | 'biweekly' | 'monthly') when yield is on.
    pub yield_frequency: Option<String>,
    /// 'YYYY-MM-DD' the day yield was switched on (None when off).
    pub yield_anchor_date: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TransactionCategory {
    pub id: i64,
    pub name: String,
    pub kind: String, // 'income' | 'expense'
    pub icon: Option<String>,
    pub color: Option<String>,
    pub is_system: bool,
    /// True when this (system/seed) category is hidden for the current user.
    /// Always false for the user's own categories. Used by the category manager.
    pub is_hidden: bool,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Transaction {
    pub id: i64,
    pub wallet_id: i64,
    pub wallet_name: String,
    pub kind: String, // 'income' | 'expense' | 'transfer_in' | 'transfer_out'
    pub amount_cents: i64,
    pub category_id: Option<i64>,
    pub category_name: Option<String>,
    pub transfer_group_id: Option<String>,
    pub description: Option<String>,
    pub occurred_at: String,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExchangeRate {
    pub currency_code: String,
    pub rate_to_mxn_micros: i64,
    pub as_of: String,
    pub source: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Investment {
    pub id: i64,
    pub name: String,
    pub calculator: String, // 'nu_cajita' | 'cetes' | 'fixed_rate' | 'manual'
    pub currency_code: String,
    pub principal_cents: i64,
    pub start_date: String, // 'YYYY-MM-DD'
    pub params_json: String,
    pub linked_wallet_id: Option<i64>,
    pub is_closed: bool,
    pub notes: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentMovement {
    pub id: i64,
    pub investment_id: i64,
    pub kind: String, // 'deposit' | 'withdrawal'
    pub amount_cents: i64,
    pub occurred_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InvestmentSnapshot {
    pub id: i64,
    pub investment_id: i64,
    pub value_cents: i64,
    pub as_of: String,
    pub source: String,
}
