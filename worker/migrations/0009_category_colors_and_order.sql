-- Migration number: 0009 	 2026-06-15
-- Categories: a distinct default color for every category, plus a per-user
-- display order (drag-to-reorder in the manager). Keep docs/DATA_MODEL.md and
-- src/lib/palette.ts (CATEGORY_PALETTE) in sync.

-- 1) Distinct default colors for the shared seed categories (user_id IS NULL).
UPDATE transaction_categories SET color = '#34d399' WHERE user_id IS NULL AND kind = 'income'  AND name = 'Salario';
UPDATE transaction_categories SET color = '#f472b6' WHERE user_id IS NULL AND kind = 'income'  AND name = 'Regalo';
UPDATE transaction_categories SET color = '#fbbf24' WHERE user_id IS NULL AND kind = 'income'  AND name = 'Intereses';
UPDATE transaction_categories SET color = '#38bdf8' WHERE user_id IS NULL AND kind = 'income'  AND name = 'Otro ingreso';
UPDATE transaction_categories SET color = '#f97316' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Comida';
UPDATE transaction_categories SET color = '#a855f7' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Transporte';
UPDATE transaction_categories SET color = '#22d3ee' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Hogar';
UPDATE transaction_categories SET color = '#ec4899' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Entretenimiento';
UPDATE transaction_categories SET color = '#ef4444' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Salud';
UPDATE transaction_categories SET color = '#8b5cf6' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Suscripciones';
UPDATE transaction_categories SET color = '#94a3b8' WHERE user_id IS NULL AND kind = 'expense' AND name = 'Otro gasto';

-- 2) Backfill any remaining uncolored category (e.g. user-made ones) with a
--    palette color derived from its id, so nothing stays without a color.
UPDATE transaction_categories
SET color = (CASE id % 16
  WHEN 0 THEN '#34d399' WHEN 1 THEN '#f472b6' WHEN 2 THEN '#fbbf24' WHEN 3 THEN '#38bdf8'
  WHEN 4 THEN '#f97316' WHEN 5 THEN '#a855f7' WHEN 6 THEN '#22d3ee' WHEN 7 THEN '#ec4899'
  WHEN 8 THEN '#ef4444' WHEN 9 THEN '#8b5cf6' WHEN 10 THEN '#94a3b8' WHEN 11 THEN '#10b981'
  WHEN 12 THEN '#eab308' WHEN 13 THEN '#3b82f6' WHEN 14 THEN '#fb7185' ELSE '#14b8a6' END)
WHERE color IS NULL;

-- 3) Per-user display order. A row per (user, category) the user has arranged;
--    unset categories fall back to a stable default order. Covers seeds and the
--    user's own categories without affecting other users.
CREATE TABLE category_order (
  user_id INTEGER NOT NULL REFERENCES users(id),
  category_id INTEGER NOT NULL REFERENCES transaction_categories(id),
  position INTEGER NOT NULL,
  PRIMARY KEY (user_id, category_id)
);
