-- Stripe payment tracking. amount is a price snapshot taken at booking time (so a later
-- service price change can't shift what the webhook considers a valid payment), and
-- stripe_payment_intent_id is how the webhook finds the booking to update.

ALTER TABLE bookings ADD COLUMN amount NUMERIC(10, 2);
ALTER TABLE bookings ADD COLUMN stripe_payment_intent_id VARCHAR(255) UNIQUE;
