-- Migration number: 0004 	 2026-06-13
-- Sign in with Google: link a Google account id (the OIDC 'sub' claim) to a
-- user. password_hash stays NOT NULL — Google-only users get the '!' sentinel
-- (verify_password returns false for non-PHC strings, like the system user).

ALTER TABLE users ADD COLUMN google_sub TEXT;
CREATE UNIQUE INDEX idx_users_google_sub ON users(google_sub) WHERE google_sub IS NOT NULL;
