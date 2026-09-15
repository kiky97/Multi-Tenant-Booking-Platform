import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createService, listServices, type ServiceRequest } from './api';

export const servicesQueryKey = (organizationId: string | null) => ['services', organizationId] as const;

export const useServices = (organizationId: string | null) =>
  useQuery({
    queryKey: servicesQueryKey(organizationId),
    queryFn: () => listServices(organizationId as string),
    enabled: Boolean(organizationId),
  });

export const useCreateService = (organizationId: string | null) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ServiceRequest) => createService(organizationId as string, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: servicesQueryKey(organizationId) }),
  });
};
