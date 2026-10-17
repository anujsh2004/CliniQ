import { doctors } from '@/api/endpoints';
import type { DoctorSummary } from '@/types/api';
import { CLINIC_ADDRESS, type AssistantReply } from './assistant';
import { matchDoctors } from './doctorNames';

/**
 * The assistant, in Hindi.
 *
 * <p>Built the same way as the Tamil one and for the same reason: matching an
 * intent in Hindi is a different problem from matching one in English, so the
 * patterns live here rather than in a translation table. Hindi word order is
 * relatively free and the verb lands at the end, so these match content words
 * wherever they appear rather than relying on position.
 *
 * <p>Patients code-switch constantly — "कल appointment चाहिए" is ordinary
 * speech, not a mistake — so every intent accepts the English loan word beside
 * the Hindi one. Doctor names are stored in English and spoken in English even
 * mid-Hindi, which is why the name matching below looks for Latin script
 * inside a Devanagari sentence.
 *
 * <p>Answers are written in Hindi, not machine-translated at runtime. A
 * clinic's instructions are too important to hand to a translator that has
 * never seen this product.
 */

/** Devanagari block, U+0900 to U+097F. */
const DEVANAGARI = /[ऀ-ॿ]/;

/** True when the question is written in Hindi, even partly. */
export function isHindi(text: string): boolean {
  return DEVANAGARI.test(text);
}

const DAY_NAMES: Record<string, string> = {
  MONDAY: 'सोमवार',
  TUESDAY: 'मंगलवार',
  WEDNESDAY: 'बुधवार',
  THURSDAY: 'गुरुवार',
  FRIDAY: 'शुक्रवार',
  SATURDAY: 'शनिवार',
  SUNDAY: 'रविवार',
};

const DAY_ORDER = Object.keys(DAY_NAMES);

const BOOKING_ANSWER = [
  'अपॉइंटमेंट बुक करने के लिए:',
  '1. Doctors पेज पर जाकर डॉक्टर चुनें।',
  '2. तारीख चुनें, फिर खाली समय चुनें।',
  '3. विवरण जाँचकर पुष्टि करें।',
  '4. आपका समय 5 मिनट के लिए रोक दिया जाएगा। उतने समय में भुगतान करने पर बुकिंग पक्की हो जाती है।',
].join('\n');

const HOLD_ANSWER = [
  'बुक करने के बाद आपका समय 5 मिनट के लिए रोक दिया जाता है।',
  'उस समय के भीतर भुगतान करने पर अपॉइंटमेंट पक्की हो जाती है।',
  'नहीं तो बुकिंग अपने आप रद्द हो जाती है और वह समय दूसरे मरीज़ों को वापस मिल जाता है। इसलिए आपका कुछ नहीं जाता।',
].join('\n');

const CANCEL_ANSWER = [
  'My appointments पेज खोलें, अपनी बुकिंग ढूंढें और Cancel चुनें।',
  'एक कारण पूछा जाएगा। वह समय तुरंत दूसरे मरीज़ों को दे दिया जाता है।',
].join('\n');

const REMINDER_ANSWER = [
  'अपॉइंटमेंट से एक दिन पहले और एक घंटे पहले याद दिलाया जाता है।',
  'आप अपनी पसंद के समय पर अतिरिक्त रिमाइंडर भी जोड़ सकते हैं: My appointments में Remind me चुनें।',
  'सभी रिमाइंडर Reminders पेज पर दिखते हैं।',
].join('\n');

const GREETING =
  'नमस्ते! आप मुझसे अपॉइंटमेंट बुकिंग, डॉक्टर का समय, शुल्क, या क्लिनिक के पते के बारे में पूछ सकते हैं।';

const DECLINE = [
  'माफ़ कीजिए, यह मुझे नहीं पता।',
  'आप अपॉइंटमेंट बुकिंग, डॉक्टर का समय, भुगतान, रिमाइंडर, या क्लिनिक के पते के बारे में पूछ सकते हैं।',
].join('\n');

/**
 * Anything that sounds like a symptom is refused, not answered.
 *
 * <p>Checked before every other intent. A patient describing chest pain must
 * never get a scripted reply about opening hours, and must never get anything
 * resembling advice.
 */
const MEDICAL = /दर्द|बुखार|बीमार|तबीयत|इलाज|दवा|दवाई|लक्षण|खांसी|चक्कर|साँस|सांस|उल्टी|घबराहट/;

const MEDICAL_ANSWER = [
  'मैं चिकित्सा सलाह नहीं दे सकता।',
  'अगर आपकी तबीयत ठीक नहीं है तो कृपया सीधे क्लिनिक से संपर्क करें, या आपात स्थिति में तुरंत नज़दीकी अस्पताल जाएँ।',
  'मैं अपॉइंटमेंट बुक करने में आपकी मदद कर सकता हूँ।',
].join('\n');

const SUGGEST = {
  book: 'अपॉइंटमेंट कैसे बुक करें?',
  where: 'क्लिनिक कहाँ है?',
  cancel: 'अपॉइंटमेंट कैसे रद्द करें?',
  pay: 'अगर भुगतान न करूँ तो क्या होगा?',
  doctors: 'कौन से डॉक्टर उपलब्ध हैं?',
};

export const HINDI_STARTERS = [SUGGEST.doctors, SUGGEST.book, SUGGEST.where];

function shortTime(apiTime: string): string {
  const [hours, minutes] = apiTime.split(':').map(Number);
  // Hindi names the part of the day before the time, as Tamil does.
  const period = hours < 12 ? 'सुबह' : hours < 16 ? 'दोपहर' : 'शाम';
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  const clock = minutes === 0 ? `${hour12}` : `${hour12}:${String(minutes).padStart(2, '0')}`;
  return `${period} ${clock} बजे`;
}

async function findDoctors(asked: string): Promise<{ matches: DoctorSummary[]; all: DoctorSummary[] }> {
  const PAGE = 100;
  const MAX_PAGES = 10;
  const all: DoctorSummary[] = [];

  for (let page = 0; page < MAX_PAGES; page += 1) {
    const result = await doctors.list(page, PAGE);
    all.push(...result.content);
    const matches = matchDoctors(asked, result.content);
    if (matches.length > 0) {
      return { matches, all };
    }
    if (page + 1 >= result.totalPages) {
      break;
    }
  }
  return { matches: [], all };
}

async function availabilityAnswer(doctor: DoctorSummary): Promise<string> {
  const windows = await doctors.availability(doctor.doctorId);
  if (windows.length === 0) {
    return `${doctor.name} का अभी कोई समय तय नहीं है, इसलिए फ़िलहाल बुकिंग नहीं हो सकती।`;
  }
  const lines = [...windows]
    .sort((a, b) => DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek))
    .map(
      (window) =>
        `· ${DAY_NAMES[window.dayOfWeek] ?? window.dayOfWeek}: ${shortTime(
          window.startTime,
        )} से ${shortTime(window.endTime)} तक (${window.slotDurationMinutes} मिनट की मुलाक़ात)`,
    );
  return [`${doctor.name} का समय:`, ...lines].join('\n');
}

/** The doctor list, for "which doctors are available?". */
function doctorListAnswer(all: DoctorSummary[]): string {
  if (all.length === 0) {
    return 'अभी कोई डॉक्टर सूचीबद्ध नहीं है।';
  }
  const lines = all
    .slice(0, 8)
    .map((doctor) => `· ${doctor.name} — ${doctor.specialization}, शुल्क ₹${doctor.consultationFee}`);
  return ['हमारे डॉक्टर:', ...lines].join('\n');
}

/**
 * Answers a Hindi question, or declines it.
 *
 * <p>Intent order matters exactly as it does in the other two languages, with
 * one addition: medical questions are caught first, before anything else can
 * match them.
 */
export async function answerHindi(question: string): Promise<AssistantReply> {
  const asked = question.trim();

  if (!asked) {
    return { text: GREETING, suggestions: HINDI_STARTERS };
  }

  // Refused before any other intent can claim it.
  if (MEDICAL.test(asked)) {
    return { text: MEDICAL_ANSWER, suggestions: [SUGGEST.book, SUGGEST.doctors] };
  }

  if (/नमस्ते|नमस्कार|हैलो|हेलो|hello|hi\b/i.test(asked)) {
    return { text: GREETING, suggestions: HINDI_STARTERS };
  }

  if (/धन्यवाद|शुक्रिया|thanks|thank you/i.test(asked)) {
    return { text: 'धन्यवाद! कुछ और पूछना चाहेंगे?', suggestions: HINDI_STARTERS };
  }

  if (/कहाँ|कहां|पता|जगह|रास्ता|कैसे पहुँच|कैसे पहुंच|address|map/i.test(asked)) {
    return {
      text: `क्लिनिक यहाँ है: ${CLINIC_ADDRESS.line}.`,
      map: true,
      suggestions: [SUGGEST.book],
    };
  }

  // Money before hours: "समय पर भुगतान" contains the word for "time" and
  // would otherwise be pulled into the opening-hours branch.
  if (/पैसे|पैसा|भुगतान|शुल्क|फीस|payment|pay\b|रोक|होल्ड|hold|5 मिनट|पाँच मिनट|पांच मिनट/i.test(asked)) {
    return { text: HOLD_ANSWER, suggestions: [SUGGEST.cancel] };
  }

  // The whole list, before the named-doctor branch tries to find one name.
  if (/कौन से डॉक्टर|कौन कौन|सभी डॉक्टर|डॉक्टरों की सूची|कौन डॉक्टर|list of doctors/i.test(asked)) {
    const { all } = await findDoctors('');
    return { text: doctorListAnswer(all), suggestions: [SUGGEST.book] };
  }

  const wantsHours = /समय|कब|टाइमिंग|timing|उपलब्ध|खुला|बैठते/i.test(asked);
  if (wantsHours || /डॉक्टर|डाक्टर|doctor|dr\.?\b/i.test(asked)) {
    const { matches, all } = await findDoctors(asked);

    if (matches.length === 1) {
      return {
        text: await availabilityAnswer(matches[0]),
        suggestions: [SUGGEST.book, SUGGEST.where],
      };
    }
    if (matches.length > 1) {
      return {
        text: `एक से ज़्यादा डॉक्टर मिले: ${matches
          .map((doctor) => doctor.name)
          .join(', ')}. आप किसके बारे में पूछ रहे हैं?`,
      };
    }
    if (wantsHours) {
      return {
        text:
          all.length === 0
            ? 'अभी कोई डॉक्टर सूचीबद्ध नहीं है।'
            : `किस डॉक्टर के बारे में? मैं इनके बारे में बता सकता हूँ: ${all
                .slice(0, 6)
                .map((doctor) => doctor.name)
                .join(', ')}.`,
        suggestions: all.slice(0, 3).map((doctor) => `${doctor.name} का समय क्या है?`),
      };
    }
    return { text: doctorListAnswer(all), suggestions: [SUGGEST.book] };
  }

  if (/रद्द|कैंसल|cancel|बदल/i.test(asked)) {
    return { text: CANCEL_ANSWER };
  }

  if (/रिमाइंडर|याद|reminder|सूचना/i.test(asked)) {
    return { text: REMINDER_ANSWER };
  }

  if (/अपॉइंटमेंट|अपाइंटमेंट|appointment|बुक|book|मुलाक़ात|मुलाकात|स्लॉट|slot/i.test(asked)) {
    return { text: BOOKING_ANSWER, suggestions: [SUGGEST.where, SUGGEST.pay] };
  }

  return { text: DECLINE, suggestions: HINDI_STARTERS };
}
