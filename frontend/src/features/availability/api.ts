import api from '../../api/client';
import type { Availability, DayOfWeek } from '../../types/booking';

export interface AvailabilityRequest {
  staffId: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}

export const listAvailability = async (organizationId: string): Promise<Availability[]> => {
  const { data } = await api.get<Availability[]>(`/organizations/${organizationId}/availability`);
  return data;
};

export const createAvailability = async (organizationId: string, request: AvailabilityRequest): Promise<Availability> => {
  const { data } = await api.post<Availability>(`/organizations/${organizationId}/availability`, request);
  return data;
};
