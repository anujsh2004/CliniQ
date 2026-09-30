import { request, requestAudio, requestForm } from './client';
import type {
  Availability,
  NotificationSummary,
  PaymentStatus,
  PaymentOrder,
  AppointmentCreated,
  AppointmentDetail,
  AppointmentListItem,
  DoctorDayAppointments,
  DoctorDetail,
  DoctorSlots,
  DoctorSummary,
  LoginResult,
  Paged,
  PatientProfile,
  RegisterResult,
  Role,
} from '@/types/api';

/** One function per endpoint in the API contract, named after what it does. */

export const auth = {
  register: (body: {
    name: string;
    email: string;
    phone: string;
    password: string;
    role: Role;
  }) => request<RegisterResult>('/auth/register', { method: 'POST', body, anonymous: true }),

  login: (body: { email: string; password: string }) =>
    request<LoginResult>('/auth/login', { method: 'POST', body, anonymous: true }),

  refresh: (body: { refreshToken: string }) =>
    request<{ accessToken: string; expiresIn: number }>('/auth/refresh', {
      method: 'POST',
      body,
      anonymous: true,
    }),
};

export const doctors = {
  list: (page = 0, size = 10) =>
    request<Paged<DoctorSummary>>(`/doctors?page=${page}&size=${size}`),

  get: (doctorId: string) => request<DoctorDetail>(`/doctors/${doctorId}`),

  /**
   * The signed-in doctor's own profile (API contract 9, v1.2). Replaces
   * matching the account name against the doctor list, which picked the wrong
   * profile when two doctors shared a name.
   */
  me: () => request<DoctorDetail>('/doctors/me'),

  create: (body: {
    name: string;
    specialization: string;
    licenseNumber: string;
    consultationFee: number;
    /** Links the profile to an existing doctor account (v1.2). */
    accountEmail?: string;
    clinic: { name: string; address: string; phone: string };
  }) => request<DoctorDetail>('/doctors', { method: 'POST', body }),

  slots: (doctorId: string, date: string) =>
    request<DoctorSlots>(`/doctors/${doctorId}/slots?date=${date}`),

  /** Every weekly window a doctor has defined (API contract 11, v1.3). */
  availability: (doctorId: string) => request<Availability[]>(`/doctors/${doctorId}/availability`),

  updateAvailability: (
    doctorId: string,
    availabilityId: string,
    body: { dayOfWeek: string; startTime: string; endTime: string; slotDurationMinutes: number },
  ) =>
    request<Availability>(`/doctors/${doctorId}/availability/${availabilityId}`, {
      method: 'PUT',
      body,
    }),

  deleteAvailability: (doctorId: string, availabilityId: string) =>
    request<void>(`/doctors/${doctorId}/availability/${availabilityId}`, { method: 'DELETE' }),

  /** Blocks a single date without touching the weekly pattern (v1.3). */
  timeOff: (doctorId: string, date: string, reason?: string) =>
    request<{ date: string; slotsBlocked: number; appointmentsToReschedule: number }>(
      `/doctors/${doctorId}/time-off`,
      { method: 'POST', body: { date, reason } },
    ),

  addAvailability: (
    doctorId: string,
    body: { dayOfWeek: string; startTime: string; endTime: string; slotDurationMinutes: number },
  ) => request<Availability>(`/doctors/${doctorId}/availability`, { method: 'POST', body }),

  ownDay: (date: string) => request<DoctorDayAppointments>(`/doctors/me/appointments?date=${date}`),
};

export const patients = {
  me: () => request<PatientProfile>('/patients/me'),

  update: (body: { name: string; phone: string }) =>
    request<{ patientId: string; name: string; phone: string }>('/patients/me', {
      method: 'PUT',
      body,
    }),
};

export const payments = {
  /**
   * Creates a gateway order for an appointment (API contract 14).
   *
   * <p>Nothing the browser reports about the outcome is trusted: the
   * appointment only becomes CONFIRMED when the gateway's signed webhook
   * reaches the backend.
   */
  /**
   * Confirms an appointment without a gateway (v1.4). Razorpay is deferred, so
   * this stands in for the checkout; the server refuses it once real
   * credentials exist.
   */
  demoConfirm: (appointmentId: string) =>
    request<{ paymentId: string; appointmentId: string; status: PaymentStatus }>(
      '/payments/demo-confirm',
      { method: 'POST', body: { appointmentId } },
    ),

  createOrder: (appointmentId: string) =>
    request<PaymentOrder>('/payments/create-order', {
      method: 'POST',
      body: { appointmentId },
    }),
};

export const appointments = {
  book: (body: { doctorId: string; slotId: string; reason?: string }) =>
    request<AppointmentCreated>('/appointments', { method: 'POST', body }),

  mine: (page = 0, size = 10) =>
    request<Paged<AppointmentListItem>>(`/appointments/my?page=${page}&size=${size}`),

  get: (appointmentId: string) => request<AppointmentDetail>(`/appointments/${appointmentId}`),

  cancel: (appointmentId: string, reason: string) =>
    request<{ appointmentId: string; status: string }>(`/appointments/${appointmentId}/cancel`, {
      method: 'PATCH',
      body: { reason },
    }),

  reschedule: (appointmentId: string, newSlotId: string) =>
    request<{ appointmentId: string; date: string; startTime: string; endTime: string }>(
      `/appointments/${appointmentId}/reschedule`,
      { method: 'PATCH', body: { newSlotId } },
    ),

  complete: (appointmentId: string) =>
    request<{ appointmentId: string; status: string; followUpEligible: boolean }>(
      `/appointments/${appointmentId}/complete`,
      { method: 'PATCH' },
    ),
};

export const notifications = {
  mine: () => request<NotificationSummary[]>('/notifications'),

  /** Drives the bell's unread count, so it stays a single small read. */
  unreadCount: () => request<{ unread: number }>('/notifications/unread-count'),

  addReminder: (appointmentId: string, minutesBefore: number) =>
    request<NotificationSummary>('/notifications/reminders', {
      method: 'POST',
      body: { appointmentId, minutesBefore },
    }),

  removeReminder: (notificationId: string) =>
    request<void>(`/notifications/reminders/${notificationId}`, { method: 'DELETE' }),

  markRead: (notificationId: string) =>
    request<void>(`/notifications/${notificationId}/read`, { method: 'PATCH' }),

  markAllRead: () => request<void>('/notifications/read-all', { method: 'PATCH' }),
};

export interface Transcript {
  text: string;
  language: string;
  decoder: string;
  durationSeconds: number;
  elapsedMs: number;
}

export const voice = {
  /**
   * Sends a recording to the clinic's own speech model.
   *
   * <p>Defaults to Tamil: this endpoint exists because typing Tamil on a phone
   * is slow, so a patient reaching for the microphone is overwhelmingly likely
   * to be speaking it.
   */
  transcribe: (audio: Blob, language = 'ta') => {
    const form = new FormData();
    form.append('audio', audio, 'recording.webm');
    form.append('language', language);
    return requestForm<Transcript>('/voice/transcribe', form);
  },

  languages: () => request<{ languages: string[] }>('/voice/languages'),

  /**
   * Asks the clinic's own voice model to read a reply aloud.
   *
   * <p>Returns audio rather than JSON, so it bypasses the envelope helpers.
   * A failure here is not worth surfacing to the patient: the reply is
   * already on screen, so the caller falls back to the browser's voice.
   */
  speak: async (text: string, language: string): Promise<Blob> => {
    const form = new FormData();
    form.append('text', text);
    form.append('language', language);
    return requestAudio('/voice/speak', form);
  },
};
