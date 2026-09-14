-- PENDING never meant anything different from "slot held, payment not yet confirmed" in this
-- app (a booking row is only ever created after a Redis hold is consumed) — renaming it to HELD
-- says that plainly instead of leaving CONFIRMED as the only status whose name matches its meaning.

ALTER TABLE bookings DROP CONSTRAINT chk_booking_status;
UPDATE bookings SET status = 'HELD' WHERE status = 'PENDING';
ALTER TABLE bookings ALTER COLUMN status SET DEFAULT 'HELD';
ALTER TABLE bookings ADD CONSTRAINT chk_booking_status
    CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));
