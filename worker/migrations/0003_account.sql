-- Migration number: 0003 	 2026-06-12
-- Device info on sessions (account page: list/revoke logged-in devices) and
-- offline-outbox idempotency for transactions.

ALTER TABLE sessions ADD COLUMN user_agent TEXT;
ALTER TABLE sessions ADD COLUMN last_seen_at TEXT;

-- Client-generated id so an offline capture replayed after a dropped
-- response never inserts twice. Transfers carry it on the transfer_out leg.
ALTER TABLE transactions ADD COLUMN client_id TEXT;
CREATE UNIQUE INDEX idx_tx_client ON transactions(client_id) WHERE client_id IS NOT NULL;
