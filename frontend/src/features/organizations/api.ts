import api from '../../api/client';
import type { Organization } from '../../types/booking';

export interface OrganizationRequest {
  name: string;
  timezone: string;
}

export const listOrganizations = async (): Promise<Organization[]> => {
  const { data } = await api.get<Organization[]>('/organizations');
  return data;
};

export const createOrganization = async (request: OrganizationRequest): Promise<Organization> => {
  const { data } = await api.post<Organization>('/organizations', request);
  return data;
};
