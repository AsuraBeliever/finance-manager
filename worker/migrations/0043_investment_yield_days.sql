-- Migration number: 0043 	 2026-11-08
-- Daily yield of every investment (FIN-9): what each one earned per day, so
-- it shows in the transactions list and counts as income of its month even
-- when nothing is withdrawn. Derived data: finanzas_core::investments::
-- daily_yield computes it, handlers::investment_yield keeps it current.
--
-- gain_cents        what the investment earned that day (can be negative),
--                   in the investment's own currency. Deposits/withdrawals
--                   never count — they move value and capital alike.
-- total_gain_cents  the position's gain (value − net invested) on that day,
--                   i.e. the running sum of gain_cents; an observed history
--                   (crypto, BONDDIA títulos) continues from it.
--
-- Days that earned exactly nothing are not stored, except an observed
-- history's baseline row (gain 0), which readers skip.
--
-- (0042 is taken by the bank-notices branch, FIN-5.)
CREATE TABLE investment_yield_days (
  investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  day TEXT NOT NULL,                       -- 'YYYY-MM-DD'
  gain_cents INTEGER NOT NULL,
  total_gain_cents INTEGER NOT NULL,
  PRIMARY KEY (investment_id, day)
);
CREATE INDEX idx_inv_yield_day ON investment_yield_days(day);
