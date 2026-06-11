//! Cryptocurrency holdings. The investment stores the QUANTITY held
//! (params.quantity_e8 = units × 1e8, exact integer) and is valued in MXN
//! with the latest cached price (crypto_prices table, refreshed from
//! CoinGecko). Movements track the MXN you spent/received (buys and sells)
//! so gain = current value − net invested; update the quantity when you buy
//! or sell.
//!
//! params: {"symbol": "BTC", "quantity_e8": 5000000}   (0.05 BTC)
//!
//! Past/future `as_of` dates use the same latest price (no price history),
//! so projections are flat by design.

use chrono::NaiveDate;
use rusqlite::Connection;

use super::{parse_params, InvestmentCalculator};
use crate::error::{AppError, AppResult};
use crate::models::Investment;

/// symbol → CoinGecko id, for the supported coins.
pub const COINS: &[(&str, &str)] = &[
    ("BTC", "bitcoin"),
    ("ETH", "ethereum"),
    ("SOL", "solana"),
    ("XRP", "ripple"),
    ("DOGE", "dogecoin"),
    ("ADA", "cardano"),
    ("USDT", "tether"),
    ("USDC", "usd-coin"),
    ("BNB", "binancecoin"),
    ("LTC", "litecoin"),
];

pub fn coingecko_id(symbol: &str) -> Option<&'static str> {
    COINS.iter().find(|(s, _)| *s == symbol).map(|(_, id)| *id)
}

pub struct Crypto;

impl InvestmentCalculator for Crypto {
    fn id(&self) -> &'static str {
        "crypto"
    }

    fn value_at(&self, inv: &Investment, conn: &Connection, _as_of: NaiveDate) -> AppResult<i64> {
        let params = parse_params(inv)?;
        let symbol = params
            .get("symbol")
            .and_then(|v| v.as_str())
            .ok_or_else(|| AppError::InvalidInput("falta el parámetro 'symbol'".into()))?;
        let quantity_e8 = params
            .get("quantity_e8")
            .and_then(|v| v.as_i64())
            .ok_or_else(|| AppError::InvalidInput("falta el parámetro 'quantity_e8'".into()))?;

        let price_cents: Option<i64> = conn
            .query_row(
                "SELECT price_mxn_cents FROM crypto_prices WHERE symbol = ?1",
                [symbol],
                |r| r.get(0),
            )
            .map(Some)
            .or_else(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => Ok(None),
                other => Err(other),
            })?;
        let Some(price_cents) = price_cents else {
            // no price yet (offline first run): show what was paid for it
            return Ok(inv.principal_cents);
        };
        Ok(((quantity_e8 as i128 * price_cents as i128) / 100_000_000i128) as i64)
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;
    use crate::investments::test_investment;

    #[test]
    fn values_quantity_at_cached_price() {
        let conn = open_in_memory();
        // 1 BTC = $2,000,000.00 MXN; holding 0.05 BTC → $100,000.00
        conn.execute(
            "INSERT INTO crypto_prices (symbol, price_mxn_cents) VALUES ('BTC', 200000000)",
            [],
        )
        .unwrap();
        let inv = test_investment(
            "crypto",
            9_000_000,
            r#"{"symbol":"BTC","quantity_e8":5000000}"#,
        );
        let date = NaiveDate::from_ymd_opt(2026, 6, 10).unwrap();
        assert_eq!(Crypto.value_at(&inv, &conn, date).unwrap(), 10_000_000);
    }

    #[test]
    fn falls_back_to_principal_without_price() {
        let conn = open_in_memory();
        let inv = test_investment(
            "crypto",
            9_000_000,
            r#"{"symbol":"BTC","quantity_e8":5000000}"#,
        );
        let date = NaiveDate::from_ymd_opt(2026, 6, 10).unwrap();
        assert_eq!(Crypto.value_at(&inv, &conn, date).unwrap(), 9_000_000);
    }

    #[test]
    fn known_symbols_map_to_coingecko_ids() {
        assert_eq!(coingecko_id("BTC"), Some("bitcoin"));
        assert_eq!(coingecko_id("PEPE"), None);
    }
}
