-- Multi-tenant booking domain. The original V1 salon tables remain untouched so
-- existing installations can migrate data deliberately rather than losing it.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'PROVIDER'))
);

CREATE TABLE providers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    business_name VARCHAR(255)
);

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    display_name VARCHAR(255),
    phone_number VARCHAR(50)
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_id UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    CONSTRAINT uq_organization_provider_name UNIQUE (provider_id, name)
);

CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    CONSTRAINT uq_service_organization_name UNIQUE (organization_id, name)
);

CREATE TABLE staff (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    CONSTRAINT uq_staff_organization_email UNIQUE (organization_id, email)
);

CREATE TABLE availability (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    staff_id UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT chk_availability_day CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT chk_availability_time CHECK (end_time > start_time),
    CONSTRAINT uq_availability_staff_day_time UNIQUE (staff_id, day_of_week, start_time, end_time)
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    staff_id UUID NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE RESTRICT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_time CHECK (end_time > start_time),
    CONSTRAINT chk_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'))
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_organizations_provider ON organizations(provider_id);
CREATE INDEX idx_services_organization ON services(organization_id);
CREATE INDEX idx_staff_organization ON staff(organization_id);
CREATE INDEX idx_availability_staff_day ON availability(staff_id, day_of_week);
CREATE INDEX idx_bookings_organization_start ON bookings(organization_id, start_time);
CREATE INDEX idx_bookings_staff_start ON bookings(staff_id, start_time);
CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_reviews_organization ON reviews(organization_id);
