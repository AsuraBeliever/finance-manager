-- Migration number: 0041 	 2026-10-16
-- Editable time of day for automatic subscription charges.
--
-- charge_time = 'HH:MM' (24h, Mexico City wall clock): the cron books the
--   charge on its date once that time is reached, and the expense carries it
--   as occurred_time.
-- charge_time = NULL (default, every existing subscription): charged from the
--   start of the day, expense without its own time — the 0039 behaviour.
ALTER TABLE subscriptions ADD COLUMN charge_time TEXT;
