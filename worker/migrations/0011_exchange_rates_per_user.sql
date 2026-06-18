-- Per-user manual exchange rates. Until now exchange_rates was a single global
-- table, so one user's manual override changed everyone's converted balances.
-- Add user_id: 0 = the global auto-fetched rates (cron: 'api' / 'banxico_fix'),
-- any other id = that user's manual override. Reads prefer the user's own row
-- and fall back to the global one (same precedence trick as `settings`).
-- Existing rows keep user_id 0 (the global fallback), which is the daily cron's
-- job anyway.
ALTER TABLE exchange_rates ADD COLUMN user_id INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_exchange_rates_lookup ON exchange_rates (currency_code, user_id, id);
