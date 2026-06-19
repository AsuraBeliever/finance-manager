-- Migration number: 0013 	 2026-06-19
-- A contribution/withdrawal can now move real money: when the user picks a
-- source/destination wallet, the deposit posts an 'expense' (money leaves the
-- wallet into the investment) and a withdrawal posts an 'income' (money returns)
-- on that wallet, converted to the wallet's currency. The movement remembers the
-- transaction it created so deleting one removes the other and balances never
-- desync. ON DELETE CASCADE handles the reverse direction: deleting the wallet
-- transaction directly also drops its movement. NULL = external/first-time move
-- with no wallet side (the prior behaviour). See docs/DATA_MODEL.md.
ALTER TABLE investment_movements
  ADD COLUMN linked_transaction_id INTEGER REFERENCES transactions(id) ON DELETE CASCADE;
