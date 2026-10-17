import { expect } from '@playwright/test';
import { seedDoctorWithSlots, seedPatient, signIn, test } from './fixtures';

/**
 * Where each role lands, and what it can see.
 *
 * <p>Everyone used to arrive at the patient-facing doctor browser, which is the
 * wrong first screen for a doctor: their day is the schedule, not a list of
 * colleagues.
 */
test.describe('Role-aware navigation', () => {
  test('a patient lands on the doctor list', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Landing Patient');
    await signIn(page, patient.email);

    await expect(page.getByRole('heading', { name: 'Doctors' })).toBeVisible();
  });

  test('a doctor lands on their schedule, not the doctor browser', async ({
    clientPage: page,
    request,
  }) => {
    const doctor = await seedDoctorWithSlots(request);
    await signIn(page, doctor.accountEmail);

    await expect(page.getByRole('heading', { name: 'Schedule' })).toBeVisible();
  });

  test('a doctor is not offered the patient doctor browser', async ({
    clientPage: page,
    request,
  }) => {
    const doctor = await seedDoctorWithSlots(request);
    await signIn(page, doctor.accountEmail);

    await expect(page.getByRole('link', { name: 'Doctors', exact: true })).toBeHidden();
    // What a doctor actually needs is there.
    await expect(page.getByRole('link', { name: 'Schedule', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Availability', exact: true })).toBeVisible();
  });

  test('a patient is not offered doctor-only screens', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Nav Patient');
    await signIn(page, patient.email);

    await expect(page.getByRole('link', { name: 'Schedule', exact: true })).toBeHidden();
    await expect(page.getByRole('link', { name: 'Availability', exact: true })).toBeHidden();
    await expect(page.getByRole('link', { name: 'My appointments', exact: true })).toBeVisible();
  });

  test('an unknown route sends each role somewhere useful', async ({
    clientPage: page,
    request,
  }) => {
    const doctor = await seedDoctorWithSlots(request);
    await signIn(page, doctor.accountEmail);

    await page.goto('/this-route-does-not-exist');

    await expect(page.getByRole('heading', { name: 'Schedule' })).toBeVisible();
  });
});
