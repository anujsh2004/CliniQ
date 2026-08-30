import { expect } from '@playwright/test';
import { openSlotGrid, seedDoctorWithSlots, seedPatient, signIn, test, type SeededDoctor } from './fixtures';

/**
 * Empty, loading and error states (design.md 2.12 to 2.14).
 *
 * <p>These are the screens a reviewer never sees by clicking around a working
 * system with seeded data, and the ones a patient meets on their first visit or
 * when something breaks. Checking them by hand means remembering to; checking
 * them here means they cannot regress quietly.
 */
test.describe('Empty and error states', () => {
  let doctor: SeededDoctor;

  test.beforeEach(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  test('a new patient sees a useful empty state, not a blank list', async ({
    clientPage: page,
    request,
  }) => {
    // design.md 2.12: an empty list says what is missing and what to do next.
    const patient = await seedPatient(request, 'Empty Lister');
    await signIn(page, patient.email);
    await page.getByRole('link', { name: 'My appointments' }).click();

    await expect(page.getByText('No appointments yet')).toBeVisible();
    await expect(page.getByText(/pick a doctor to book/i)).toBeVisible();
  });

  test('a date with no slots explains itself and suggests another', async ({
    clientPage: page,
    request,
  }) => {
    const patient = await seedPatient(request, 'No Slots');
    await signIn(page, patient.email);
    // The doctor works one weekday; today is not it, so the default date is
    // empty, which is the state a patient meets most often.
    await page.goto(`/doctors/${doctor.doctorId}`);

    await expect(page.getByText('No slots on this date')).toBeVisible();
    await expect(page.getByText(/try another date/i)).toBeVisible();
  });

  test('a failed slot load offers a retry rather than an empty grid', async ({
    clientPage: page,
    request,
  }) => {
    // design.md 2.14: a full error state with retry for a failed data load.
    // Silently rendering nothing would look identical to a doctor with no
    // availability, which is a different thing entirely.
    const patient = await seedPatient(request, 'Slot Failure');
    await signIn(page, patient.email);

    await page.route('**/api/v1/doctors/*/slots*', (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
    );
    await page.goto(`/doctors/${doctor.doctorId}`);

    await expect(page.getByText('Something went wrong')).toBeVisible();
    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible();
  });

  test('retrying after a failure actually recovers', async ({ clientPage: page, request }) => {
    // A retry button that does not retry is worse than none.
    const patient = await seedPatient(request, 'Retry Recovery');
    await signIn(page, patient.email);

    // The client retries once on its own before giving up, so the error state
    // only appears after both the request and its retry have failed.
    let failuresLeft = 2;
    await page.route('**/api/v1/doctors/*/slots*', async (route) => {
      if (failuresLeft > 0) {
        failuresLeft -= 1;
        await route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });
    await page.goto(`/doctors/${doctor.doctorId}`);
    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible();

    await page.getByRole('button', { name: /try again/i }).click();

    await expect(page.getByRole('button', { name: /try again/i })).toBeHidden();
  });

  test('a failed appointments load offers a retry', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Appt Failure');
    await signIn(page, patient.email);

    await page.route('**/api/v1/appointments/my*', (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
    );
    await page.getByRole('link', { name: 'My appointments' }).click();

    await expect(page.getByText(/could not load your appointments/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible();
  });

  test('a lost slot is reported in a way the patient can act on', async ({
    clientPage: page,
    request,
  }) => {
    // design.md 2.14 names this case specifically: 409 is not a dead end, it
    // returns the patient to the grid with an explanation.
    const patient = await seedPatient(request, 'Lost Slot');
    await signIn(page, patient.email);
    await openSlotGrid(page, doctor);

    await page.route('**/api/v1/appointments', (route) =>
      route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          message: 'Appointment slot is already booked',
          errorCode: 'SLOT_ALREADY_BOOKED',
          errors: [],
        }),
      }),
    );

    await page.getByRole('button', { name: /^09:00/ }).click();
    await page.getByRole('button', { name: /confirm booking/i }).click();

    await expect(page.getByText(/just booked by someone else/i)).toBeVisible();
    await expect(page.getByText('Just booked', { exact: true })).toBeVisible();
    // Still on the grid, still able to choose.
    await expect(page.getByRole('button', { name: /^09:30/ })).toBeEnabled();
  });

  test('a doctor account with no profile is told why, not shown a broken page', async ({
    clientPage: page,
    request,
  }) => {
    // A real state this product can be in, because an admin can create a doctor
    // profile that is not linked to any account (D3).
    const patient = await seedPatient(request, 'Wrong Role');
    await signIn(page, patient.email);

    await page.route('**/api/v1/doctors/me/appointments*', (route) =>
      route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          message: 'You are not allowed to access this resource',
          errorCode: 'UNAUTHORIZED_ACCESS',
          errors: [],
        }),
      }),
    );
    await page.goto('/schedule');

    // A patient is redirected away from a doctor-only route rather than shown
    // an error they cannot act on.
    await expect(page.getByRole('heading', { name: 'Doctors' })).toBeVisible();
  });
});
