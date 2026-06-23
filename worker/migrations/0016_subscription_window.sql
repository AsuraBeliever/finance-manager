-- Migration number: 0016 	 2026-06-21
-- Historical economy: subscriptions only had `is_active` (a present-tense flag),
-- so a past period couldn't tell which subs were active then. These columns
-- record the active window: a sub counts for a date D when started_at <= D and
-- (ended_at IS NULL OR ended_at > D). Cancelling sets ended_at; deleting still
-- removes the row (loses its history). See docs/DATA_MODEL.md.

ALTER TABLE subscriptions ADD COLUMN started_at TEXT;  -- 'YYYY-MM-DD'
ALTER TABLE subscriptions ADD COLUMN ended_at TEXT;    -- 'YYYY-MM-DD' or NULL

-- Baseline for existing rows: active from creation; inactive ones treated as
-- ended at creation (their real cancellation date is unknown).
UPDATE subscriptions SET started_at = date(created_at);
UPDATE subscriptions SET ended_at = date(created_at) WHERE is_active = 0;
