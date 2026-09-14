export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export interface Booking {
  id: string;
  serviceId: string;
  providerId: string;
  customerId: string;
  startTime: string;
  endTime: string;
  status: BookingStatus;
}

export interface Service {
  id: string;
  name: string;
  durationMinutes: number;
  price: number;
}

export interface Organization {
  id: string;
  providerId: string;
  name: string;
  timezone: string;
}
