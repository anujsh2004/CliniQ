import { expect, type APIRequestContext, type Page } from '@playwright/test';

export const API = process.env.E2E_API_URL ?? 'http://localhost:8080/api/v1';
export const PASSWORD = 'StrongPassword123';

/** Unique per run, so repeated runs never collide on email or licence number. */
export function unique(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
}

export interface TestPatient {
  email: string;
  name: string;
}

export interface SeededDoctor {
  doctorId: string;
  name: string;
  /** A date, yyyy-mm-dd, on which this doctor has freshly generated slots. */
  date: string;
}

async function register(
  request: APIRequestContext,
  role: 'PATIENT' | 'DOCTOR' | 'ADMIN',
  name: string,
): Promise<{ email: string; name: string }> {
  const email = `${unique(role.toLowerCase())}@example.com`;
  // Phone must be unique too: the contract enforces it.
  const phone = `+9198${Math.floor(10000000 + Math.random() * 89999999)}`;
  const response = await request.post(`${API}/auth/register`, {
    data: { name, email, phone, password: PASSWORD, role },
  });
  expect(response.status(), await response.text()).toBe(201);
  return { email, name };
}

async function token(request: APIRequestContext, email: string): Promise<string> {
  const response = await request.post(`${API}/auth/login`, {
    data: { email, password: PASSWORD },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data.accessToken;
}

/** The next occurrence of a weekday, far enough out to be inside the slot horizon. */
function nextWeekday(weekday: number): Date {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  while (date.getDay() !== weekday) {
    date.setDate(date.getDate() + 1);
  }
  return date;
}

function toApiDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

const DAY_NAMES = [
  'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY',
];

/**
 * Creates a brand new doctor with availability on an upcoming weekday.
 *
 * <p>Each run gets its own doctor rather than reusing seeded data, so tests
 * never fail because a previous run consumed the slots.
 */
export async function seedDoctorWithSlots(request: APIRequestContext): Promise<SeededDoctor> {
  const doctorName = `Dr. E2E ${unique('')}`;
  const account = await register(request, 'DOCTOR', doctorName);
  const doctorToken = await token(request, account.email);
  const auth = { Authorization: `Bearer ${doctorToken}` };

  const created = await request.post(`${API}/doctors`, {
    headers: auth,
    data: {
      name: doctorName,
      specialization: 'Dentist',
      licenseNumber: unique('LIC').toUpperCase(),
      consultationFee: 500,
      clinic: {
        name: 'E2E Test Clinic',
        address: 'MG Road, Chennai',
        phone: '+919876543210',
      },
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  const doctorId = (await created.json()).data.doctorId;

  // A weekday a few days out: inside the 30-day generation horizon and never
  // today, so no slot has already passed.
  const target = nextWeekday(3);
  const availability = await request.post(`${API}/doctors/${doctorId}/availability`, {
    headers: auth,
    data: {
      dayOfWeek: DAY_NAMES[target.getDay()],
      startTime: '09:00:00',
      endTime: '12:00:00',
      slotDurationMinutes: 30,
    },
  });
  expect(availability.status(), await availability.text()).toBe(201);

  return { doctorId, name: doctorName, date: toApiDate(target) };
}

export async function seedPatient(request: APIRequestContext, name: string): Promise<TestPatient> {
  return register(request, 'PATIENT', name);
}

/** Signs in through the real login form, as a patient would. */
export async function signIn(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page.getByRole('heading', { name: 'Doctors' })).toBeVisible();
}

/** Books a doctor's slot at a given time through the API, as another patient would. */
export async function bookSlotViaApi(
  request: APIRequestContext,
  doctor: SeededDoctor,
  startTime: string,
): Promise<void> {
  const patient = await seedPatient(request, 'API Booker');
  const patientToken = await token(request, patient.email);
  const auth = { Authorization: `Bearer ${patientToken}` };

  const slots = await request.get(
    `${API}/doctors/${doctor.doctorId}/slots?date=${doctor.date}`,
    { headers: auth },
  );
  expect(slots.ok(), await slots.text()).toBeTruthy();
  const slot = (await slots.json()).data.slots.find(
    (s: { startTime: string; status: string }) => s.startTime === startTime,
  );
  expect(slot, `no slot at ${startTime}`).toBeTruthy();

  const booked = await request.post(`${API}/appointments`, {
    headers: auth,
    data: { doctorId: doctor.doctorId, slotId: slot.slotId, reason: 'Taken by another patient' },
  });
  expect(booked.status(), await booked.text()).toBe(201);
}
