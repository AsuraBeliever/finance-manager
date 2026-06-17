-- Brute-force throttle for the unauthenticated auth endpoints (login, register).
-- Fixed-window counter: one row per (scope, client key = CF-Connecting-IP). The
-- upsert that opens or increments a window is a single atomic statement (D1 has
-- no BEGIN/COMMIT). Stale rows are pruned by the daily cron in lib.rs.
CREATE TABLE auth_attempts (
  scope        TEXT    NOT NULL, -- 'login' | 'register'
  client_key   TEXT    NOT NULL, -- CF-Connecting-IP, or 'unknown' when absent
  count        INTEGER NOT NULL,
  window_start TEXT    NOT NULL, -- datetime('now') when the current window opened
  PRIMARY KEY (scope, client_key)
);
