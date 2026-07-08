-- Migration number: 0032 	 2026-07-08
-- Freeze the wall-clock time of every pre-existing movement. Legacy rows had a
-- NULL occurred_time and were only ever shown via created_at converted to the
-- viewer's timezone — so if a user later switched timezones, those times would
-- drift. Every account is currently America/Mexico_City, which has observed no
-- DST since 2022 and all data postdates that, so the zone is a fixed UTC-6:
-- persist created_at shifted -6h as the local 'HH:MM'. This yields exactly the
-- time already displayed, now stored, so a future timezone change won't move it.
-- Only untimed rows are touched; times a user explicitly set are left intact.

UPDATE transactions
SET occurred_time = strftime('%H:%M', datetime(created_at, '-6 hours'))
WHERE occurred_time IS NULL AND created_at IS NOT NULL;
