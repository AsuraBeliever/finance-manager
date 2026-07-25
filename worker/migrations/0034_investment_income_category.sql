-- Migration number: 0034 	 2026-07-24
-- Income sibling of the 'Inversiones' category added in 0033. Withdrawing from
-- an investment posts an income on the destination wallet; like the deposit
-- (expense) leg it was landing uncategorized. Same visible, non-reserved seed
-- (is_reserved = 0) so it shows in the pickers and manager. Its display name is
-- localized in src/i18n/seed.ts (Inversiones → Investments). See docs/DATA_MODEL.md.

INSERT INTO transaction_categories (user_id, name, kind, icon, color, is_system, is_reserved)
VALUES (NULL, 'Inversiones', 'income', 'trending-up', '#10b981', 1, 0);
