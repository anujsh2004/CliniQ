import { expect } from '@playwright/test';
import { seedPatient, signIn, test } from './fixtures';

/**
 * The public page.
 *
 * <p>A visitor who is not signed in used to be bounced straight to a login
 * form, which explains nothing to someone who has not heard of the clinic.
 */
test.describe('Public landing page', () => {
  test('a visitor sees what the clinic offers', async ({ clientPage: page }) => {
    await page.goto('/');

    await expect(
      page.getByRole('heading', { name: /book a real appointment/i }),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: 'For patients' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'For clinics' })).toBeVisible();
  });

  test('signing in is reachable from the top right', async ({ clientPage: page }) => {
    // The reason most people arrive is to get in, so the way in is in reach
    // without scrolling.
    await page.goto('/');

    await page.getByRole('link', { name: 'Sign in', exact: true }).first().click();

    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
  });

  test('creating an account is reachable too', async ({ clientPage: page }) => {
    await page.goto('/');

    await page.getByRole('link', { name: 'Create account', exact: true }).first().click();

    await expect(page.getByLabel('Email')).toBeVisible();
  });

  test('a signed-in patient goes to the app, not the sales page', async ({
    clientPage: page,
    request,
  }) => {
    const patient = await seedPatient(request, 'Landing Patient');
    await signIn(page, patient.email);

    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Doctors' })).toBeVisible();
    await expect(page.getByRole('heading', { name: /book a real appointment/i })).toBeHidden();
  });

  test('the booking guarantee is stated plainly', async ({ clientPage: page }) => {
    // It is the one promise the whole product rests on, so it gets its own
    // section rather than a bullet.
    await page.goto('/');

    await expect(page.getByText(/never given to two patients/i)).toBeVisible();
  });
});
