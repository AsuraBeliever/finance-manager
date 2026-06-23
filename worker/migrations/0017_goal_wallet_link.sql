-- Migration number: 0017 	 2026-06-22
-- Savings goals can now optionally move money from/to a wallet on each
-- contribution (same model as investments): a contribution may be a plain
-- tracking entry ("from nowhere", e.g. when you're just starting to use the
-- app) or it can post an expense/income on a chosen wallet. The last wallet
-- used is remembered here as the goal's default. See docs/DATA_MODEL.md.

ALTER TABLE savings_goals ADD COLUMN linked_wallet_id INTEGER REFERENCES wallets(id);
