-- Migration number: 0018 	 2026-06-22
-- User-defined display order for savings goals (drag-to-reorder in the goals
-- page). Lower sort_order shows first; the first goal is the "principal" shown
-- as the gauge/circle on the dashboard, the rest as bars. Ties fall back to
-- created_at, id. Seed existing rows with their current order per user so
-- nothing visibly moves on rollout. Keep docs/DATA_MODEL.md in sync.

ALTER TABLE savings_goals ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

UPDATE savings_goals SET sort_order = (
  SELECT COUNT(*)
  FROM savings_goals g2
  WHERE g2.user_id IS savings_goals.user_id
    AND (g2.created_at < savings_goals.created_at
         OR (g2.created_at = savings_goals.created_at AND g2.id < savings_goals.id))
);
