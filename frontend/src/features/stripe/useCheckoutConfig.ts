import { useQuery } from '@tanstack/react-query';
import { getCheckoutConfig } from './api';

export const useCheckoutConfig = () =>
  useQuery({ queryKey: ['stripe-checkout-config'], queryFn: getCheckoutConfig, staleTime: Infinity });
