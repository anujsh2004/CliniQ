import { expect, test } from '@playwright/test';
import { bookSlotViaApi, seedDoctorWithSlots, seedPatient, signIn, type SeededDoctor } from './fixtures';

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

  test('a patient registers, books a slot, and sees it in their appointments', async ({
    page,
    request,
  }) => {
    const patient = await seedPatient(request, 'E2E Booker');
    await signIn(page, patient.email);

    // Straight to this run's doctor: the list is paginated and a freshly
    // created doctor is not guaranteed to be on the first page.
    await page.goto(`/doctors/${doctor.doctorId}`);
    await expect(page.getByRole('heading', { name: doctor.name })).toBeVisible();

    // Pick the date the doctor actually works, from the date strip.
    await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();

    // The first free slot.
    const slot = page.getByRole('button', { name: /^09:00/ });
    await expect(slot).toBeEnabled();
    await slot.click();

    // The review step restates the booking before it is committed, because
    // booking creates a payment obligation (design.md 3.6).
    await expect(page.getByText('Confirm your appointment')).toBeVisible();
    await expect(page.getByText(/09:00–09:30/)).toBeVisible();

    await page.getByRole('button', { name: /confirm booking/i }).click();

    // Landing on My appointments with the booking present is the proof.
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();
    await expect(page.getByText(doctor.name)).toBeVisible();
    await expect(page.getByText('Pending payment')).toBeVisible();
  });

  test('a booked slot is no longer offered to the next patient', async ({ page, request }) => {
    // Someone else takes 09:00 through the API; the grid must show it as gone
    // rather than only rejecting it on submit.
    await bookSlotViaApi(request, doctor, '09:00:00');
    const patient = await seedPatient(request, 'E2E Second Looker');
    await signIn(page, patient.email);

    await page.goto(`/doctors/${doctor.doctorId}`);
    await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();

    await expect(page.getByRole('button', { name: /^09:00/ })).toBeDisabled();
    await expect(page.getByRole('button', { name: /^09:30/ })).toBeEnabled();
  });

  test('a patient can cancel, and the slot returns to the grid', async ({ page, request }) => {
    const patient = await seedPatient(request, 'E2E Canceller');
    await signIn(page, patient.email);

    await page.goto(`/doctors/${doctor.doctorId}`);
    await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();
    await page.getByRole('button', { name: /^10:00/ }).click();
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
    await page.goto(`/doctors/${doctor.doctorId}`);
    await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();
    await expect(page.getByRole('button', { name: /^10:00/ })).toBeEnabled();
  });

  test('cancelling without a reason is refused before the request is sent', async ({
    page,
    request,
  }) => {
    const patient = await seedPatient(request, 'E2E No Reason');
    await signIn(page, patient.email);

    await page.goto(`/doctors/${doctor.doctorId}`);
    await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();
    await page.getByRole('button', { name: /^10:30/ }).click();
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

/** The date strip renders dates as "Wed 10 Sep 2026". */
function formatStripLabel(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}
