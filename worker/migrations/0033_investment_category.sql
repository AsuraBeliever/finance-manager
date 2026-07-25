-- Migration number: 0033 	 2026-07-23
-- Default expense category for investment contributions ("aportes"). Adding
-- money to an investment posts an expense on the source wallet; before this it
-- landed uncategorized. Unlike the reserved 'Metas'/'Meses sin intereses'
-- categories, this one is a normal visible seed (is_reserved = 0): it shows in
-- the pickers and the manager like the other defaults, so the user can also
-- file transactions under it by hand. Its display name is localized in
-- src/i18n/seed.ts (Inversiones → Investments). See docs/DATA_MODEL.md.

INSERT INTO transaction_categories (user_id, name, kind, icon, color, is_system, is_reserved)
VALUES (NULL, 'Inversiones', 'expense', 'trending-up', '#10b981', 1, 0);
