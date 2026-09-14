import api from '../../api/client';
import type { Service } from '../../types/booking';

export const getServices = async (): Promise<Service[]> => {
  const { data } = await api.get<Service[]>('/services');
  return data;
};
