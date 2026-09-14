import { Navigate, Route, Routes } from 'react-router-dom';
import BookingsPage from './BookingsPage';
import BookPage from './BookPage';
import ConsoleLayout from './ConsoleLayout';
import LoginPage from './LoginPage';
import OrganizationsPage from './OrganizationsPage';
import RegisterPage from './RegisterPage';
import RequireAuth from './RequireAuth';
import SetupPage from './SetupPage';

export default function ConsoleRoutes() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route path="register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<ConsoleLayout />}>
          <Route index element={<Navigate to="organizations" replace />} />
          <Route path="organizations" element={<OrganizationsPage />} />
          <Route path="setup" element={<SetupPage />} />
          <Route path="book" element={<BookPage />} />
          <Route path="bookings" element={<BookingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="login" replace />} />
    </Routes>
  );
}
