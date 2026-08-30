import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/AppShell';
import { RequireAuth } from '@/components/RequireAuth';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { Placeholder } from '@/pages/Placeholder';

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
          <Route
            path="/doctors"
            element={<Placeholder title="Doctors" description="The doctor list lands next." />}
          />
          <Route
            path="/doctors/:doctorId"
            element={
              <Placeholder title="Doctor" description="Profile and slot picker land next." />
            }
          />
        </Route>
      </Route>

      <Route element={<RequireAuth roles={['PATIENT']} />}>
        <Route element={<AppShell />}>
          <Route
            path="/appointments"
            element={
              <Placeholder title="My appointments" description="Your bookings land next." />
            }
          />
          <Route
            path="/profile"
            element={<Placeholder title="Profile" description="Your details land next." />}
          />
        </Route>
      </Route>

      <Route element={<RequireAuth roles={['DOCTOR']} />}>
        <Route element={<AppShell />}>
          <Route
            path="/schedule"
            element={<Placeholder title="Schedule" description="Your day lands next." />}
          />
        </Route>
      </Route>

      <Route element={<RequireAuth roles={['DOCTOR', 'ADMIN']} />}>
        <Route element={<AppShell />}>
          <Route
            path="/availability"
            element={
              <Placeholder title="Availability" description="The weekly editor lands next." />
            }
          />
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/doctors" replace />} />
      <Route path="*" element={<Navigate to="/doctors" replace />} />
    </Routes>
  );
}
