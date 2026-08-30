import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/AppShell';
import { RequireAuth } from '@/components/RequireAuth';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { AppointmentsPage } from '@/pages/AppointmentsPage';
import { DoctorDetailPage } from '@/pages/DoctorDetailPage';
import { DoctorsPage } from '@/pages/DoctorsPage';
import { ProfilePage } from '@/pages/ProfilePage';
import { AvailabilityPage } from '@/pages/AvailabilityPage';
import { SchedulePage } from '@/pages/SchedulePage';

/**
 * Every route in product-description.md section 6 renders its correct layout
 * shell. Screens arrive branch by branch behind these routes.
 */
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/doctors" element={<DoctorsPage />} />
          <Route path="/doctors/:doctorId" element={<DoctorDetailPage />} />
        </Route>
      </Route>

      <Route element={<RequireAuth roles={['PATIENT']} />}>
        <Route element={<AppShell />}>
          <Route path="/appointments" element={<AppointmentsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route element={<RequireAuth roles={['DOCTOR']} />}>
        <Route element={<AppShell />}>
          <Route path="/schedule" element={<SchedulePage />} />
        </Route>
      </Route>

      <Route element={<RequireAuth roles={['DOCTOR', 'ADMIN']} />}>
        <Route element={<AppShell />}>
          <Route path="/availability" element={<AvailabilityPage />} />
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/doctors" replace />} />
      <Route path="*" element={<Navigate to="/doctors" replace />} />
    </Routes>
  );
}
