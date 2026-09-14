export interface Session {
  accessToken: string;
  role: 'CUSTOMER' | 'PROVIDER';
}

export const saveSession = (session: Session): void => {
  localStorage.setItem('accessToken', session.accessToken);
  localStorage.setItem('role', session.role);
};

export const clearSession = (): void => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('role');
  localStorage.removeItem('organizationId');
};
