-- Migration number: 0014 	 2026-06-21
-- Historical economy: budget limits change over time, so the dashboard can no
-- longer assume the current limit applied in the past. This append-only history
-- records the monthly limit in effect from a given month. The `budgets` table
-- stays as the "current" pointer; this table is read when scoping a budget to a
-- past period (limit prorated by day, honoring changes). See docs/DATA_MODEL.md.

CREATE TABLE budget_limit_history (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  category_id INTEGER REFERENCES transaction_categories(id),
  limit_cents INTEGER NOT NULL CHECK (limit_cents > 0),
  effective_from TEXT NOT NULL,                 -- 'YYYY-MM-DD' (start of month)
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_budget_hist
  ON budget_limit_history(user_id, COALESCE(category_id, 0), effective_from);

-- Seed: existing budgets cover the whole past as a baseline (effective from the
-- epoch), so historical views show the current limit until real changes accrue.
INSERT INTO budget_limit_history (user_id, category_id, limit_cents, effective_from)
SELECT user_id, category_id, limit_cents, '1970-01-01' FROM budgets;
