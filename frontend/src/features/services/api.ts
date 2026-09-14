import api from '../../api/client';
import type { Service } from '../../types/booking';

export interface ServiceRequest {
  name: string;
  durationMinutes: number;
  price: number;
}

export const getServices = async (): Promise<Service[]> => {
  const { data } = await api.get<Service[]>('/services');
  return data;
};

export const createService = async (organizationId: string, request: ServiceRequest): Promise<Service> => {
  const { data } = await api.post<Service>(`/organizations/${organizationId}/services`, request);
  return data;
};
