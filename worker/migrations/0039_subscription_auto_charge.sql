-- Migration number: 0039 	 2026-10-10
-- Opt-in automatic charging for subscriptions.
--
-- auto_charge = 0 (default, every existing subscription): nothing is booked
--   until the user presses "register payment" (expense dated that day).
-- auto_charge = 1: the daily cron books an expense into wallet_id on each
--   due next_charge_date (dated that charge date, client_id
--   'sub:<id>:<YYYY-MM-DD>' for idempotency) and advances next_charge_date.
--   Requires wallet_id (validated in the handler).
ALTER TABLE subscriptions ADD COLUMN auto_charge INTEGER NOT NULL DEFAULT 0;
