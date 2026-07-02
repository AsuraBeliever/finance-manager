-- Migration number: 0028 	 2026-07-02
-- MSI plans can carry a spending category: each posted installment files under
-- it, so installment purchases show up in budgets/analytics like any expense.
-- NULL falls back to the reserved 'Meses sin intereses' category (0027), which
-- keeps existing plans and uncategorized ones labeled. See docs/DATA_MODEL.md.

ALTER TABLE msi_plans ADD COLUMN category_id INTEGER REFERENCES transaction_categories(id);
