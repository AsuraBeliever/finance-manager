-- Migration number: 0030 	 2026-07-03
-- The "behind pace" check anchored at the goal's creation date, so adding a
-- deadline to an existing goal instantly flagged it "atrasada" (the app
-- pretended you should have been saving since day one toward a deadline that
-- didn't exist yet). The pace now starts the day the deadline is SET:
-- plan_anchor_date is stamped whenever target_date is set or changed, cleared
-- when removed, and read as the pace start (created_at remains the fallback).
-- Backfill with today: deadlines only exist since v2.20 (2026-07-01), so the
-- error is at most a couple of days and nobody gets a false "atrasada".

ALTER TABLE savings_goals ADD COLUMN plan_anchor_date TEXT;

UPDATE savings_goals SET plan_anchor_date = date('now') WHERE target_date IS NOT NULL;
