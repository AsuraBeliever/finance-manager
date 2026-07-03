-- Migration number: 0029 	 2026-07-02
-- In-app notifications + per-investment reminders. A notification row stores
-- kind (an i18n key) plus raw params (cents, dates, names) — the frontend
-- renders the text in the active locale. dedupe_key ('<kind>:<entity>:<date>')
-- with the unique index makes cron re-runs no-ops (same idempotency scheme as
-- yield/MSI client_id). Rows older than 60 days are pruned by the daily cron.
-- See docs/DATA_MODEL.md.

CREATE TABLE notifications (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  kind TEXT NOT NULL,
  params_json TEXT NOT NULL DEFAULT '{}',
  dedupe_key TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  read_at TEXT
);
CREATE UNIQUE INDEX idx_notifications_dedupe ON notifications(user_id, dedupe_key);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);

-- User-configured reminders per investment ("remind me to contribute every X"
-- / "tell me every X how much it has earned"). Cron state lives here:
-- last_fired_date drives the next occurrence, last_value_cents the earnings
-- delta shown in each performance summary.
CREATE TABLE investment_reminders (
  id INTEGER PRIMARY KEY,
  investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('contribute','performance')),
  cadence TEXT NOT NULL CHECK (cadence IN ('daily','weekly','biweekly','monthly')),
  anchor_date TEXT NOT NULL,
  last_fired_date TEXT,
  last_value_cents INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX idx_inv_reminder_unique ON investment_reminders(investment_id, kind);
