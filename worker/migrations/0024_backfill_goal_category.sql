-- Migration number: 0024 	 2026-06-30
-- Backfill goal expenses created before 0023, which were stored with a hardcoded
-- "Meta: " prefix in their description and no category. Move them into the
-- reserved Metas category and strip the prefix, so old history matches the new
-- behavior (localized category instead of a Spanish prefix). 'Meta: ' is 6 chars
-- → keep from char 7 on.

UPDATE transactions
SET category_id = (SELECT id FROM transaction_categories WHERE is_reserved = 1 LIMIT 1),
    description = substr(description, 7)
WHERE kind = 'expense'
  AND category_id IS NULL
  AND description LIKE 'Meta: %';
