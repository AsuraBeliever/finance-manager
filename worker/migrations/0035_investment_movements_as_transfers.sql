-- Investment deposits/withdrawals move money between the user's own places
-- (wallet <-> investment): they are transfers, not income/expense. Counting them
-- as income/expense inflated both totals every time the same money went back and
-- forth. Reclassify every wallet transaction linked to an investment movement:
--   deposit    -> 'transfer_out' (money leaves the wallet into the investment)
--   withdrawal -> 'transfer_in'  (money returns to the wallet)
-- and drop the 'Inversiones' category (transfers carry none; the description
-- already names the investment). transfer_group_id stays NULL: the investment
-- isn't a wallet, so this leg has no sibling — that NULL is what marks it as an
-- investment leg. Idempotent: rows already reclassified match nothing.
UPDATE transactions
SET kind = (
      SELECT CASE m.kind WHEN 'deposit' THEN 'transfer_out' ELSE 'transfer_in' END
      FROM investment_movements m WHERE m.linked_transaction_id = transactions.id
    ),
    category_id = NULL
WHERE kind IN ('income', 'expense')
  AND EXISTS (
    SELECT 1 FROM investment_movements m WHERE m.linked_transaction_id = transactions.id
  );
