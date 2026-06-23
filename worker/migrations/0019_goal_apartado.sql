-- Migration number: 0019 	 2026-06-22
-- Goals become "apartados" (earmarked pockets) of a wallet, like BBVA Apartados
-- or Nu cajitas. A goal with linked_wallet_id (0017) now RESERVES part of that
-- wallet's balance instead of spending it: contributing earmarks, the money
-- stays in the wallet and in net worth. archived_at marks a goal whose money was
-- used (a real expense was booked) or that was closed; archived goals no longer
-- reserve. NULL = active. See docs/DATA_MODEL.md.

ALTER TABLE savings_goals ADD COLUMN archived_at TEXT;  -- 'YYYY-MM-DD' or NULL
