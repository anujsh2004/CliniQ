import type { AuthUser } from '@/types/api';

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

const STORAGE_KEY = 'cliniva.session';

/**
 * The stored session, read straight from storage rather than from React state.
 *
 * <p>This is deliberately not a hook. On a full page load - a refresh, or
 * opening a deep link - queries fire during the first render, before any effect
 * has run. If the API client only learned the token from an effect, those first
 * requests would go out unauthenticated, come back 401, and log the user
 * straight back out. Reading storage directly means the token is available from
 * the very first request.
 */
export function readStoredSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function writeStoredSession(session: Session | null): void {
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
