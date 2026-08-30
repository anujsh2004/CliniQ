import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DoctorDetailPage } from './DoctorDetailPage';
import { ToastProvider } from '@/components/Toast';
import { ApiRequestError } from '@/api/client';
import { appointments, doctors } from '@/api/endpoints';
import { upcomingDates } from '@/lib/format';
import type { DoctorDetail, DoctorSlots } from '@/types/api';

vi.mock('@/api/endpoints', () => ({
  doctors: { get: vi.fn(), slots: vi.fn() },
  appointments: { book: vi.fn() },
}));

const DOCTOR: DoctorDetail = {
  doctorId: 'doc-1',
  name: 'Dr. Sharma',
  specialization: 'Dentist',
  consultationFee: 500,
  clinic: {
    clinicId: 'clinic-1',
    name: 'Sharma Dental Clinic',
    address: 'MG Road, Chennai',
    phone: '+919876543210',
  },
};

function slotsFor(date: string): DoctorSlots {
  return {
    doctorId: 'doc-1',
    date,
    slots: [
      { slotId: 'slot-1', startTime: '10:00:00', endTime: '10:30:00', status: 'AVAILABLE' },
      { slotId: 'slot-2', startTime: '10:30:00', endTime: '11:00:00', status: 'BOOKED' },
      { slotId: 'slot-3', startTime: '17:30:00', endTime: '18:00:00', status: 'AVAILABLE' },
    ],
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/doctors/doc-1']}>
          <Routes>
            <Route path="/doctors/:doctorId" element={<DoctorDetailPage />} />
            <Route path="/appointments" element={<p>My appointments</p>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('DoctorDetailPage', () => {
  beforeEach(() => {
    vi.mocked(doctors.get).mockResolvedValue(DOCTOR);
    vi.mocked(doctors.slots).mockImplementation((_id, date) => Promise.resolve(slotsFor(date)));
    vi.mocked(appointments.book).mockReset();
  });

  it('offers only available slots, grouped by time of day', async () => {
    renderPage();

    expect(await screen.findByText('Morning')).toBeInTheDocument();
    expect(screen.getByText('Evening')).toBeInTheDocument();
    // 10:30 is BOOKED, so it is present but not selectable.
    expect(screen.getByRole('button', { name: /10:00/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /10:30/ })).toBeDisabled();
  });

  it('shows a review step before booking, because booking creates an obligation', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /10:00/ }));

    // design.md 3.6: doctor, date, time and fee are all restated before confirm.
    const heading = await screen.findByText('Confirm your appointment');
    const reviewCard = heading.closest('div');
    expect(reviewCard).not.toBeNull();
    const summary = reviewCard!.textContent ?? '';
    expect(summary).toContain('Dr. Sharma');
    expect(summary).toContain('10:00–10:30');
    expect(summary).toContain('₹500');
  });

  it('books the selected slot and sends the patient to their appointments', async () => {
    const user = userEvent.setup();
    vi.mocked(appointments.book).mockResolvedValue({
      appointmentId: 'appt-1',
      doctorId: 'doc-1',
      patientId: 'pat-1',
      slotId: 'slot-1',
      appointmentDate: upcomingDates(1)[0],
      startTime: '10:00:00',
      endTime: '10:30:00',
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
    });
    renderPage();

    await user.click(await screen.findByRole('button', { name: /10:00/ }));
    await user.click(screen.getByRole('button', { name: /Confirm booking/ }));

    await waitFor(() => expect(screen.getByText('My appointments')).toBeInTheDocument());
    expect(appointments.book).toHaveBeenCalledWith(
      expect.objectContaining({ doctorId: 'doc-1', slotId: 'slot-1' }),
    );
  });

  it('returns the patient to slot selection when the slot is taken mid-booking', async () => {
    // The race the backend rejects with 409 SLOT_ALREADY_BOOKED has to land as
    // something a patient can act on, not a dead end (design.md 2.14, 4.5).
    const user = userEvent.setup();
    vi.mocked(appointments.book).mockRejectedValue(
      new ApiRequestError(409, {
        message: 'Appointment slot is already booked',
        errorCode: 'SLOT_ALREADY_BOOKED',
        errors: [],
      }),
    );
    renderPage();

    await user.click(await screen.findByRole('button', { name: /10:00/ }));
    await user.click(screen.getByRole('button', { name: /Confirm booking/ }));

    // The review step closes, the grid comes back, and the lost slot is marked.
    await waitFor(() =>
      expect(screen.queryByText('Confirm your appointment')).not.toBeInTheDocument(),
    );
    expect(await screen.findByText('Just booked')).toBeInTheDocument();
    expect(
      screen.getByText(/That slot was just booked by someone else/),
    ).toBeInTheDocument();
    // And the grid was refetched, so the patient sees current availability.
    await waitFor(() => expect(vi.mocked(doctors.slots).mock.calls.length).toBeGreaterThan(1));
  });

  it('clears the selection when the patient switches date', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /10:00/ }));
    expect(await screen.findByText('Confirm your appointment')).toBeInTheDocument();

    const tabs = screen.getAllByRole('tab');
    await user.click(tabs[1]);

    await waitFor(() =>
      expect(screen.queryByText('Confirm your appointment')).not.toBeInTheDocument(),
    );
  });
});
