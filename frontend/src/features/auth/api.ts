import api from '../../api/client';
import type { BusinessAccount, Customer, UserRole } from '../../types/booking';

export interface AccountRequest {
  email: string;
  password: string;
  displayName: string;
  phoneNumber?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  role: UserRole;
}

export const login = async (request: LoginRequest): Promise<LoginResponse> => {
  const { data } = await api.post<LoginResponse>('/auth/login', request);
  return data;
};

export const registerProvider = async (request: AccountRequest): Promise<BusinessAccount> => {
  const { data } = await api.post<BusinessAccount>('/providers', request);
  return data;
};

export const registerCustomer = async (request: AccountRequest): Promise<Customer> => {
  const { data } = await api.post<Customer>('/customers', request);
  return data;
};
