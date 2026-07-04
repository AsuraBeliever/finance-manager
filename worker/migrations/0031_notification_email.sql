-- Migration number: 0031 	 2026-07-04
-- Email channel for notifications. email_status: NULL = bell only,
-- 'pending' = the morning digest email still owes this alert, 'sent' = done.
-- The evaluators stamp 'pending' when the rule has its email channel on (and
-- the user's master email switch is on); the sender flips it to 'sent' after
-- the SMTP transaction succeeds, so a failed send retries on the next cron.

ALTER TABLE notifications ADD COLUMN email_status TEXT;

CREATE INDEX idx_notifications_email ON notifications(email_status)
  WHERE email_status = 'pending';
