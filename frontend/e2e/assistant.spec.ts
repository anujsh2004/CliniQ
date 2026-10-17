import { expect } from '@playwright/test';
import { seedDoctorWithSlots, seedPatient, signIn, test, type SeededDoctor } from './fixtures';

/**
 * The scripted clinic assistant.
 *
 * <p>It is rule-based, not AI, and the tests are mostly about that: it answers
 * a few questions from real data, and declines everything else rather than
 * inventing an answer about a clinic.
 */
test.describe('Clinic assistant', () => {
  let doctor: SeededDoctor;

  test.beforeEach(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  async function openAssistant(page: import('@playwright/test').Page, email: string) {
    await signIn(page, email);
    await page.getByRole('button', { name: /ask a question/i }).click();
    await expect(page.getByRole('dialog', { name: /clinic assistant/i })).toBeVisible();
  }

  async function ask(page: import('@playwright/test').Page, question: string) {
    await page.getByLabel('Ask the clinic assistant').fill(question);
    await page.getByRole('button', { name: 'Send' }).click();
  }

  test('it explains how to book', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Assistant Booker');
    await openAssistant(page, patient.email);

    await ask(page, 'How do I book an appointment?');

    await expect(page.getByText(/choose who you want to see/i)).toBeVisible();
    await expect(page.getByText(/held for five minutes/i)).toBeVisible();
  });

  test("it answers a doctor's hours from real data", async ({ clientPage: page, request }) => {
    // The point of a scripted assistant over a canned script: what it says
    // about this doctor is read from the API, so it cannot be stale or made up.
    const patient = await seedPatient(request, 'Assistant Hours');
    await openAssistant(page, patient.email);

    await ask(page, `What are ${doctor.name}'s hours?`);

    await expect(page.getByText(new RegExp(`${doctor.name} works`, 'i'))).toBeVisible();
    await expect(page.getByText(/9am to 12pm/i)).toBeVisible();
  });

  test('it shows the clinic address with a map', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Assistant Map');
    await openAssistant(page, patient.email);

    await ask(page, 'Where is the clinic?');

    await expect(page.getByText(/MG Road, Bengaluru/i)).toBeVisible();
    await expect(page.getByTitle(/map showing/i)).toBeVisible();
  });

  test('it declines what it does not know rather than inventing', async ({
    clientPage: page,
    request,
  }) => {
    // A made-up answer about a clinic is worse than no answer.
    const patient = await seedPatient(request, 'Assistant Limits');
    await openAssistant(page, patient.email);

    await ask(page, 'Do I need antibiotics for a sore throat?');

    await expect(page.getByText(/only answer a few things/i)).toBeVisible();
  });

  test('a suggestion can be tapped instead of typed', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Assistant Suggest');
    await openAssistant(page, patient.email);

    await page.getByRole('button', { name: 'Where is the clinic?' }).click();

    await expect(page.getByText(/MG Road, Bengaluru/i)).toBeVisible();
  });

  test('it explains the five-minute hold', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Assistant Hold');
    await openAssistant(page, patient.email);

    await ask(page, 'What happens if I do not pay in time?');

    await expect(page.getByText(/goes back to other patients/i)).toBeVisible();
  });

  test('it can be closed again', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'Assistant Close');
    await openAssistant(page, patient.email);

    await page.getByRole('button', { name: /close the clinic assistant/i }).click();

    await expect(page.getByRole('dialog', { name: /clinic assistant/i })).toBeHidden();
    await expect(page.getByRole('button', { name: /ask a question/i })).toBeVisible();
  });
});
