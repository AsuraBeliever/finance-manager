-- Migration number: 0023 	 2026-06-30
-- Reserved transaction category for goals. When you "use" (spend) a goal, the
-- real expense is filed under this category instead of carrying a hardcoded
-- "Meta:" prefix in its description (which broke the bilingual UI). is_reserved
-- = 1 keeps it out of the category pickers and the manage screen, so it can
-- never be chosen by hand — it only ever comes from goals. Its display name is
-- localized in src/i18n/seed.ts (Metas → Goals). See docs/DATA_MODEL.md.

ALTER TABLE transaction_categories ADD COLUMN is_reserved INTEGER NOT NULL DEFAULT 0;

INSERT INTO transaction_categories (user_id, name, kind, icon, is_system, is_reserved)
VALUES (NULL, 'Metas', 'expense', 'piggy-bank', 1, 1);
