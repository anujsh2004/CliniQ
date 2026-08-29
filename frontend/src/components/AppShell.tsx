import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Button } from './Button';
import type { Role } from '@/types/api';

interface NavItem {
  to: string;
  label: string;
  roles: Role[];
}

/**
 * design.md 2.1: left-anchored primary navigation for the authenticated staff
 * views. Patients get the lighter header instead, since they complete a task
 * rather than explore data (design.md 1.2).
 */
const NAV_ITEMS: NavItem[] = [
  { to: '/doctors', label: 'Doctors', roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
  { to: '/appointments', label: 'My appointments', roles: ['PATIENT'] },
  { to: '/schedule', label: 'Schedule', roles: ['DOCTOR'] },
  { to: '/availability', label: 'Availability', roles: ['DOCTOR', 'ADMIN'] },
  { to: '/profile', label: 'Profile', roles: ['PATIENT'] },
];

export function AppShell() {
  const { user, hasRole, logout } = useAuth();
  const navigate = useNavigate();

  const visibleItems = NAV_ITEMS.filter((item) => item.roles.some((role) => hasRole(role)));

  const handleSignOut = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex min-h-full flex-col md:flex-row">
      {/* Sidebar at desktop width; a horizontal bar below the header on mobile
          (design.md 3.8). */}
      <aside className="border-b border-border bg-surface md:w-60 md:border-b-0 md:border-r">
        <div className="flex items-center gap-2 px-4 py-4 md:py-6">
          <span className="text-cardTitle font-semibold text-accent">Cliniva</span>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-2 pb-3 md:flex-col md:pb-0">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-card px-3 py-2 text-body transition-colors ${
                  isActive
                    ? 'bg-accent/10 font-medium text-accent'
                    : 'text-text-secondary hover:bg-bg hover:text-text-primary'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* design.md 2.2: page context on the left, account on the right. */}
        <header className="flex items-center justify-between gap-4 border-b border-border bg-surface px-4 py-3">
          <div className="min-w-0">
            <p className="truncate text-body font-medium text-text-primary">
              {user ? user.name : 'Cliniva'}
            </p>
            {user && <p className="text-meta text-text-secondary">{titleCase(user.role)}</p>}
          </div>
          <Button variant="ghost" onClick={handleSignOut}>
            Sign out
          </Button>
        </header>

        {/* design.md 2.3: constrained width so tables do not stretch on wide
            monitors. */}
        <main className="mx-auto w-full max-w-[1280px] flex-1 px-4 py-6 md:px-6 md:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}
