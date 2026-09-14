import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createOrganization, listOrganizations, type OrganizationRequest } from './api';

export const organizationsQueryKey = ['organizations'] as const;

export const useOrganizations = () =>
  useQuery({ queryKey: organizationsQueryKey, queryFn: listOrganizations });

export const useCreateOrganization = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: OrganizationRequest) => createOrganization(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationsQueryKey }),
  });
};
