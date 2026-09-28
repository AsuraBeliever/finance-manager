-- Migration number: 0038 	 2026-09-28
-- A goal's plan can be a fixed contribution instead of a deadline.
--
-- contribution_cents NULL (default, every existing goal): the plan, if any,
--   is target_date + contribution_cadence → "set aside X per period".
-- contribution_cents > 0: the user puts in this much every
--   contribution_cadence period; target_date stays NULL and the date the goal
--   would be reached is projected on read (finanzas_core::goals::
--   plan_fixed_contribution). Never stored.
ALTER TABLE savings_goals ADD COLUMN contribution_cents INTEGER;
