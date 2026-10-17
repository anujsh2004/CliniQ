import { expect } from '@playwright/test';
import { seedDoctorWithSlots, seedPatient, signIn, test, type SeededDoctor } from './fixtures';

/**
 * The voice assistant page.
 *
 * <p>The microphone itself cannot be driven from a test — there is no real
 * audio device — so these cover everything around it: that the page renders,
 * that all three languages answer from live clinic data, that it refuses what
 * it must refuse, and that it says plainly what it cannot do.
 *
 * <p>Speech recognition and synthesis are verified separately, against the
 * running model, because a browser test cannot speak.
 */
test.describe('Voice assistant', () => {
  let doctor: SeededDoctor;

  test.beforeEach(async ({ request }) => {
    doctor = await seedDoctorWithSlots(request);
  });

  async function openAssistant(page: Parameters<typeof signIn>[0], request: never) {
    const patient = await seedPatient(request, 'Voice Patient');
    await signIn(page, patient.email);
    await page.getByRole('link', { name: 'Voice assistant' }).click();
    await expect(page.getByRole('heading', { name: 'Voice assistant' })).toBeVisible();
  }

  test('a patient can reach it from the sidebar', async ({ clientPage: page, request }) => {
    await openAssistant(page, request as never);

    // The three languages the clinic has actually been tested in.
    await expect(page.getByRole('button', { name: 'हिंदी' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'தமிழ்' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'English' })).toBeVisible();
  });

  test('it says plainly what it cannot do', async ({ clientPage: page, request }) => {
    // A patient must not have to discover by failing that it will not book.
    await openAssistant(page, request as never);

    await expect(page.getByText(/cannot book or cancel an appointment/i)).toBeVisible();
    await expect(page.getByText(/cannot give medical advice/i)).toBeVisible();
  });

  test('it answers a typed Hindi question from live clinic data', async ({
    clientPage: page,
    request,
  }) => {
    await openAssistant(page, request as never);

    await page.getByLabel(/टाइप|type/i).fill(`${doctor.name} का समय क्या है?`);
    await page.getByRole('button', { name: 'भेजें' }).click();

    // Whichever weekday the seeded doctor works, named in Hindi and read from
    // their real availability. All seven are listed deliberately: the seed
    // picks a day relative to today, so pinning a subset makes this fail on
    // some days of the week and pass on others.
    await expect(
      page.getByText(/सोमवार|मंगलवार|बुधवार|गुरुवार|शुक्रवार|शनिवार|रविवार/),
    ).toBeVisible();
    // And the time, in Hindi rather than as a raw API value.
    await expect(page.getByText(/बजे/)).toBeVisible();
  });

  test('it answers in Tamil when asked in Tamil', async ({ clientPage: page, request }) => {
    await openAssistant(page, request as never);

    await page.getByRole('button', { name: 'தமிழ்' }).click();
    await page.getByLabel(/தட்டச்சு|type/i).fill('கிளினிக் எங்கே இருக்கிறது?');
    await page.getByRole('button', { name: 'அனுப்பு' }).click();

    await expect(page.getByText(/MG Road/)).toBeVisible();
  });

  test('it refuses a medical question rather than answering it', async ({
    clientPage: page,
    request,
  }) => {
    // The single most important behaviour on this page.
    await openAssistant(page, request as never);

    await page.getByLabel(/टाइप|type/i).fill('मुझे सीने में दर्द है, क्या करूँ?');
    await page.getByRole('button', { name: 'भेजें' }).click();

    await expect(page.getByText(/चिकित्सा सलाह नहीं/)).toBeVisible();
    await expect(page.getByText(/अस्पताल/)).toBeVisible();
  });

  test('switching language clears the conversation', async ({ clientPage: page, request }) => {
    // Answers are language-specific, so a Hindi answer left on screen under a
    // Tamil heading would read as a bug.
    await openAssistant(page, request as never);

    await page.getByLabel(/टाइप|type/i).fill('क्लिनिक कहाँ है?');
    await page.getByRole('button', { name: 'भेजें' }).click();
    await expect(page.getByText(/क्लिनिक यहाँ है/)).toBeVisible();

    await page.getByRole('button', { name: 'தமிழ்' }).click();
    await expect(page.getByText(/क्लिनिक यहाँ है/)).toBeHidden();
  });

  test('the page shows what it heard, not just the answer', async ({
    clientPage: page,
    request,
  }) => {
    // Recognition mishears English loanwords, so the transcript is shown for
    // the patient to check rather than hidden behind a correct-looking answer.
    await openAssistant(page, request as never);

    await page.getByLabel(/टाइप|type/i).fill('क्लिनिक कहाँ है?');
    await page.getByRole('button', { name: 'भेजें' }).click();

    await expect(page.getByText('आपने कहा')).toBeVisible();
    await expect(page.getByText('“क्लिनिक कहाँ है?”')).toBeVisible();
  });
});
