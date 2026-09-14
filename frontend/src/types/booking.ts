// Mirrors the record shapes returned by PlatformController / MembershipController /
// BookingHoldController / StripeController (backend/src/main/java/com/booking/engine/platform).

export type BookingStatus = 'HELD' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';
export type MembershipRole = 'OWNER' | 'ADMIN' | 'STAFF';
export type UserRole = 'CUSTOMER' | 'PROVIDER';
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface Organization {
  id: string;
  name: string;
  timezone: string;
}

export interface Service {
  id: string;
  name: string;
  durationMinutes: number;
  price: number;
}

export interface Staff {
  id: string;
  name: string;
  email: string | null;
}

export interface Availability {
  id: string;
  staffId: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}

export interface Booking {
  id: string;
  customerId: string;
  staffId: string;
  serviceId: string;
  startTime: string;
  endTime: string;
  status: BookingStatus;
  clientSecret: string | null;
}

export interface BusinessAccount {
  userId: string;
  email: string;
}

export interface Customer {
  id: string;
  displayName: string | null;
  phoneNumber: string | null;
}

export interface Hold {
  holdToken: string;
  startTime: string;
  endTime: string;
  expiresAt: string;
}

export interface StripeCheckoutConfig {
  currency: string;
  stripePublishableKey: string;
}
