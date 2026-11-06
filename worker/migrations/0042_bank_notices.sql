-- Migration number: 0042 	 2026-11-06
-- Movements read from bank notices (FIN-5): a Gmail connection per user, the
-- senders they treat as banks, card ending → wallet, and the inbox of notices
-- waiting to be confirmed or dismissed.

-- One Gmail mailbox per user. Tokens are AES-GCM encrypted with the worker
-- secret GMAIL_TOKEN_KEY (base64 of iv || ciphertext); D1 never holds them in
-- clear. history_id = Gmail's cursor: each push reads what came after it.
-- Times are unix seconds. needs_reconnect = 1 once Google refuses the refresh
-- token (the user revoked access): pushes are ignored until they reconnect.
CREATE TABLE gmail_connections (
  user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  refresh_token_enc TEXT NOT NULL,
  access_token_enc TEXT,
  access_expires_at INTEGER,
  history_id TEXT,
  watch_expires_at INTEGER,
  last_push_at TEXT,
  needs_reconnect INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_gmail_connections_email ON gmail_connections(email);

-- Senders the user adds as banks, beyond the built-in ones (BBVA, Nu).
-- pattern = full address (alertas@banco.com) or domain (banco.com, subdomains
-- included). wallet_id = default wallet for that sender's notices.
CREATE TABLE bank_senders (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pattern TEXT NOT NULL,
  wallet_id INTEGER REFERENCES wallets(id) ON DELETE SET NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (user_id, pattern)
);

-- Card/account ending (4 digits) → the wallet its notices land in.
CREATE TABLE card_wallets (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  card_last4 TEXT NOT NULL,
  wallet_id INTEGER NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, card_last4)
);

-- A notice read into a movement, waiting for the user. source = 'gmail'
-- (source_id = Gmail message id) or 'paste' (source_id = SHA-256 of the
-- pasted text); UNIQUE makes a re-delivered notice a no-op. Rows stay after
-- they're resolved so a re-delivery of a dismissed notice doesn't come back.
-- Suggested wallet/category are computed when listing, so a new card mapping
-- applies to what is already waiting.
CREATE TABLE pending_transactions (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source TEXT NOT NULL CHECK (source IN ('gmail', 'paste')),
  source_id TEXT NOT NULL,
  sender TEXT NOT NULL,
  subject TEXT,
  bank TEXT,
  kind TEXT NOT NULL CHECK (kind IN ('expense', 'income')),
  amount_cents INTEGER NOT NULL,
  currency_code TEXT NOT NULL,
  merchant TEXT,
  card_last4 TEXT,
  occurred_at TEXT NOT NULL,
  occurred_time TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'dismissed')),
  transaction_id INTEGER REFERENCES transactions(id) ON DELETE SET NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  resolved_at TEXT,
  UNIQUE (user_id, source, source_id)
);
CREATE INDEX idx_pending_transactions_user ON pending_transactions(user_id, status);
