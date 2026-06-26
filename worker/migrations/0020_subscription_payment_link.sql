-- Link a booked expense back to the subscription it paid, so the dashboard can
-- count REAL charges in a period (what was actually registered) instead of
-- projecting from the billing calendar. Nullable: normal transactions stay NULL.
ALTER TABLE transactions ADD COLUMN subscription_id INTEGER REFERENCES subscriptions(id);
CREATE INDEX idx_tx_subscription ON transactions(subscription_id, occurred_at);

-- Backfill: payments registered before this migration were booked "loose"
-- (no link). register_subscription_payment always writes description = the
-- subscription's name into the subscription's own wallet, so match on that.
UPDATE transactions
SET subscription_id = (
  SELECT s.id FROM subscriptions s
  WHERE s.name = transactions.description
    AND s.wallet_id = transactions.wallet_id
  ORDER BY s.id
  LIMIT 1
)
WHERE transactions.kind = 'expense'
  AND transactions.subscription_id IS NULL
  AND transactions.description IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM subscriptions s2
    WHERE s2.name = transactions.description
      AND s2.wallet_id = transactions.wallet_id
  );
