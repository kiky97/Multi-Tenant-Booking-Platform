-- payments: append-only ledger of applied Stripe events, kept separate from bookings.status
-- (the current state) so payment history survives retries/multiple webhook deliveries.
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    stripe_payment_intent_id VARCHAR(255) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_status CHECK (status IN ('SUCCEEDED', 'FAILED', 'CANCELED'))
);
CREATE INDEX idx_payments_booking ON payments(booking_id);

-- audit_logs: who changed access/organization state and when. Only meaningful now that
-- memberships carry OWNER/ADMIN/STAFF roles instead of a single implicit owner.
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID,
    detail VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_log_action CHECK (action IN ('MEMBERSHIP_INVITED', 'MEMBERSHIP_REMOVED', 'ORGANIZATION_CREATED'))
);
CREATE INDEX idx_audit_logs_organization ON audit_logs(organization_id, created_at DESC);
