import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError, configureApiClient, request } from './client';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    configureApiClient({ readToken: () => 'test-token', onUnauthorized: () => {} });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('unwraps the data field of the standard success envelope', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, {
        success: true,
        message: 'Doctor fetched successfully',
        data: { doctorId: 'abc', name: 'Dr. Sharma' },
        timestamp: '2026-08-20T10:00:00+05:30',
        requestId: 'req_01',
      }),
    );

    await expect(request('/doctors/abc')).resolves.toEqual({ doctorId: 'abc', name: 'Dr. Sharma' });
  });

  it('sends the bearer token on authenticated calls', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: {} }));

    await request('/patients/me');

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
  });

  it('omits the token on endpoints that are public by contract', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: {} }));

    await request('/auth/login', { method: 'POST', body: {}, anonymous: true });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it('surfaces field errors from a 422 so a form can map them onto inputs', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(422, {
        success: false,
        message: 'Validation failed',
        errorCode: 'VALIDATION_ERROR',
        errors: [{ field: 'phone', message: 'Phone number is invalid' }],
        requestId: 'req_02',
      }),
    );

    await expect(request('/auth/register', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 422,
      errorCode: 'VALIDATION_ERROR',
      fieldErrors: [{ field: 'phone', message: 'Phone number is invalid' }],
    });
  });

  it('recognises a lost slot race so the booking flow can react to it', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        success: false,
        message: 'Appointment slot is already booked',
        errorCode: 'SLOT_ALREADY_BOOKED',
        errors: [],
      }),
    );

    await expect(request('/appointments', { method: 'POST', body: {} })).rejects.toSatisfy(
      (error: unknown) => error instanceof ApiRequestError && error.isSlotTaken,
    );
  });

  it('ends the session when the server rejects the token', async () => {
    const onUnauthorized = vi.fn();
    configureApiClient({ readToken: () => 'stale-token', onUnauthorized });
    fetchMock.mockResolvedValue(
      jsonResponse(401, { success: false, message: 'Invalid email or password', errors: [] }),
    );

    await expect(request('/patients/me')).rejects.toBeInstanceOf(ApiRequestError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('does not end the session when a login attempt itself fails', async () => {
    // A wrong password on the login screen must not look like an expired
    // session; the user is not signed in to begin with.
    const onUnauthorized = vi.fn();
    configureApiClient({ readToken: () => null, onUnauthorized });
    fetchMock.mockResolvedValue(
      jsonResponse(401, { success: false, message: 'Invalid email or password', errors: [] }),
    );

    await expect(
      request('/auth/login', { method: 'POST', body: {}, anonymous: true }),
    ).rejects.toBeInstanceOf(ApiRequestError);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
