import api from '../../api/client';
import type { Booking, Hold } from '../../types/booking';

export interface HoldRequest {
  organizationId: string;
  staffId: string;
  serviceId: string;
  startTime: string;
}

export interface BookingRequest {
  customerId: string;
  staffId: string;
  serviceId: string;
  startTime: string;
  holdToken: string;
}

export const createHold = async (request: HoldRequest): Promise<Hold> => {
  const { data } = await api.post<Hold>('/booking-holds', request);
  return data;
};

export const releaseHold = async (holdToken: string): Promise<void> => {
  await api.delete(`/booking-holds/${holdToken}`);
};

export const listBookings = async (organizationId: string): Promise<Booking[]> => {
  const { data } = await api.get<Booking[]>(`/organizations/${organizationId}/bookings`);
  return data;
};

export const createBooking = async (organizationId: string, request: BookingRequest): Promise<Booking> => {
  const { data } = await api.post<Booking>(`/organizations/${organizationId}/bookings`, request);
  return data;
};
