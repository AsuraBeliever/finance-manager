-- Migration number: 0008 	 2026-06-15
-- Per-user customization of transaction categories. Users can add/rename/delete
-- their OWN categories (user_id = uid); the shared seeds (user_id NULL) can't be
-- deleted globally, so a user "removes" a seed by hiding it just for themselves.
-- Existing transactions keep their category_id either way (hidden seeds still
-- exist; deleting an own category reassigns its transactions to NULL first).
-- Keep docs/DATA_MODEL.md in sync.
CREATE TABLE hidden_categories (
  user_id INTEGER NOT NULL REFERENCES users(id),
  category_id INTEGER NOT NULL REFERENCES transaction_categories(id),
  PRIMARY KEY (user_id, category_id)
);
