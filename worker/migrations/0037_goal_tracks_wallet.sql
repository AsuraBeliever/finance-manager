-- Migration number: 0037 	 2026-09-28
-- A goal can now BE a whole wallet instead of an apartado inside it.
--
-- tracks_wallet = 0 (default, every existing goal): apartado — reserves
--   saved_cents out of the wallet's balance, moved by contribute_savings_goal.
-- tracks_wallet = 1: the goal's progress IS the linked wallet's full balance
--   (initial + Σ transactions, computed on read, floored at 0). Nothing is
--   earmarked, so it never counts toward wallets.reserved_cents; you advance it
--   by moving money into the wallet, not by contributing. saved_cents stays 0.
ALTER TABLE savings_goals ADD COLUMN tracks_wallet INTEGER NOT NULL DEFAULT 0;
