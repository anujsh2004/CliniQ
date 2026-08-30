import { expect, test as base, type APIRequestContext, type Page } from '@playwright/test';

export const API = process.env.E2E_API_URL ?? 'http://localhost:8080/api/v1';
export const PASSWORD = 'StrongPassword123';

let clientCounter = 0;

/**
 * A distinct client address for each simulated person.
 *
 * <p>The API rate limits authentication per client, which is correct and stays
 * enabled during these tests. Without this, a suite that signs several people
 * in inside a minute looks exactly like one machine guessing passwords, and
 * gets blocked. Real patients arrive from different addresses, so the tests
 * model that rather than turning the control off.
 */
export function nextClientIp(): string {
  clientCounter += 1;
  return `203.0.113.${clientCounter % 250}`;
}

/** Headers that make a request look like it came from its own client. */
export function asClient(ip: string): Record<string, string> {
  return { 'X-Forwarded-For': ip };
}

/**
 * A page whose every request carries its own client address, so signing in
 * does not consume another test's rate limit allowance.
 */
export const test = base.extend<{ clientPage: Page }>({
  clientPage: async ({ browser }, use) => {
    const context = await browser.newContext({ extraHTTPHeaders: asClient(nextClientIp()) });
    const page = await context.newPage();
    await use(page);
    await context.close();
  },
});

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
  clientIp: string,
): Promise<{ email: string; name: string }> {
  const email = `${unique(role.toLowerCase())}@example.com`;
  // Phone must be unique too: the contract enforces it.
  const phone = `+9198${Math.floor(10000000 + Math.random() * 89999999)}`;
  const response = await request.post(`${API}/auth/register`, {
    headers: asClient(clientIp),
    data: { name, email, phone, password: PASSWORD, role },
  });
  expect(response.status(), await response.text()).toBe(201);
  return { email, name };
}

async function token(request: APIRequestContext, email: string, clientIp: string): Promise<string> {
  const response = await request.post(`${API}/auth/login`, {
    headers: asClient(clientIp),
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
  const clientIp = nextClientIp();
  const account = await register(request, 'DOCTOR', doctorName, clientIp);
  const doctorToken = await token(request, account.email, clientIp);
  const auth = { Authorization: `Bearer ${doctorToken}`, ...asClient(clientIp) };

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
  return register(request, 'PATIENT', name, nextClientIp());
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
  const clientIp = nextClientIp();
  const patient = await seedPatient(request, 'API Booker');
  const patientToken = await token(request, patient.email, clientIp);
  const auth = { Authorization: `Bearer ${patientToken}`, ...asClient(clientIp) };

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

/**
 * Opens a doctor's page and waits until the grid for {@code doctor.date} has
 * actually arrived.
 *
 * <p>Clicking a date tab starts a fetch. Clicking a slot before that fetch
 * resolves acts on the previous date's grid, and the selection is discarded
 * when the new one renders - so the review step never opens. Waiting for the
 * response removes the race rather than papering over it with a sleep.
 */
export async function openSlotGrid(page: Page, doctor: SeededDoctor): Promise<void> {
  await page.goto(`/doctors/${doctor.doctorId}`);

  const slotsLoaded = page.waitForResponse(
    (response) =>
      response.url().includes(`/doctors/${doctor.doctorId}/slots`) &&
      response.url().includes(`date=${doctor.date}`) &&
      response.ok(),
  );
  await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();
  await slotsLoaded;
}

/** The date strip renders dates as "Wed 10 Sep 2026". */
export function formatStripLabel(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

/**
 * Picks a slot and waits for the review step.
 *
 * <p>Retries the click as a unit. The slots response arriving is not the same
 * as the new grid being rendered: a click landing in that gap selects a slot
 * from the previous date, whose id is absent from the new list, so the app
 * correctly discards the selection and no review step appears. Retrying the
 * click-and-check together is the honest fix; a fixed sleep would only make the
 * gap likelier to be missed on a slower machine.
 */
export async function selectSlotAndReview(page: Page, startTime: string): Promise<void> {
  try {
    await expect(async () => {
      await page.getByRole('button', { name: new RegExp(`^${startTime}`) }).click();
      await expect(page.getByText('Confirm your appointment')).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 20_000 });
  } catch (error) {
    const buttons = await page.getByRole('button').allTextContents();
    const url = page.url();
    const body = (await page.locator('body').innerText()).slice(0, 400);
    throw new Error(
      `Could not reach the review step for ${startTime}.
URL: ${url}
` +
        `Buttons: ${JSON.stringify(buttons)}
Body:
${body}
Original: ${String(error)}`,
    );
  }
}
