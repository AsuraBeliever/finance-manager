-- Migration number: 0015 	 2026-06-21
-- Historical economy: savings goals only stored their current `saved_cents`, so
-- there was no way to know a goal's progress on a past date. These snapshots
-- record the saved amount over time (on each contribution and via the daily
-- cron), so a historical period reads the latest snapshot at or before its end.
-- See docs/DATA_MODEL.md.

CREATE TABLE goal_snapshots (
  id INTEGER PRIMARY KEY,
  goal_id INTEGER NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
  saved_cents INTEGER NOT NULL,
  as_of TEXT NOT NULL,                          -- 'YYYY-MM-DD'
  source TEXT NOT NULL DEFAULT 'auto'
);
CREATE INDEX idx_goal_snap ON goal_snapshots(goal_id, as_of);

-- Seed: one snapshot per existing goal at its creation date with its current
-- saved amount (the baseline; real history accrues going forward).
INSERT INTO goal_snapshots (goal_id, saved_cents, as_of, source)
SELECT id, saved_cents, date(created_at), 'seed' FROM savings_goals;
