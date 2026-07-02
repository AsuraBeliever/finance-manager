-- Migration number: 0027 	 2026-07-01
-- A wallet can be a credit card. credit_cut_day (1-31, clamped to month end)
-- is the discriminator: NULL = a plain wallet, set = a credit card. Spending
-- drives the balance negative (debt = -balance); paying the card is a normal
-- transfer from a debit wallet. Everything else is derived in Rust
-- (finanzas-core::credit), never stored: next cut date, statement balance
-- ("saldo al corte" = debt as of the last cut), amount left to pay it, the
-- payment due date (cut + credit_due_days), utilization vs the limit and
-- available credit. See docs/DATA_MODEL.md.

ALTER TABLE wallets ADD COLUMN credit_cut_day INTEGER;      -- 1-31; NULL = not a credit card
ALTER TABLE wallets ADD COLUMN credit_due_days INTEGER;     -- days after the cut to pay without interest (MX banks: ~20)
ALTER TABLE wallets ADD COLUMN credit_limit_cents INTEGER;  -- credit line; NULL = untracked
ALTER TABLE wallets ADD COLUMN credit_anniversary TEXT;     -- 'MM-DD' the bank charges the annual fee; NULL = untracked

-- Purchases in fixed monthly installments ("meses sin intereses"). The plan is
-- NOT a transaction: the daily cron posts one expense per installment on each
-- cut date (client_id 'msi:<plan>:<n>' keeps it idempotent), so the wallet's
-- debt reflects what has been billed — matching the bank statement — while the
-- unbilled remainder still counts against available credit.
CREATE TABLE msi_plans (
  id INTEGER PRIMARY KEY,
  wallet_id INTEGER NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
  description TEXT NOT NULL,
  total_cents INTEGER NOT NULL CHECK (total_cents > 0),
  months INTEGER NOT NULL CHECK (months > 1),
  purchased_at TEXT NOT NULL,                        -- 'YYYY-MM-DD'
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_msi_wallet ON msi_plans(wallet_id);

-- Reserved expense category for MSI charges, mirroring 0023's 'Metas': posted
-- only by the cron, hidden from pickers, localized in src/i18n/seed.ts.
INSERT INTO transaction_categories (user_id, name, kind, icon, is_system, is_reserved)
VALUES (NULL, 'Meses sin intereses', 'expense', 'calendar-clock', 1, 1);
