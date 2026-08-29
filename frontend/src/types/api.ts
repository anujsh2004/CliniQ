/**
 * TypeScript mirrors of the API contract. Field names here must match the
 * contract exactly (contract 22: never invent a field name the contract
 * already defines).
 */

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN';

export type SlotStatus = 'AVAILABLE' | 'HELD' | 'BOOKED' | 'BLOCKED' | 'EXPIRED';

export type AppointmentStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

export type PaymentStatus = 'PENDING' | 'CREATED' | 'PAID' | 'FAILED' | 'REFUNDED';

/** The canonical error codes from API contract 7a. */
export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'SLOT_ALREADY_BOOKED'
  | 'SLOT_NOT_FOUND'
  | 'APPOINTMENT_NOT_FOUND'
  | 'UNAUTHORIZED_ACCESS'
  | 'INVALID_CREDENTIALS'
  | 'DOCTOR_NOT_FOUND'
  | 'DUPLICATE_EMAIL';

export interface FieldError {
  field: string;
  message: string;
}

/** The success envelope from API contract 7. */
export interface ApiSuccess<T> {
  success: true;
  message: string;
  data: T;
  timestamp: string;
  requestId: string;
}

/** The error envelope from API contract 7. */
export interface ApiError {
  success: false;
  message: string;
  errorCode?: ErrorCode;
  errors: FieldError[];
  timestamp: string;
  requestId: string;
}

export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuthUser {
  userId: string;
  name: string;
  role: Role;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
}

export interface RegisterResult {
  userId: string;
  name: string;
  email: string;
  phone: string;
  role: Role;
}

export interface ClinicBlock {
  clinicId: string;
  name: string;
  address: string;
  phone?: string;
}

export interface DoctorSummary {
  doctorId: string;
  name: string;
  specialization: string;
  consultationFee: number;
}

export interface DoctorDetail extends DoctorSummary {
  clinic: ClinicBlock;
}

export interface Slot {
  slotId: string;
  startTime: string;
  endTime: string;
  status: SlotStatus;
}

/** Note: date lives here, once, never inside a slot (contract 11). */
export interface DoctorSlots {
  doctorId: string;
  date: string;
  slots: Slot[];
}

export interface AppointmentCreated {
  appointmentId: string;
  doctorId: string;
  patientId: string;
  slotId: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  paymentStatus: PaymentStatus;
}

export interface AppointmentListItem {
  appointmentId: string;
  doctorName: string;
  date: string;
  startTime: string;
  status: AppointmentStatus;
  paymentStatus: PaymentStatus;
}

export interface AppointmentDetail {
  appointmentId: string;
  doctor: { doctorId: string; name: string };
  patient: { patientId: string; name: string; phone?: string };
  date: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  paymentStatus: PaymentStatus;
}

export interface DoctorDayAppointment {
  appointmentId: string;
  patient: { patientId: string; name: string; phone?: string };
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
}

export interface DoctorDayAppointments {
  date: string;
  appointments: DoctorDayAppointment[];
}

export interface PatientProfile {
  patientId: string;
  name: string;
  email: string;
  phone: string;
}

export interface Availability {
  availabilityId: string;
  doctorId: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
}
