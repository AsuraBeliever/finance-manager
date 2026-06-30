-- Migration number: 0022 	 2026-06-30
-- Log of individual apartado moves so the transactions history can show, for
-- tracking, each time money was set aside in (or released from) a goal — like a
-- Nu cajita movement. These are INFORMATIONAL: the money never leaves the wallet
-- (apartado = earmark, still in your balance and net worth), so this lives in
-- its own table and is merged into the transactions list at read time only. It
-- never touches balance/flow math (those read the `transactions` table). The
-- running earmark stays in savings_goals.saved_cents; this is just the trail.
-- amount_cents is signed: positive = reserved more, negative = released.
-- See docs/DATA_MODEL.md.

CREATE TABLE goal_contributions (
  id INTEGER PRIMARY KEY,
  goal_id INTEGER NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
  amount_cents INTEGER NOT NULL,                          -- + reserva, − liberación
  occurred_at TEXT NOT NULL,                              -- 'YYYY-MM-DD'
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_goal_contributions_goal ON goal_contributions(goal_id);
