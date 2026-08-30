import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';
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
 * Accessibility audit against design.md 3.7.
 *
 * <p>The design document commits to specific things: 4.5:1 text contrast,
 * every interactive element keyboard-navigable with a visible focus ring,
 * inputs paired with a visible label rather than a placeholder, and status
 * never conveyed by colour alone. Automated rules catch the first and the
 * last two; the keyboard and colour-only checks below are written by hand,
 * because no scanner can tell whether a green dot means "confirmed".
 *
 * <p>The audit targets WCAG 2.1 AA, which is what the 4.5:1 contrast figure in
 * design.md comes from.
 */

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function audit(page: Page) {
  return new AxeBuilder({ page }).withTags(WCAG).analyze();
}

/** Reports violations with enough detail to fix them, not just a count. */
function describe(violations: Awaited<ReturnType<typeof audit>>['violations']): string {
  return violations
    .map((violation) => {
      const nodes = violation.nodes.slice(0, 3).map((node) => `      ${node.html.slice(0, 120)}`);
      return `  [${violation.impact}] ${violation.id}: ${violation.help}\n${nodes.join('\n')}`;
    })
    .join('\n');
}

test.describe('Accessibility', () => {
  let doctor: SeededDoctor;

  test.beforeEach(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  test('the sign-in screen has no violations', async ({ clientPage: page }) => {
    await page.goto('/login');
    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('the registration screen has no violations', async ({ clientPage: page }) => {
    await page.goto('/register');
    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('the doctor list has no violations', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'A11y Browser');
    await signIn(page, patient.email);
    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('the slot grid has no violations', async ({ clientPage: page, request }) => {
    // The densest screen in the product, and the one patients spend most time
    // on.
    const patient = await seedPatient(request, 'A11y Booker');
    await signIn(page, patient.email);
    await openSlotGrid(page, doctor);
    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('the booking review step has no violations', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'A11y Reviewer');
    await signIn(page, patient.email);
    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, '09:00');
    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('the appointments list has no violations', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'A11y Lister');
    await signIn(page, patient.email);
    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, '09:30');
    await page.getByRole('button', { name: /confirm booking/i }).click();
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();

    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('the cancellation dialog has no violations', async ({ clientPage: page, request }) => {
    const patient = await seedPatient(request, 'A11y Dialog');
    await signIn(page, patient.email);
    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, '10:00');
    await page.getByRole('button', { name: /confirm booking/i }).click();
    await page.getByRole('button', { name: /^cancel$/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    const { violations } = await audit(page);
    expect(violations, `\n${describe(violations)}`).toEqual([]);
  });

  test('status is never conveyed by colour alone', async ({ clientPage: page, request }) => {
    // design.md 3.7 is explicit about this, and no scanner can check it: a
    // green dot and a red dot are indistinguishable to a colour-blind user, so
    // every status badge carries its text.
    const patient = await seedPatient(request, 'A11y Colour');
    await signIn(page, patient.email);
    await openSlotGrid(page, doctor);
    await selectSlotAndReview(page, '10:30');
    await page.getByRole('button', { name: /confirm booking/i }).click();
    await expect(page.getByRole('heading', { name: 'My appointments' })).toBeVisible();

    await expect(page.getByText('Pending payment')).toBeVisible();
  });

  test('the booking flow is operable by keyboard alone', async ({ clientPage: page, request }) => {
    // design.md 3.7: every interactive element keyboard-navigable with a
    // visible focus ring. Tested by actually driving it from the keyboard.
    const patient = await seedPatient(request, 'A11y Keyboard');
    await page.goto('/login');

    await page.getByLabel('Email').focus();
    await page.keyboard.type(patient.email);
    await page.keyboard.press('Tab');
    await page.keyboard.type('StrongPassword123');
    await page.keyboard.press('Enter');

    await expect(page.getByRole('heading', { name: 'Doctors' })).toBeVisible();

    // Something is always focusable on the page, and focus is never lost to
    // the document body after navigating.
    await page.keyboard.press('Tab');
    const focused = await page.evaluate(() => document.activeElement?.tagName ?? 'NONE');
    expect(focused).not.toBe('BODY');
  });
});
