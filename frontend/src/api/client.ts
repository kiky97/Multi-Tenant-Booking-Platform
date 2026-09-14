import axios from 'axios';

const api = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '/api/v1' });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  const organizationId = localStorage.getItem('organizationId');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  if (organizationId) config.headers['X-Organization-Id'] = organizationId;
  return config;
});

export default api;
