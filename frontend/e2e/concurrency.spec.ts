import { expect, test, type Page } from '@playwright/test';
import { seedDoctorWithSlots, seedPatient, signIn, type SeededDoctor } from './fixtures';

/**
 * The product's one non-negotiable guarantee, observed the way a patient would
 * experience it (product-description.md G1, NFR-1, and the acceptance criterion
 * in section 21).
 *
 * <p>The backend has its own concurrency test against a real PostgreSQL. This
 * one exists because passing that test is not the same as the patient being
 * treated well: the loser of the race has to be told what happened and given a
 * way forward, not left staring at a dead button.
 */
test.describe('Two patients racing for one slot', () => {
  let doctor: SeededDoctor;

  test.beforeAll(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  test('exactly one booking succeeds, and the loser is told why', async ({ browser, request }) => {
    const [first, second] = await Promise.all([
      seedPatient(request, 'E2E Racer One'),
      seedPatient(request, 'E2E Racer Two'),
    ]);

    // Two independent browser contexts: separate cookies, separate storage,
    // genuinely two clients rather than two tabs sharing a session.
    const [contextA, contextB] = await Promise.all([
      browser.newContext(),
      browser.newContext(),
    ]);
    const [pageA, pageB] = await Promise.all([contextA.newPage(), contextB.newPage()]);

    try {
      await Promise.all([signIn(pageA, first.email), signIn(pageB, second.email)]);

      // Both reach the review step for the same slot, which is exactly the
      // situation the guarantee is about: both saw it as available.
      await Promise.all([
        selectSlot(pageA, doctor, '09:00'),
        selectSlot(pageB, doctor, '09:00'),
      ]);

      // Then commit at the same instant.
      await Promise.all([
        pageA.getByRole('button', { name: /confirm booking/i }).click(),
        pageB.getByRole('button', { name: /confirm booking/i }).click(),
      ]);

      const outcomes = await Promise.all([outcomeOf(pageA), outcomeOf(pageB)]);

      expect(outcomes.filter((o) => o === 'booked')).toHaveLength(1);
      expect(outcomes.filter((o) => o === 'rejected')).toHaveLength(1);

      // The loser is not left stuck: the slot grid is back, the lost slot is
      // marked, and another time can be chosen immediately.
      const loser = (await outcomeOf(pageA)) === 'rejected' ? pageA : pageB;
      await expect(loser.getByText(/just booked by someone else/i)).toBeVisible();
      await expect(loser.getByText('Just booked', { exact: true })).toBeVisible();
      await expect(loser.getByRole('button', { name: /^09:30/ })).toBeEnabled();
    } finally {
      await Promise.all([contextA.close(), contextB.close()]);
    }
  });
});

async function selectSlot(page: Page, doctor: SeededDoctor, time: string): Promise<void> {
  await page.goto(`/doctors/${doctor.doctorId}`);
  await page.getByRole('tab', { name: formatStripLabel(doctor.date) }).click();
  await page.getByRole('button', { name: new RegExp(`^${time}`) }).click();
  await expect(page.getByText('Confirm your appointment')).toBeVisible();
}

/** Which side of the race this page ended up on. */
async function outcomeOf(page: Page): Promise<'booked' | 'rejected' | 'unknown'> {
  const booked = page.getByRole('heading', { name: 'My appointments' });
  const rejected = page.getByText(/just booked by someone else/i);

  try {
    await expect(booked.or(rejected).first()).toBeVisible({ timeout: 15_000 });
  } catch {
    return 'unknown';
  }
  return (await booked.isVisible()) ? 'booked' : 'rejected';
}

function formatStripLabel(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}
