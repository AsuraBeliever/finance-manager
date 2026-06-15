-- Migration number: 0006 	 2026-06-13
-- Card "skin" for wallets: a visual style for the wallet rendered as a card.
-- Stored as a catalog id ("oro", "azul"…), a custom gradient ("grad:..."),
-- or an imported image ("img:<data-url>"). NULL falls back to a default by
-- category. Keep docs/DATA_MODEL.md in sync.
ALTER TABLE wallets ADD COLUMN skin TEXT;
