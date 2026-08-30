import { request } from './client';
import type {
  Availability,
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

  create: (body: {
    name: string;
    specialization: string;
    licenseNumber: string;
    consultationFee: number;
    clinic: { name: string; address: string; phone: string };
  }) => request<DoctorDetail>('/doctors', { method: 'POST', body }),

  slots: (doctorId: string, date: string) =>
    request<DoctorSlots>(`/doctors/${doctorId}/slots?date=${date}`),

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
