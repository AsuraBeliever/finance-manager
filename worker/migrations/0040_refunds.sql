-- Migration number: 0040 	 2026-10-14
-- Refunds: money that comes back for an expense. It is NOT income.
--
-- A refund is stored as kind = 'income' (it adds to the wallet balance, so
-- every balance formula stays untouched) with is_refund = 1, and the API
-- exposes it as kind 'refund'. Its category_id is an EXPENSE category: it nets
-- against that category's spending (analytics, budgets) in the month it lands,
-- and never counts as income.
--
-- refund_of_id optionally points at the expense being refunded (partial
-- refunds allowed, several per expense). Deleting the expense keeps the refund
-- and just unlinks it.
--
-- Why not a new kind 'refund' in the CHECK: changing a CHECK means rebuilding
-- `transactions`, and in D1 the DROP TABLE would fire investment_movements'
-- ON DELETE CASCADE (foreign keys can't be turned off in a migration).
ALTER TABLE transactions ADD COLUMN is_refund INTEGER NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN refund_of_id INTEGER REFERENCES transactions(id) ON DELETE SET NULL;
CREATE INDEX idx_tx_refund_of ON transactions(refund_of_id) WHERE refund_of_id IS NOT NULL;
