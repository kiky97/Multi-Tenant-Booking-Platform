-- Replaces the single-owner Provider->Organization relationship with a proper
-- membership model: any number of users can belong to an organization, each with
-- their own OWNER/ADMIN/STAFF role. No production data exists yet, so this migration
-- drops the old structures outright rather than migrating rows.

ALTER TABLE organizations DROP CONSTRAINT organizations_provider_id_fkey;
ALTER TABLE organizations DROP CONSTRAINT uq_organization_provider_name;
ALTER TABLE organizations DROP COLUMN provider_id;

DROP TABLE providers;

ALTER TABLE staff ADD COLUMN user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE memberships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'STAFF')),
    CONSTRAINT uq_membership_user_org UNIQUE (user_id, organization_id)
);

CREATE INDEX idx_memberships_organization ON memberships(organization_id);
CREATE INDEX idx_memberships_user ON memberships(user_id);
