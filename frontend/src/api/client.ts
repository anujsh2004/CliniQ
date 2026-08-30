import { readStoredSession } from './session';
import type { ApiError, ApiSuccess, ErrorCode, FieldError } from '@/types/api';

const BASE_URL = '/api/v1';

/**
 * A failed API call, carrying the canonical error code and any field-level
 * errors so a form can map them back onto its inputs (design.md 2.7).
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly errorCode?: ErrorCode;
  readonly fieldErrors: FieldError[];
  readonly requestId?: string;

  constructor(status: number, body: Partial<ApiError>) {
    super(body.message ?? 'Something went wrong. Please try again.');
    this.name = 'ApiRequestError';
    this.status = status;
    this.errorCode = body.errorCode;
    this.fieldErrors = body.errors ?? [];
    this.requestId = body.requestId;
  }

  /** The slot was taken between fetching the grid and submitting the booking. */
  get isSlotTaken(): boolean {
    return this.errorCode === 'SLOT_ALREADY_BOOKED';
  }
}

type TokenReader = () => string | null;
type UnauthorizedHandler = () => void;

// Reads storage by default, so a request made during the very first render -
// before any effect has configured the client - still carries the token.
let readToken: TokenReader = () => readStoredSession()?.accessToken ?? null;
let onUnauthorized: UnauthorizedHandler = () => {};

/**
 * The auth context owns the tokens; the client only needs to read the current
 * one and to say when the server rejected it.
 */
export function configureApiClient(options: {
  readToken: TokenReader;
  onUnauthorized: UnauthorizedHandler;
}): void {
  readToken = options.readToken;
  onUnauthorized = options.onUnauthorized;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Endpoints that are public by contract (register, login, refresh). */
  anonymous?: boolean;
}

/**
 * Every response from this backend is the standard envelope, so unwrapping is
 * uniform: success responses yield `data`, failures throw ApiRequestError.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, anonymous = false } = options;
  const headers: Record<string, string> = {};

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (!anonymous) {
    const token = readToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const payload = await readBody(response);

  if (!response.ok) {
    const error = new ApiRequestError(response.status, payload as Partial<ApiError>);
    // An expired or missing token ends the session rather than surfacing as a
    // confusing error on whichever screen happened to be open.
    if (response.status === 401 && !anonymous) {
      onUnauthorized();
    }
    throw error;
  }

  return (payload as ApiSuccess<T>).data;
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return { message: 'The server returned an unreadable response.' };
  }
}
