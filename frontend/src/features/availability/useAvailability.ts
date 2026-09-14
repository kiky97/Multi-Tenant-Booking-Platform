import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createAvailability, listAvailability, type AvailabilityRequest } from './api';

export const availabilityQueryKey = (organizationId: string | null) => ['availability', organizationId] as const;

export const useAvailability = (organizationId: string | null) =>
  useQuery({
    queryKey: availabilityQueryKey(organizationId),
    queryFn: () => listAvailability(organizationId as string),
    enabled: Boolean(organizationId),
  });

export const useCreateAvailability = (organizationId: string | null) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AvailabilityRequest) => createAvailability(organizationId as string, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: availabilityQueryKey(organizationId) }),
  });
};
