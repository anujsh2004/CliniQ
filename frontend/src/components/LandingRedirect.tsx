import { Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { LandingPage } from '@/pages/LandingPage';

/**
 * Where each role starts.
 *
 * <p>Everyone used to land on the patient-facing doctor browser, which is the
 * wrong first screen for a doctor: their day is the schedule, not a list of
 * colleagues. An admin still starts at the doctor list, because that list is
 * their roster.
 *
 * <p>A visitor who is not signed in gets the public page rather than being
 * bounced to a login form: they may not have an account yet, and a form with
 * no explanation of what it is for is a poor first impression.
 */
export function LandingRedirect() {
  const { user } = useAuth();

  if (!user) {
    return <LandingPage />;
  }

  if (user.role === 'DOCTOR') {
    return <Navigate to="/schedule" replace />;
  }
  return <Navigate to="/doctors" replace />;
}
