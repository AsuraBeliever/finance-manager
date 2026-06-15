-- Migration number: 0007 	 2026-06-15
-- User-defined display order for wallets (drag-to-reorder in the wallets grid).
-- Lower sort_order shows first; ties fall back to created_at, id. Seed existing
-- rows with their current order (per user) so nothing visibly moves on rollout.
-- Keep docs/DATA_MODEL.md in sync.
ALTER TABLE wallets ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

UPDATE wallets SET sort_order = (
  SELECT COUNT(*)
  FROM wallets w2
  WHERE w2.user_id IS wallets.user_id
    AND (w2.created_at < wallets.created_at
         OR (w2.created_at = wallets.created_at AND w2.id < wallets.id))
);
