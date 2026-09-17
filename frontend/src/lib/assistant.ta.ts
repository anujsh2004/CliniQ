import { doctors } from '@/api/endpoints';
import type { DoctorSummary } from '@/types/api';
import { CLINIC_ADDRESS, type AssistantReply } from './assistant';

/**
 * The assistant, in Tamil.
 *
 * <p>A separate module rather than a translation table, because matching an
 * intent in Tamil is not the same problem as matching one in English. Tamil is
 * agglutinative: "appointment", "for the appointment" and "of the appointment"
 * are one word with different endings, so the patterns below match stems
 * rather than whole words, and word order carries far less meaning than it
 * does in English.
 *
 * <p>Patients also code-switch constantly — "நாளைக்கு appointment வேணும்" is
 * ordinary speech, not a mistake — so every intent accepts the English loan
 * word alongside the Tamil one. That is the same reason the speech model is
 * asked for Tamil rather than English: it transcribes the Tamil and leaves the
 * borrowed English words readable.
 *
 * <p>Answers are written in Tamil, not translated at runtime. A clinic's
 * instructions are too important to hand to a machine translator that has
 * never seen this product.
 */

/** Tamil block, U+0B80 to U+0BFF. */
const TAMIL_SCRIPT = /[஀-௿]/;

/** True when the question is written in Tamil, even partly. */
export function isTamil(text: string): boolean {
  return TAMIL_SCRIPT.test(text);
}

const DAY_NAMES: Record<string, string> = {
  MONDAY: 'திங்கள்',
  TUESDAY: 'செவ்வாய்',
  WEDNESDAY: 'புதன்',
  THURSDAY: 'வியாழன்',
  FRIDAY: 'வெள்ளி',
  SATURDAY: 'சனி',
  SUNDAY: 'ஞாயிறு',
};

const DAY_ORDER = Object.keys(DAY_NAMES);

const BOOKING_ANSWER = [
  'அப்பாயிண்ட்மென்ட் பதிவு செய்ய:',
  '1. Doctors பக்கத்தில் மருத்துவரைத் தேர்ந்தெடுக்கவும்.',
  '2. தேதியைத் தேர்வு செய்து, காலியாக உள்ள நேரத்தைத் தேர்ந்தெடுக்கவும்.',
  '3. விவரங்களைச் சரிபார்த்து உறுதிப்படுத்தவும்.',
  '4. உங்கள் நேரம் 5 நிமிடங்களுக்குப் பிடித்து வைக்கப்படும். அதற்குள் பணம் செலுத்தினால் பதிவு உறுதியாகும்.',
].join('\n');

const HOLD_ANSWER = [
  'பதிவு செய்த பிறகு உங்கள் நேரம் 5 நிமிடங்களுக்குப் பிடித்து வைக்கப்படும்.',
  'அந்த நேரத்திற்குள் பணம் செலுத்தினால் அப்பாயிண்ட்மென்ட் உறுதியாகும்.',
  'இல்லையெனில் பதிவு தானாகவே ரத்து செய்யப்பட்டு, அந்த நேரம் மற்ற நோயாளிகளுக்குத் திரும்பக் கிடைக்கும். எனவே நீங்கள் எதையும் இழக்க மாட்டீர்கள்.',
].join('\n');

const CANCEL_ANSWER = [
  'My appointments பக்கத்தைத் திறந்து, உங்கள் பதிவைக் கண்டுபிடித்து Cancel என்பதைத் தேர்வு செய்யவும்.',
  'ஒரு காரணம் கேட்கப்படும். அந்த நேரம் உடனடியாக மற்ற நோயாளிகளுக்கு வழங்கப்படும்.',
].join('\n');

const REMINDER_ANSWER = [
  'அப்பாயிண்ட்மென்ட்டுக்கு ஒரு நாள் முன்பும், ஒரு மணி நேரம் முன்பும் நினைவூட்டல் வரும்.',
  'உங்களுக்கு விருப்பமான நேரத்திலும் கூடுதல் நினைவூட்டல் சேர்க்கலாம்: My appointments பக்கத்தில் Remind me என்பதைத் தேர்வு செய்யவும்.',
  'நினைவூட்டல்கள் Reminders பக்கத்தில் தெரியும்.',
].join('\n');

const GREETING = 'வணக்கம்! அப்பாயிண்ட்மென்ட் பதிவு, மருத்துவர் நேரம், கட்டணம், அல்லது கிளினிக் முகவரி பற்றி என்னிடம் கேட்கலாம்.';

const DECLINE = [
  'மன்னிக்கவும், அது எனக்குத் தெரியவில்லை.',
  'அப்பாயிண்ட்மென்ட் பதிவு, மருத்துவர் நேரம், கட்டணம், நினைவூட்டல், அல்லது கிளினிக் முகவரி பற்றி கேட்கலாம்.',
].join('\n');

/** Tappable follow-ups, in Tamil. */
const SUGGEST = {
  book: 'அப்பாயிண்ட்மென்ட் எப்படி பதிவு செய்வது?',
  where: 'கிளினிக் எங்கே இருக்கிறது?',
  cancel: 'அப்பாயிண்ட்மென்ட்டை எப்படி ரத்து செய்வது?',
  pay: 'பணம் செலுத்தவில்லை என்றால் என்ன ஆகும்?',
};

export const TAMIL_STARTERS = [SUGGEST.book, SUGGEST.where, SUGGEST.pay];

function shortTime(apiTime: string): string {
  const [hours, minutes] = apiTime.split(':').map(Number);
  // Tamil marks the part of the day before the time, rather than after it.
  const period = hours < 12 ? 'காலை' : hours < 16 ? 'மதியம்' : 'மாலை';
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  const clock = minutes === 0 ? `${hour12}` : `${hour12}.${String(minutes).padStart(2, '0')}`;
  return `${period} ${clock} மணி`;
}

/**
 * Finds a doctor named in a Tamil question.
 *
 * <p>Doctor names are stored in English, and a Tamil speaker will usually type
 * or say the surname in English too ("டாக்டர் Sharma" or plain "Sharma"), so
 * the English name is matched inside the Tamil sentence.
 */
function doctorsNamedIn(question: string, all: DoctorSummary[]): DoctorSummary[] {
  const asked = question.toLowerCase();
  return all.filter((doctor) => {
    const full = doctor.name.toLowerCase();
    const withoutTitle = full.replace(/^dr\.?\s*/, '');
    const surname = withoutTitle.split(/\s+/).pop() ?? '';
    return (
      asked.includes(withoutTitle) || (surname.length > 3 && asked.includes(surname))
    );
  });
}

async function findDoctors(asked: string): Promise<{ matches: DoctorSummary[]; sample: string[] }> {
  const PAGE = 100;
  const MAX_PAGES = 10;
  const sample: string[] = [];

  for (let page = 0; page < MAX_PAGES; page += 1) {
    const result = await doctors.list(page, PAGE);
    if (sample.length === 0) {
      sample.push(...result.content.slice(0, 6).map((doctor) => doctor.name));
    }
    const matches = doctorsNamedIn(asked, result.content);
    if (matches.length > 0) {
      return { matches, sample };
    }
    if (page + 1 >= result.totalPages) {
      break;
    }
  }
  return { matches: [], sample };
}

async function availabilityAnswer(doctor: DoctorSummary): Promise<string> {
  const windows = await doctors.availability(doctor.doctorId);
  if (windows.length === 0) {
    return `${doctor.name} அவர்களுக்கு இன்னும் நேரம் பதிவு செய்யப்படவில்லை. தற்போது பதிவு செய்ய முடியாது.`;
  }
  const lines = [...windows]
    .sort((a, b) => DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek))
    .map(
      (window) =>
        `· ${DAY_NAMES[window.dayOfWeek] ?? window.dayOfWeek}: ${shortTime(
          window.startTime,
        )} முதல் ${shortTime(window.endTime)} வரை (${window.slotDurationMinutes} நிமிட சந்திப்பு)`,
    );
  return [`${doctor.name} அவர்களின் நேரம்:`, ...lines].join('\n');
}

/**
 * Answers a Tamil question, or declines it.
 *
 * <p>Intent order matters exactly as it does in English: a question about
 * money is about the hold even when it also contains the word for "time",
 * because "நேரத்திற்குள் பணம்" ("money within the time") would otherwise be
 * pulled into the opening-hours branch.
 */
export async function answerTamil(question: string): Promise<AssistantReply> {
  const asked = question.trim();

  if (!asked) {
    return { text: GREETING, suggestions: TAMIL_STARTERS };
  }

  if (/வணக்கம்|ஹலோ|hello|hi\b/i.test(asked)) {
    return { text: GREETING, suggestions: TAMIL_STARTERS };
  }

  if (/நன்றி|thanks|thank you/i.test(asked)) {
    return { text: 'நன்றி! வேறு ஏதேனும் கேட்க வேண்டுமா?', suggestions: TAMIL_STARTERS };
  }

  // Where the clinic is.
  if (/எங்கே|எங்க\b|முகவரி|இடம்|வழி|எப்படி வர|address|map/i.test(asked)) {
    return {
      text: `கிளினிக் இங்கே உள்ளது: ${CLINIC_ADDRESS.line}.`,
      map: true,
      suggestions: [SUGGEST.book],
    };
  }

  // Money first, for the reason in the doc comment above.
  if (/பணம்|கட்டணம்|செலுத்த|பே\b|payment|pay\b|பிடித்து|ஹோல்ட்|hold|5 நிமிட|ஐந்து நிமிட/i.test(asked)) {
    return { text: HOLD_ANSWER, suggestions: [SUGGEST.cancel] };
  }

  const wantsHours = /நேரம்|நேரங்கள்|எப்போது|எப்பொழுது|டைமிங்|timing|கிடைப்ப/i.test(asked);
  if (wantsHours || /டாக்டர்|மருத்துவர்|doctor|dr\.?\b/i.test(asked)) {
    const { matches, sample } = await findDoctors(asked);

    if (matches.length === 1) {
      return {
        text: await availabilityAnswer(matches[0]),
        suggestions: [SUGGEST.book, SUGGEST.where],
      };
    }
    if (matches.length > 1) {
      return {
        text: `ஒன்றுக்கு மேற்பட்ட மருத்துவர்கள் கிடைத்தனர்: ${matches
          .map((doctor) => doctor.name)
          .join(', ')}. யாரைக் குறிப்பிடுகிறீர்கள்?`,
      };
    }
    if (wantsHours) {
      return {
        text:
          sample.length === 0
            ? 'இன்னும் எந்த மருத்துவரும் பதிவு செய்யப்படவில்லை.'
            : `எந்த மருத்துவர்? நான் இவர்களைப் பார்க்க முடியும்: ${sample.join(', ')}.`,
        suggestions: sample.slice(0, 3).map((name) => `${name} அவர்களின் நேரம் என்ன?`),
      };
    }
  }

  if (/ரத்து|கேன்சல்|cancel|மாற்ற|ரீஷெட்யூல்/i.test(asked)) {
    return { text: CANCEL_ANSWER };
  }

  if (/நினைவூட்டல்|ரிமைண்டர்|reminder|ஞாபக/i.test(asked)) {
    return { text: REMINDER_ANSWER };
  }

  if (/அப்பாயிண்ட்மென்ட்|appointment|பதிவு|முன்பதிவு|புக்|book|சந்திப்பு|ஸ்லாட்|slot/i.test(asked)) {
    return {
      text: BOOKING_ANSWER,
      suggestions: [SUGGEST.where, SUGGEST.pay],
    };
  }

  return { text: DECLINE, suggestions: TAMIL_STARTERS };
}
