import { doctors } from '@/api/endpoints';
import type { DoctorSummary } from '@/types/api';

/**
 * A scripted assistant, not an AI one.
 *
 * <p>It matches a question against a small set of intents and answers from the
 * real API, so what it says about a doctor's hours is true rather than
 * plausible. Anything it does not recognise it declines, which is the honest
 * behaviour for a demo: a made-up answer about clinic hours is worse than no
 * answer.
 *
 * <p>The AI layer in Phase 7 replaces this with retrieval over a real knowledge
 * base. Until then nothing here pretends to be more than a lookup.
 */

/** The clinic's address, for the map. A placeholder until a real one exists. */
export const CLINIC_ADDRESS = {
  name: 'Cliniva Clinic',
  line: '12 MG Road, Bengaluru, Karnataka 560001',
  latitude: 12.9752,
  longitude: 77.6058,
};

export interface AssistantReply {
  text: string;
  /** Rendered as a map beneath the reply. */
  map?: boolean;
  /** Offered as tappable follow-ups. */
  suggestions?: string[];
}

const DAY_ORDER = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

function titleCase(day: string): string {
  return day.charAt(0) + day.slice(1).toLowerCase();
}

function shortTime(apiTime: string): string {
  const [hours, minutes] = apiTime.split(':').map(Number);
  const suffix = hours >= 12 ? 'pm' : 'am';
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  return minutes === 0 ? `${hour12}${suffix}` : `${hour12}:${String(minutes).padStart(2, '0')}${suffix}`;
}

const BOOKING_ANSWER = [
  'To book an appointment:',
  '1. Open Doctors and choose who you want to see.',
  '2. Pick a date from the strip, then a free time slot.',
  '3. Check the details on the review step and confirm.',
  '4. Your slot is held for five minutes while you pay - pay within that time and the booking is confirmed.',
].join('\n');

const HOLD_ANSWER = [
  'Your slot is held for five minutes after you book.',
  'Pay in that time and the appointment is confirmed.',
  'If you do not, the booking is cancelled automatically and the slot goes back to other patients - so nothing is lost by changing your mind.',
].join('\n');

const CANCEL_ANSWER = [
  'Open My appointments, find the booking, and choose Cancel.',
  'You will be asked for a reason, and the slot is offered to other patients straight away.',
].join('\n');

const REMINDER_ANSWER = [
  'The clinic reminds you a day before and an hour before your appointment.',
  'You can add your own reminder too: open My appointments and choose Remind me.',
  'Reminders appear under Reminders in the menu.',
].join('\n');

/** Every doctor whose name appears in the question. */
function doctorsNamedIn(question: string, all: DoctorSummary[]): DoctorSummary[] {
  const asked = question.toLowerCase();
  return all.filter((doctor) => {
    const full = doctor.name.toLowerCase();
    const withoutTitle = full.replace(/^dr\.?\s*/, '');
    const surname = withoutTitle.split(/\s+/).pop() ?? '';
    return (
      asked.includes(full) ||
      asked.includes(withoutTitle) ||
      (surname.length > 3 && asked.includes(surname))
    );
  });
}

/**
 * Finds doctors by name, paging until they are found.
 *
 * <p>A single page would quietly miss anyone further down the list, and
 * "I don't know that doctor" about a doctor who exists is worse than slow.
 */
async function findDoctorsByName(asked: string): Promise<{
  matches: DoctorSummary[];
  sample: string[];
}> {
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
    return `${doctor.name} has no hours listed yet, so there is nothing to book at the moment.`;
  }
  const lines = [...windows]
    .sort((a, b) => DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek))
    .map(
      (window) =>
        `· ${titleCase(window.dayOfWeek)}: ${shortTime(window.startTime)} to ${shortTime(
          window.endTime,
        )} (${window.slotDurationMinutes} minute appointments)`,
    );
  return [`${doctor.name} works:`, ...lines].join('\n');
}

/**
 * Answers a question, or declines it.
 *
 * <p>Intent order matters: a question naming a doctor is about that doctor even
 * if it also contains the word "book".
 */
export async function answer(question: string): Promise<AssistantReply> {
  const asked = question.toLowerCase().trim();

  if (!asked) {
    return { text: 'Ask me about booking, a doctor’s hours, or where the clinic is.' };
  }

  if (/\b(where|address|located|location|map|reach|direction)/.test(asked)) {
    return {
      text: `${CLINIC_ADDRESS.name} is at ${CLINIC_ADDRESS.line}.`,
      map: true,
      suggestions: ['How do I book an appointment?'],
    };
  }

  // Money questions come first. "What happens if I do not pay in time?" is
  // about the hold, not about anyone's opening hours, and the word "time"
  // would otherwise pull it into the doctor branch.
  if (/(hold|five minute|5 minute|expire|pay|payment|paid)/.test(asked)) {
    return { text: HOLD_ANSWER, suggestions: ['How do I cancel an appointment?'] };
  }

  // Doctor-specific questions, answered from live data.
  // Deliberately not matching a bare "time": "if I do not pay in time" is a
  // question about the hold, not about anyone's opening hours.
  const wantsHours =
    /\b(hour|timing|available|availability|schedule|open|works?|working)\b/.test(asked) ||
    /\bwhen (is|are|does|do)\b/.test(asked);
  if (wantsHours || /\bdr\.?\b|doctor/.test(asked)) {
    const { matches: named, sample } = await findDoctorsByName(asked);

    if (named.length === 1) {
      return {
        text: await availabilityAnswer(named[0]),
        suggestions: ['How do I book an appointment?', 'Where is the clinic?'],
      };
    }
    if (named.length > 1) {
      return {
        text: `I found more than one match: ${named
          .map((doctor) => doctor.name)
          .join(', ')}. Which one did you mean?`,
      };
    }
    if (wantsHours) {
      const names = sample;
      return {
        text:
          names.length === 0
            ? 'No doctors are listed yet.'
            : `Which doctor did you mean? I can look up: ${names.join(', ')}.`,
        suggestions: names.slice(0, 3).map((name) => `What are ${name}'s hours?`),
      };
    }
  }

  if (/\b(hold|five minute|5 minute|expire|pay|payment)/.test(asked)) {
    return { text: HOLD_ANSWER, suggestions: ['How do I cancel an appointment?'] };
  }

  if (/\b(cancel|reschedule|change my appointment)/.test(asked)) {
    return { text: CANCEL_ANSWER };
  }

  if (/\b(remind|reminder|notify|notification)/.test(asked)) {
    return { text: REMINDER_ANSWER };
  }

  if (/\b(book|appointment|slot|schedule)/.test(asked)) {
    return {
      text: BOOKING_ANSWER,
      suggestions: ['Where is the clinic?', 'What happens if I do not pay in time?'],
    };
  }

  // Declining beats inventing. This is a scripted demo, and saying so keeps it
  // honest about what it can do.
  return {
    text: 'I can only answer a few things at the moment: how to book, a doctor’s hours, and where the clinic is.',
    suggestions: ['How do I book an appointment?', 'Where is the clinic?'],
  };
}
