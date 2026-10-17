import { expect } from '@playwright/test';
import {
  asClient,
  bookSlotViaApi,
  nextClientIp,
  openSlotGrid,
  seedDoctorWithSlots,
  seedPatient,
  selectSlotAndReview,
  signIn,
  test,
  type SeededDoctor,
} from './fixtures';

/**
 * Paying for a held appointment (API contract 14, v1.4).
 *
 * <p>Razorpay is deferred, so payment is a demo confirmation that takes the
 * same path a captured webhook does. The server refuses it once real gateway
 * credentials exist, so this flow cannot leak into an environment that handles
 * real money.
 */
test.describe('Paying for an appointment', () => {
  let doctor: SeededDoctor;

  test.beforeEach(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  async function bookAnAppointment(page: import('@playwright/test').Page, time: string) {
    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, time);
    await page.getByRole('button', { name: /confirm booking/i }).click();
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();
  }

  test('an unpaid appointment offers a way to pay, and shows its countdown', async ({
    clientPage: page,
    request,
  }) => {
    const patient = await seedPatient(request, 'Payer');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '09:00');

    await expect(page.getByRole('button', { name: /pay now/i })).toBeVisible();
    // The slot is held, not sold, and the patient can see the clock.
    await expect(page.getByText(/slot held/i)).toBeVisible();
  });

  test('paying confirms the appointment', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Confirmer');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '09:30');

    await page.getByRole('button', { name: /pay now/i }).click();

    await expect(page.getByText('Confirmed', { exact: true })).toBeVisible();
    await expect(page.getByText('Paid', { exact: true })).toBeVisible();
    // Nothing left to pay, and no clock left to run.
    await expect(page.getByRole('button', { name: /pay now/i })).toBeHidden();
    await expect(page.getByText(/slot held/i)).toBeHidden();
  });

  test('a confirmed appointment owns its slot outright', async ({
    clientPage: page,
    request,
  }) => {
    // The point of paying: the slot stops being at risk of expiry.
    const patient = await seedPatient(request, 'Owner');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '10:00');
    await page.getByRole('button', { name: /pay now/i }).click();
    await expect(page.getByText('Confirmed', { exact: true })).toBeVisible();

    await page.reload();

    await expect(page.getByText('Confirmed', { exact: true })).toBeVisible();
    await expect(page.getByText(/hold expired/i)).toBeHidden();
  });

  test('a cancelled appointment shows no payment badge and no way to pay', async ({
    clientPage: page,
    request,
  }) => {
    // A cancelled appointment carries no payment obligation, so a badge on one
    // is noise at best and alarming at worst.
    const patient = await seedPatient(request, 'Cancelled Badge');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '10:30');

    await page.getByRole('button', { name: /^cancel$/i }).click();
    await page.getByLabel('Reason').fill('Changed my mind');
    await page.getByRole('button', { name: /cancel appointment/i }).click();
    await expect(page.getByText('Cancelled', { exact: true })).toBeVisible();

    await expect(page.getByText('Pending', { exact: true })).toBeHidden();
    await expect(page.getByRole('button', { name: /pay now/i })).toBeHidden();
  });

  test('an unpaid appointment does not say "Pending" twice', async ({
    clientPage: page,
    request,
  }) => {
    // The status already reads "Pending payment"; a second amber "Pending"
    // badge beside it is the same fact repeated.
    const patient = await seedPatient(request, 'No Double Badge');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '11:00');

    await expect(page.getByText('Pending payment')).toBeVisible();
    await expect(page.getByText('Pending', { exact: true })).toBeHidden();
  });

  test('a patient cannot pay for someone else’s appointment', async ({ request }) => {
    // The API is the boundary, not the button. One patient books; another asks
    // the server to confirm payment on it.
    const appointmentId = await bookSlotViaApi(request, doctor, '11:30:00');

    const stranger = await seedPatient(request, 'Stranger');
    const clientIp = nextClientIp();
    const login = await request.post('http://localhost:8080/api/v1/auth/login', {
      headers: asClient(clientIp),
      data: { email: stranger.email, password: 'StrongPassword123' },
    });
    const token = (await login.json()).data.accessToken;

    const attempt = await request.post('http://localhost:8080/api/v1/payments/demo-confirm', {
      headers: { Authorization: `Bearer ${token}`, ...asClient(clientIp) },
      data: { appointmentId },
    });

    expect(attempt.status()).toBe(403);
  });
});
