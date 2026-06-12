-- Migration number: 0001 	 2026-06-11
-- Schema for the multi-user cloud backend. Squash of desktop migrations 1-5
-- (src-tauri/src/db/mod.rs) plus users/sessions and per-user scoping.
-- Keep docs/DATA_MODEL.md in sync.

-- ---- auth ----

CREATE TABLE users (
  id INTEGER PRIMARY KEY,
  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
  -- PHC-style: pbkdf2-sha256$<iterations>$<salt_b64>$<hash_b64>
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE sessions (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- SHA-256 hex of the cookie token: a DB leak can't impersonate sessions
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  expires_at TEXT NOT NULL
);
CREATE INDEX idx_sessions_expiry ON sessions(expires_at);

-- ---- global reference data (shared by all users) ----

CREATE TABLE currencies (
  code TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  symbol TEXT NOT NULL,
  decimals INTEGER NOT NULL DEFAULT 2
);

CREATE TABLE exchange_rates (
  id INTEGER PRIMARY KEY,
  currency_code TEXT NOT NULL REFERENCES currencies(code),
  rate_to_mxn_micros INTEGER NOT NULL,
  as_of TEXT NOT NULL DEFAULT (datetime('now')),
  source TEXT NOT NULL DEFAULT 'manual'
);

CREATE TABLE wallet_categories (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT,
  is_system INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE rate_history (
  series TEXT NOT NULL,               -- banxico kind: 'objetivo', 'cetes_28', ...
  date TEXT NOT NULL,                 -- 'YYYY-MM-DD' the rate took effect
  rate_bps INTEGER NOT NULL,
  PRIMARY KEY (series, date)
);

CREATE TABLE crypto_prices (
  symbol TEXT PRIMARY KEY,            -- 'BTC', 'ETH', ...
  price_mxn_cents INTEGER NOT NULL,
  price_usd_cents INTEGER,
  as_of TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ---- per-user data ----

CREATE TABLE wallets (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  category_id INTEGER NOT NULL REFERENCES wallet_categories(id),
  currency_code TEXT NOT NULL REFERENCES currencies(code),
  initial_balance_cents INTEGER NOT NULL DEFAULT 0,
  color TEXT,
  notes TEXT,
  is_archived INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_wallets_user ON wallets(user_id);

-- user_id NULL = system seed rows, visible to everyone; user rows are scoped.
CREATE TABLE transaction_categories (
  id INTEGER PRIMARY KEY,
  user_id INTEGER REFERENCES users(id),
  name TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('income','expense')),
  icon TEXT,
  color TEXT,
  is_system INTEGER NOT NULL DEFAULT 0
);

-- scoped through wallets.user_id (every query joins wallets)
CREATE TABLE transactions (
  id INTEGER PRIMARY KEY,
  wallet_id INTEGER NOT NULL REFERENCES wallets(id),
  kind TEXT NOT NULL CHECK (kind IN ('income','expense','transfer_in','transfer_out')),
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  category_id INTEGER REFERENCES transaction_categories(id),
  transfer_group_id TEXT,
  description TEXT,
  occurred_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_tx_wallet ON transactions(wallet_id, occurred_at);
CREATE INDEX idx_tx_transfer ON transactions(transfer_group_id);

CREATE TABLE investments (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  calculator TEXT NOT NULL,
  currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
  principal_cents INTEGER NOT NULL,
  start_date TEXT NOT NULL,
  params_json TEXT NOT NULL DEFAULT '{}',
  linked_wallet_id INTEGER REFERENCES wallets(id),
  is_closed INTEGER NOT NULL DEFAULT 0,
  notes TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_inv_user ON investments(user_id);

-- scoped through investments.user_id
CREATE TABLE investment_snapshots (
  id INTEGER PRIMARY KEY,
  investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  value_cents INTEGER NOT NULL,
  as_of TEXT NOT NULL,
  source TEXT NOT NULL DEFAULT 'manual'
);

-- scoped through investments.user_id
CREATE TABLE investment_movements (
  id INTEGER PRIMARY KEY,
  investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('deposit','withdrawal')),
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  occurred_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_inv_mov ON investment_movements(investment_id, occurred_at);

CREATE TABLE settings (
  user_id INTEGER NOT NULL REFERENCES users(id),
  key TEXT NOT NULL,
  value TEXT NOT NULL,
  PRIMARY KEY (user_id, key)
);

-- global market-data cache entries (e.g. 'bonddia_price') live here with a
-- reserved user_id 0 — no FK row needed because SQLite only enforces the FK
-- on insert; we create a sentinel user instead to keep integrity honest.
INSERT INTO users (id, email, password_hash)
VALUES (0, 'system@finanzas.local', '!');
