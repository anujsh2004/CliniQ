import { Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

/**
 * Where each role starts.
 *
 * <p>Everyone used to land on the patient-facing doctor browser, which is the
 * wrong first screen for a doctor: their day is the schedule, not a list of
 * colleagues. An admin still starts at the doctor list, because that list is
 * their roster.
 */
export function LandingRedirect() {
  const { user } = useAuth();

  if (user?.role === 'DOCTOR') {
    return <Navigate to="/schedule" replace />;
  }
  return <Navigate to="/doctors" replace />;
}
