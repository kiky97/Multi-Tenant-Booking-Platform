import { useQuery } from '@tanstack/react-query';
import { getServices } from './api';

export const servicesQueryKey = (organizationId: string | null) => ['services', organizationId] as const;

export const useServices = (organizationId: string | null) =>
  useQuery({ queryKey: servicesQueryKey(organizationId), queryFn: getServices, enabled: Boolean(organizationId) });
