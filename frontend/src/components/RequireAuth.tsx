import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import type { Role } from '@/types/api';

/**
 * Route-level gating. This is a usability guard, not a security boundary - the
 * backend enforces authorization on every request, and this only avoids showing
 * a screen that would come back 403.
 */
export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { isAuthenticated, hasRole } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (roles && !hasRole(...roles)) {
    return <Navigate to="/doctors" replace />;
  }
  return <Outlet />;
}
