-- Migration number: 0025 	 2026-06-30
-- Goals now declare their purpose so "completing" them does the right thing:
--   'purchase' — saving up to BUY something. Completing posts a real expense
--                from the wallet (money leaves). This is the prior behavior.
--   'fund'     — saving up a fund (emergency, trip). The money stays yours; you
--                spend it down over time, or graduate it into its own wallet.
-- Every goal is now backed by a wallet (the abstract "track only" mode is gone
-- from the UI); existing track-only goals keep working but can't be created
-- anymore. Existing goals default to 'purchase' to preserve their behavior.
-- See docs/DATA_MODEL.md.

ALTER TABLE savings_goals ADD COLUMN goal_kind TEXT NOT NULL DEFAULT 'purchase';  -- 'purchase' | 'fund'
