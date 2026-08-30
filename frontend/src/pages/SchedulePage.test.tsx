import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SchedulePage } from './SchedulePage';
import { ToastProvider } from '@/components/Toast';
import { ApiRequestError } from '@/api/client';
import { appointments, doctors } from '@/api/endpoints';
import type { DoctorDayAppointments } from '@/types/api';

vi.mock('@/api/endpoints', () => ({
  doctors: { ownDay: vi.fn() },
  appointments: { complete: vi.fn() },
}));

function day(overrides: Partial<DoctorDayAppointments> = {}): DoctorDayAppointments {
  return {
    date: '2026-08-31',
    appointments: [
      {
        appointmentId: 'appt-1',
        patient: { patientId: 'pat-1', name: 'Anjali Verma', phone: '+919876543210' },
        startTime: '09:00:00',
        endTime: '09:30:00',
        status: 'CONFIRMED',
      },
      {
        appointmentId: 'appt-2',
        patient: { patientId: 'pat-2', name: 'Ravi Kumar', phone: '+919876500011' },
        startTime: '10:00:00',
        endTime: '10:30:00',
        status: 'COMPLETED',
      },
    ],
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <SchedulePage />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('SchedulePage', () => {
  beforeEach(() => {
    vi.mocked(doctors.ownDay).mockResolvedValue(day());
    vi.mocked(appointments.complete).mockReset();
  });

  it('shows the day with each patient and their phone number', async () => {
    // The doctor's list is the one place the contract exposes the patient's
    // phone, so the front desk can call them.
    renderPage();

    expect(await screen.findByText('Anjali Verma')).toBeInTheDocument();
    expect(screen.getByText('+919876543210')).toBeInTheDocument();
    expect(screen.getByText('09:00')).toBeInTheDocument();
  });

  it('offers completion only for appointments that are still live', async () => {
    renderPage();

    await screen.findByText('Anjali Verma');
    // One CONFIRMED appointment is completable; the COMPLETED one is not.
    expect(screen.getAllByRole('button', { name: /Mark complete/ })).toHaveLength(1);
  });

  it('reports follow-up eligibility after completing a visit', async () => {
    const user = userEvent.setup();
    vi.mocked(appointments.complete).mockResolvedValue({
      appointmentId: 'appt-1',
      status: 'COMPLETED',
      followUpEligible: true,
    });
    renderPage();

    await user.click(await screen.findByRole('button', { name: /Mark complete/ }));

    expect(await screen.findByText(/eligible for a follow-up/)).toBeInTheDocument();
    expect(appointments.complete).toHaveBeenCalledWith('appt-1');
  });

  it('explains a 403 as an unlinked account rather than a generic failure', async () => {
    // An account with the DOCTOR role but no doctor profile is a real state
    // this product can be in (see the doctor-profile linking question).
    vi.mocked(doctors.ownDay).mockRejectedValue(
      new ApiRequestError(403, {
        message: 'You are not allowed to access this resource',
        errorCode: 'UNAUTHORIZED_ACCESS',
        errors: [],
      }),
    );
    renderPage();

    expect(await screen.findByText(/not linked to a doctor profile/)).toBeInTheDocument();
  });

  it('shows an empty state on a day with nothing booked', async () => {
    vi.mocked(doctors.ownDay).mockResolvedValue(day({ appointments: [] }));
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('Nothing booked on this date')).toBeInTheDocument(),
    );
  });
});
