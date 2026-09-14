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

export const getSession = (): Session | null => {
  const accessToken = localStorage.getItem('accessToken');
  const role = localStorage.getItem('role') as Session['role'] | null;
  if (!accessToken || !role) return null;
  return { accessToken, role };
};
