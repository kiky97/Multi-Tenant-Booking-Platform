import { useQuery, useQueryClient } from '@tanstack/react-query';
import { listBookings } from './api';

export const bookingsQueryKey = (organizationId: string | null) => ['bookings', organizationId] as const;

export const useBookings = (organizationId: string | null) =>
  useQuery({
    queryKey: bookingsQueryKey(organizationId),
    queryFn: () => listBookings(organizationId as string),
    enabled: Boolean(organizationId),
  });

export const useInvalidateBookings = (organizationId: string | null) => {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: bookingsQueryKey(organizationId) });
};
