-- Rate history for yield-bearing wallets.
--
-- Recomputing a past day's interest needs the rate that actually applied THAT
-- day. Without it a correction would repaint history with the current rate:
-- the Nu cajita that moved from 6.50% to 13.00% would have all of August
-- recomputed at 13%, inflating the balance instead of fixing it.
CREATE TABLE wallet_yield_rates (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  wallet_id      INTEGER NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
  effective_from TEXT    NOT NULL,  -- YYYY-MM-DD, inclusive
  rate_bps       INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_wallet_yield_rates ON wallet_yield_rates(wallet_id, effective_from);

-- Seed today's rate for every wallet that already has yield running.
--
-- Deliberately NOT back-filled to yield_anchor_date: we have no record of when
-- past rates changed, and guessing is exactly the failure this table exists to
-- prevent. Reconciliation refuses to touch days before the earliest known rate,
-- so coverage starts here and grows forward.
--
-- DATE('now','-6 hours') keeps this on the same America/Mexico_City day the
-- worker uses (db::today_mx), so the seed can't land on tomorrow.
INSERT INTO wallet_yield_rates (wallet_id, effective_from, rate_bps)
SELECT id, DATE('now', '-6 hours'), yield_rate_bps
FROM wallets
WHERE yield_rate_bps IS NOT NULL AND yield_rate_bps > 0;
