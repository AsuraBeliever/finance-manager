-- Migration number: 0021 	 2026-06-28
-- Goals gain an optional deadline so the app can suggest how much to set aside
-- each period to arrive on time. target_date is the date you want to reach the
-- target by; contribution_cadence is how often you plan to put money in
-- ('daily' | 'weekly' | 'monthly' | 'yearly'). Both NULL = a goal with no
-- deadline (just track progress, as before). The per-period suggestion and the
-- "behind pace" check are computed in Rust (finanzas-core::goals), never stored.
-- See docs/DATA_MODEL.md.

ALTER TABLE savings_goals ADD COLUMN target_date TEXT;            -- 'YYYY-MM-DD' or NULL
ALTER TABLE savings_goals ADD COLUMN contribution_cadence TEXT;   -- daily|weekly|monthly|yearly or NULL
