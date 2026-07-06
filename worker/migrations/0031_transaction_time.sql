-- Migration number: 0031 	 2026-07-06
-- Transactions only tracked a business date (occurred_at, 'YYYY-MM-DD'); the
-- internal created_at is a UTC insert timestamp never shown to the user. To let
-- the ledger record and edit the wall-clock time of a movement, add an optional
-- occurred_time as local 'HH:MM' (24h), consistent with occurred_at being the
-- user's local date. NULL = legacy rows (and apartado rows) with no known time.

ALTER TABLE transactions ADD COLUMN occurred_time TEXT;
