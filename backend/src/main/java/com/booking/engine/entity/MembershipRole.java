package com.booking.engine.entity;

/** Per-organization permission level. Lower ordinal means more privileged. */
public enum MembershipRole {
    OWNER,
    ADMIN,
    STAFF;

    /** True if this role has at least the privilege of {@code minimum} (i.e. is that role or higher). */
    public boolean satisfies(MembershipRole minimum) {
        return this.ordinal() <= minimum.ordinal();
    }
}
