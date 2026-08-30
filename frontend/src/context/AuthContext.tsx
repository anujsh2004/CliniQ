import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { auth } from '@/api/endpoints';
import { configureApiClient } from '@/api/client';
import type { AuthUser, Role } from '@/types/api';

interface Session {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

interface AuthApi {
  user: AuthUser | null;
  isAuthenticated: boolean;
  hasRole: (...roles: Role[]) => boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
}

const AuthContext = createContext<AuthApi | null>(null);

const STORAGE_KEY = 'cliniva.session';

/**
 * Tokens are kept in sessionStorage rather than localStorage: closing the tab
 * ends the session, which suits a clinic's shared front-desk machine. The
 * access token is short-lived and the refresh token is exchanged for a new one
 * on demand.
 */
function readStoredSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function writeStoredSession(session: Session | null): void {
  try {
    if (session) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // A browser with storage disabled still works; the session just ends on
    // reload rather than surviving it.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => readStoredSession());

  const logout = useCallback(() => {
    setSession(null);
    writeStoredSession(null);
  }, []);

  // The API client reads the current token through these hooks rather than
  // holding its own copy, so there is one source of truth for the session.
  useEffect(() => {
    configureApiClient({
      readToken: () => readStoredSession()?.accessToken ?? null,
      onUnauthorized: logout,
    });
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await auth.login({ email, password });
    const next: Session = {
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      user: result.user,
    };
    writeStoredSession(next);
    setSession(next);
    return result.user;
  }, []);

  const api = useMemo<AuthApi>(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      hasRole: (...roles: Role[]) => (session ? roles.includes(session.user.role) : false),
      login,
      logout,
    }),
    [session, login, logout],
  );

  return <AuthContext.Provider value={api}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthApi {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
