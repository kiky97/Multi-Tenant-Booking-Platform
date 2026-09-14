import { Navigate, Outlet } from 'react-router-dom';
import { getSession } from '../../features/auth/session';

export default function RequireAuth() {
  const session = getSession();
  if (!session) return <Navigate to="/console/login" replace />;
  return <Outlet />;
}
