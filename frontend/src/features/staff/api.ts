import api from '../../api/client';
import type { Staff } from '../../types/booking';

export interface StaffRequest {
  name: string;
  email?: string;
}

export const listStaff = async (organizationId: string): Promise<Staff[]> => {
  const { data } = await api.get<Staff[]>(`/organizations/${organizationId}/staff`);
  return data;
};

export const createStaff = async (organizationId: string, request: StaffRequest): Promise<Staff> => {
  const { data } = await api.post<Staff>(`/organizations/${organizationId}/staff`, request);
  return data;
};
