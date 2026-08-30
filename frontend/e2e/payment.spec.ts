import { expect } from '@playwright/test';
import {
  openSlotGrid,
  seedDoctorWithSlots,
  seedPatient,
  selectSlotAndReview,
  signIn,
  test,
  type SeededDoctor,
} from './fixtures';

/**
 * Paying for a booked appointment (API contract 14).
 *
 * <p>These run without a Razorpay publishable key, which is the state this
 * environment is actually in. That still exercises everything the clinic owns:
 * the order is created against the API, and the patient is told plainly that
 * online payment is unavailable rather than being dropped into a checkout that
 * cannot work. The hosted checkout itself is Razorpay's to test.
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

  test('an unpaid appointment offers a way to pay', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Payer');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '09:00');

    await expect(page.getByRole('button', { name: /pay now/i })).toBeVisible();
  });

  test('paying creates a real gateway order', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Order Maker');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '09:30');

    const orderRequest = page.waitForResponse(
      (response) => response.url().includes('/payments/create-order') && response.status() === 201,
    );
    await page.getByRole('button', { name: /pay now/i }).click();
    const response = await orderRequest;

    const order = (await response.json()).data;
    expect(order.gateway).toBe('RAZORPAY');
    expect(order.status).toBe('CREATED');
    expect(order.currency).toBe('INR');
    expect(order.orderId).toBeTruthy();
  });

  test('with no checkout key the patient is told, not dropped into a broken flow', async ({
    clientPage: page,
    request,
  }) => {
    const patient = await seedPatient(request, 'No Key');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '10:00');

    await page.getByRole('button', { name: /pay now/i }).click();

    await expect(page.getByText(/not set up yet/i)).toBeVisible();
  });

  test('the appointment is not confirmed by the browser saying so', async ({
    clientPage: page,
    request,
  }) => {
    // The guarantee from product-description.md 8.6: only a signed webhook
    // moves an appointment to CONFIRMED. Creating an order must not.
    const patient = await seedPatient(request, 'Not Confirmed');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '10:30');

    await page.getByRole('button', { name: /pay now/i }).click();
    await expect(page.getByText(/not set up yet/i)).toBeVisible();

    await expect(page.getByText('Pending payment')).toBeVisible();
    await expect(page.getByText('Confirmed', { exact: true })).toBeHidden();
  });

  test('a cancelled appointment shows no payment badge and no way to pay', async ({
    clientPage: page,
    request,
  }) => {
    // A cancelled appointment carries no payment obligation, so a "Pending"
    // badge on one is noise at best and alarming at worst.
    const patient = await seedPatient(request, 'Cancelled Badge');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '11:00');

    await page.getByRole('button', { name: /^cancel$/i }).click();
    await page.getByLabel('Reason').fill('Changed my mind');
    await page.getByRole('button', { name: /cancel appointment/i }).click();
    await expect(page.getByText('Cancelled', { exact: true })).toBeVisible();

    await expect(page.getByText('Pending', { exact: true })).toBeHidden();
    await expect(page.getByRole('button', { name: /pay now/i })).toBeHidden();
  });

  test('starting a payment does not add a meaningless "Created" badge', async ({
    clientPage: page,
    request,
  }) => {
    // Once an order exists the payment status becomes CREATED, which is the
    // gateway's word for "an order exists". To a patient it says nothing the
    // status has not already said, and it read as a second badge beside
    // "Pending payment".
    const patient = await seedPatient(request, 'Created Badge');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '09:00');

    await page.getByRole('button', { name: /pay now/i }).click();
    await expect(page.getByText(/not set up yet/i)).toBeVisible();

    await expect(page.getByText('Pending payment')).toBeVisible();
    await expect(page.getByText('Created', { exact: true })).toBeHidden();
    // Still payable.
    await expect(page.getByRole('button', { name: /pay now/i })).toBeVisible();
  });

  test('an unpaid appointment does not say "Pending" twice', async ({
    clientPage: page,
    request,
  }) => {
    // The status already reads "Pending payment"; a second amber "Pending"
    // badge beside it is the same fact repeated.
    const patient = await seedPatient(request, 'No Double Badge');
    await signIn(page, patient.email);
    await bookAnAppointment(page, '11:30');

    await expect(page.getByText('Pending payment')).toBeVisible();
    await expect(page.getByText('Pending', { exact: true })).toBeHidden();
  });
});
