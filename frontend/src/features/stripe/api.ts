import api from '../../api/client';
import type { StripeCheckoutConfig } from '../../types/booking';

export const getCheckoutConfig = async (): Promise<StripeCheckoutConfig> => {
  const { data } = await api.get<StripeCheckoutConfig>('/stripe/checkout-config');
  return data;
};
