-- Migration number: 0005 	 2026-06-13
-- Dashboard features: savings goals, monthly budgets/limits, and recurring
-- subscriptions. All per-user (scoped by user_id). Keep docs/DATA_MODEL.md in sync.

-- ---- savings goals ----
-- A target with a manually-tracked saved amount; progress = saved / target.
CREATE TABLE savings_goals (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  icon TEXT,
  color TEXT,
  currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
  target_cents INTEGER NOT NULL CHECK (target_cents > 0),
  saved_cents INTEGER NOT NULL DEFAULT 0 CHECK (saved_cents >= 0),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_goals_user ON savings_goals(user_id);

-- ---- budgets / spending limits ----
-- One monthly limit per (user, category); category_id NULL = overall limit.
-- spent is computed at read time from transactions, never stored.
CREATE TABLE budgets (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  category_id INTEGER REFERENCES transaction_categories(id),
  limit_cents INTEGER NOT NULL CHECK (limit_cents > 0),
  period TEXT NOT NULL DEFAULT 'monthly' CHECK (period IN ('monthly')),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
-- COALESCE so the overall (NULL category) limit is also unique per user.
CREATE UNIQUE INDEX idx_budgets_unique ON budgets(user_id, COALESCE(category_id, 0));

-- ---- recurring subscriptions ----
CREATE TABLE subscriptions (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  icon TEXT,
  color TEXT,
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
  cadence TEXT NOT NULL DEFAULT 'monthly' CHECK (cadence IN ('monthly','yearly')),
  next_charge_date TEXT NOT NULL,                 -- 'YYYY-MM-DD'
  wallet_id INTEGER REFERENCES wallets(id),       -- where a registered payment lands
  category_id INTEGER REFERENCES transaction_categories(id),
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_subs_user ON subscriptions(user_id);
