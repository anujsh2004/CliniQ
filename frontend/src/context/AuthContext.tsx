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
import { readStoredSession, writeStoredSession, type Session } from '@/api/session';
import type { AuthUser, Role } from '@/types/api';

interface AuthApi {
  user: AuthUser | null;
  isAuthenticated: boolean;
  hasRole: (...roles: Role[]) => boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
}

const AuthContext = createContext<AuthApi | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => readStoredSession());

  const logout = useCallback(() => {
    setSession(null);
    writeStoredSession(null);
  }, []);

  // The client already reads the token from storage by default; this only
  // tells it what to do when the server rejects one.
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
