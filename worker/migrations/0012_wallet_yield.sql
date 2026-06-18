-- Migration number: 0012 	 2026-06-17
-- Yield-bearing wallets: a plain wallet (not an investment) whose balance grows
-- on its own, mirroring debit accounts like Klar/Nu that pay daily-compounded
-- interest with a periodic payout. The daily cron posts one 'income' (Intereses)
-- transaction per period; balance stays computed (initial + Σ transactions).
-- See docs/DATA_MODEL.md.

-- NULL yield_rate_bps = no yield (the default). Rate in basis points.
ALTER TABLE wallets ADD COLUMN yield_rate_bps INTEGER;
-- 'weekly' | 'biweekly' | 'monthly' — how often interest is paid out.
ALTER TABLE wallets ADD COLUMN yield_frequency TEXT;
-- 'YYYY-MM-DD' the day yield was switched on; accrual never runs before it.
ALTER TABLE wallets ADD COLUMN yield_anchor_date TEXT;
-- 'YYYY-MM-DD' end of the last period already paid; the cron advances it.
ALTER TABLE wallets ADD COLUMN yield_last_paid_date TEXT;
