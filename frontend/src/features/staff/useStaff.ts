import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createStaff, listStaff, type StaffRequest } from './api';

export const staffQueryKey = (organizationId: string | null) => ['staff', organizationId] as const;

export const useStaff = (organizationId: string | null) =>
  useQuery({
    queryKey: staffQueryKey(organizationId),
    queryFn: () => listStaff(organizationId as string),
    enabled: Boolean(organizationId),
  });

export const useCreateStaff = (organizationId: string | null) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: StaffRequest) => createStaff(organizationId as string, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffQueryKey(organizationId) }),
  });
};
