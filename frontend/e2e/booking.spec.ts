import { expect } from '@playwright/test';
import {
  bookSlotViaApi,
  seedDoctorWithSlots,
  seedPatient,
  openSlotGrid,
  selectSlotAndReview,
  signIn,
  test,
  type SeededDoctor,
} from './fixtures';

/**
 * The patient booking journey from product-description.md 6.1, end to end
 * through the browser against a real API and database.
 */
test.describe('Patient booking journey', () => {
  let doctor: SeededDoctor;

  // A fresh doctor per test. Sharing one made each test depend on what the
  // previous test had booked, so a single failure cascaded through the file
  // and the order of the tests silently mattered.
  test.beforeEach(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  test('a patient registers, books a slot, and sees it in their appointments', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'E2E Booker');
    await signIn(page, patient.email);

    // Straight to this run's doctor: the list is paginated and a freshly
    // created doctor is not guaranteed to be on the first page.
    await openSlotGrid(page, doctor);
    await expect(page.getByRole('heading', { name: doctor.name })).toBeVisible();

    // The review step restates the booking before it is committed, because
    // booking creates a payment obligation (design.md 3.6).
    await selectSlotAndReview(page, '09:00');
    await expect(page.getByText(/09:00–09:30/)).toBeVisible();

    await page.getByRole('button', { name: /confirm booking/i }).click();

    // Landing on My appointments with the booking present is the proof.
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();
    await expect(page.getByText(doctor.name)).toBeVisible();
    await expect(page.getByText('Pending payment')).toBeVisible();
  });

  test('a booked slot is no longer offered to the next patient', async ({ clientPage: page, request }) => {
    // Someone else takes 09:00 through the API; the grid must show it as gone
    // rather than only rejecting it on submit.
    await bookSlotViaApi(request, doctor, '09:00:00');
    const patient = await seedPatient(request, 'E2E Second Looker');
    await signIn(page, patient.email);

    await openSlotGrid(page, doctor);

    await expect(page.getByRole('button', { name: /^09:00/ })).toBeDisabled();
    await expect(page.getByRole('button', { name: /^09:30/ })).toBeEnabled();
  });

  test('a patient can cancel, and the slot returns to the grid', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'E2E Canceller');
    await signIn(page, patient.email);

    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, '10:00');
    await page.getByRole('button', { name: /confirm booking/i }).click();
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();

    // Cancelling is confirmed through a dialog, never a single click
    // (design.md 3.3), and the reason is required by the contract.
    await page.getByRole('button', { name: /^cancel$/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByLabel('Reason').fill('Something came up');
    await page.getByRole('button', { name: /cancel appointment/i }).click();

    await expect(page.getByText('Cancelled', { exact: true })).toBeVisible();

    // The released slot is bookable again.
    await openSlotGrid(page, doctor);
    await expect(page.getByRole('button', { name: /^10:00/ })).toBeEnabled();
  });

  test('cancelling without a reason is refused before the request is sent', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'E2E No Reason');
    await signIn(page, patient.email);

    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, '10:30');
    await page.getByRole('button', { name: /confirm booking/i }).click();
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();

    await page.getByRole('button', { name: /^cancel$/i }).click();
    await page.getByRole('button', { name: /cancel appointment/i }).click();

    // The dialog stays open with an inline error rather than spending a round
    // trip to be told what the form already knows.
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText(/why you are cancelling/i)).toBeVisible();
  });
});

