-- Migration number: 0026 	 2026-07-01
-- A wallet can be an "apartado" (pocket) of another wallet: parent_wallet_id
-- points to the container. This is organizational — each wallet keeps its own
-- balance; the UI nests apartados under their parent so you can see, e.g., BBVA
-- with $10k plus its "Viaje a Japón" apartado with $40k. NULL = a standalone
-- wallet. When a fund goal graduates into a wallet it becomes an apartado of the
-- wallet it was saved in. See docs/DATA_MODEL.md.

ALTER TABLE wallets ADD COLUMN parent_wallet_id INTEGER REFERENCES wallets(id);
