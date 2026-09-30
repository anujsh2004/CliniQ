import type { DoctorSummary } from '@/types/api';

/**
 * Finding the doctor a question is about, whatever script it is written in.
 *
 * <p>Two problems, both found by using the assistant rather than by testing it.
 *
 * <p>The first: doctor names are stored in English, but a Hindi speaker writes
 * or says "डॉक्टर शर्मा", and a Tamil speaker "டாக்டர் சர்மா". Matching the
 * stored Latin name inside a Devanagari sentence finds nothing, so the
 * assistant listed every doctor instead of answering. Both scripts are
 * therefore transliterated to rough Latin before matching.
 *
 * <p>The second: "Dr. Sharma" matched both Dr. Sharma and Dr. Meera Sharma,
 * because "sharma" is a substring of each, so the assistant asked which was
 * meant when one of them was named exactly. A doctor whose whole name appears
 * in the question now wins over one who merely shares a surname.
 *
 * <p>The transliteration is deliberately lossy. It exists to compare names, not
 * to render them: "Meera" and "मीरा" only have to arrive at the same string,
 * and that string is never shown to a patient.
 */

/** Consonants, without their inherent vowel. */
const DEVANAGARI_CONSONANTS: Record<string, string> = {
  क: 'k', ख: 'kh', ग: 'g', घ: 'gh', ङ: 'n',
  च: 'ch', छ: 'chh', ज: 'j', झ: 'jh', ञ: 'n',
  ट: 't', ठ: 'th', ड: 'd', ढ: 'dh', ण: 'n',
  त: 't', थ: 'th', द: 'd', ध: 'dh', न: 'n',
  प: 'p', फ: 'ph', ब: 'b', भ: 'bh', म: 'm',
  य: 'y', र: 'r', ल: 'l', व: 'v', ळ: 'l',
  श: 'sh', ष: 'sh', स: 's', ह: 'h',
  क़: 'q', ख़: 'kh', ग़: 'g', ज़: 'z', फ़: 'f', ड़: 'r', ढ़: 'rh',
};

/** Vowel signs, which replace a consonant's inherent vowel. */
const DEVANAGARI_SIGNS: Record<string, string> = {
  'ा': 'a', 'ि': 'i', 'ी': 'i', 'ु': 'u', 'ू': 'u', 'ृ': 'ri',
  'े': 'e', 'ै': 'ai', 'ो': 'o', 'ौ': 'au', 'ॉ': 'o', 'ॅ': 'a',
};

const DEVANAGARI_VOWELS: Record<string, string> = {
  अ: 'a', आ: 'a', इ: 'i', ई: 'i', उ: 'u', ऊ: 'u', ऋ: 'ri',
  ए: 'e', ऐ: 'ai', ओ: 'o', औ: 'au', ऑ: 'o',
};

const TAMIL_CONSONANTS: Record<string, string> = {
  க: 'k', ங: 'n', ச: 's', ஞ: 'n', ட: 't', ண: 'n',
  த: 'th', ந: 'n', ப: 'p', ம: 'm', ய: 'y', ர: 'r',
  ல: 'l', வ: 'v', ழ: 'l', ள: 'l', ற: 'r', ன: 'n',
  ஜ: 'j', ஷ: 'sh', ஸ: 's', ஹ: 'h',
};

const TAMIL_SIGNS: Record<string, string> = {
  'ா': 'a', 'ி': 'i', 'ீ': 'i', 'ு': 'u', 'ூ': 'u',
  'ெ': 'e', 'ே': 'e', 'ை': 'ai', 'ொ': 'o', 'ோ': 'o', 'ௌ': 'au',
};

const TAMIL_VOWELS: Record<string, string> = {
  அ: 'a', ஆ: 'a', இ: 'i', ஈ: 'i', உ: 'u', ஊ: 'u',
  எ: 'e', ஏ: 'e', ஐ: 'ai', ஒ: 'o', ஓ: 'o', ஔ: 'au',
};

/** Virama in Devanagari, pulli in Tamil: both cancel the inherent vowel. */
const DEVANAGARI_VIRAMA = '्';
const TAMIL_PULLI = '்';

const NASALS = ['ं', 'ँ', 'ং'];

/** One transliterated piece, and whether it still owes an inherent vowel. */
interface Piece {
  text: string;
  inherent: boolean;
}

/**
 * Walks an Indic word, emitting each consonant with whichever vowel follows it.
 *
 * <p>A bare consonant carries an inherent "a" unless a vowel sign replaces it
 * or a virama cancels it, so this cannot be a character-by-character lookup.
 *
 * <p>The inherent vowel is tracked rather than written immediately, because
 * whether it is pronounced depends on what comes next: it is silent at the end
 * of a word and sounded in the middle. शर्मा ends in an explicit vowel and
 * keeps its "a" — "sharma"; डॉक्टर ends in an inherent one and loses it —
 * "doktar". Writing it unconditionally, or stripping every final "a", each
 * breaks one of those two.
 */
function transliterateIndic(
  text: string,
  consonants: Record<string, string>,
  signs: Record<string, string>,
  vowels: Record<string, string>,
  virama: string,
): string {
  const pieces: Piece[] = [];
  let index = 0;

  while (index < text.length) {
    const character = text[index];

    if (consonants[character]) {
      const base = consonants[character];
      index += 1;
      const next = text[index];

      if (next === virama) {
        pieces.push({ text: base, inherent: false });
        index += 1;
      } else if (next && signs[next]) {
        pieces.push({ text: base + signs[next], inherent: false });
        index += 1;
      } else {
        pieces.push({ text: base, inherent: true });
      }
      continue;
    }

    if (vowels[character]) {
      pieces.push({ text: vowels[character], inherent: false });
    } else if (NASALS.includes(character)) {
      pieces.push({ text: 'n', inherent: false });
    } else if (/[A-Za-z0-9]/.test(character)) {
      // Latin letters and digits are kept, not dropped. Patients code-switch
      // constantly — "Dr. Sharma का समय क्या है?" is one sentence — and
      // discarding the Latin half threw away the doctor's name, the only part
      // that identifies them. Digits are kept for the same reason: they carry
      // information, and replacing them with spaces made every name that
      // differed only by number normalise identically.
      pieces.push({ text: character, inherent: false });
    } else if (/[\s]/.test(character) || /[\p{P}\p{S}]/u.test(character)) {
      pieces.push({ text: ' ', inherent: false });
    }
    // Anything else — a stray combining sign — contributes nothing.
    index += 1;
  }

  let out = '';
  pieces.forEach((piece, position) => {
    out += piece.text;
    if (!piece.inherent) {
      return;
    }
    const next = pieces[position + 1];
    const endsWord = next === undefined || next.text === ' ';
    if (!endsWord) {
      out += 'a';
    }
  });
  return out;
}

const DEVANAGARI = /[ऀ-ॿ]/;
const TAMIL = /[஀-௿]/;

/**
 * Reduces a name to a form that compares equal across spellings and scripts.
 *
 * <p>Aggressive on purpose. "Meera" and "मीरा" become "mira"; "Sharma" and
 * Tamil "சர்மா" both become "sarma". Folding aspirates and doubled vowels is
 * what brings each pair together. The cost is that two genuinely similar names
 * can collide, which the caller handles by asking which was meant rather than
 * guessing.
 */
export function normalizeName(text: string): string {
  let value = text;
  if (DEVANAGARI.test(value)) {
    value = transliterateIndic(
      value, DEVANAGARI_CONSONANTS, DEVANAGARI_SIGNS, DEVANAGARI_VOWELS, DEVANAGARI_VIRAMA,
    );
  }
  if (TAMIL.test(value)) {
    value = transliterateIndic(
      value, TAMIL_CONSONANTS, TAMIL_SIGNS, TAMIL_VOWELS, TAMIL_PULLI,
    );
  }

  return value
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    // Aspirates and their plain forms are written interchangeably in
    // transliterated Indian names, so they are folded together. Tamil has no
    // "sh", which is why "Sharma" has to reduce to the same stem as "சர்மா".
    .replace(/ph/g, 'f')
    .replace(/(sh|th|kh|gh|dh|bh|jh|ch)/g, (pair) => pair[0])
    .replace(/ee/g, 'i')
    .replace(/oo/g, 'u')
    .replace(/([a-z])\1+/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}

/** A doctor's name without the title, which no patient says as part of it. */
function bareName(doctor: DoctorSummary): string {
  return normalizeName(doctor.name.replace(/^\s*dr\.?\s*/i, ''));
}

/** True when `needle` appears in `haystack` on word boundaries. */
function containsWords(haystack: string, needle: string): boolean {
  if (!needle) {
    return false;
  }
  return ` ${haystack} `.includes(` ${needle} `);
}

/**
 * The doctors a question is about.
 *
 * <p>Empty when the question names nobody, one entry when it names somebody,
 * and several only when it is genuinely ambiguous.
 */
export function matchDoctors(question: string, all: DoctorSummary[]): DoctorSummary[] {
  const asked = normalizeName(question);

  // A doctor whose whole name appears in the question, longest match winning:
  // asking for "Meera Sharma" must not also match Dr. Sharma, and asking for
  // "Sharma" must not be made ambiguous by Dr. Meera Sharma.
  const named = all.filter((doctor) => containsWords(asked, bareName(doctor)));
  if (named.length > 0) {
    const longest = Math.max(...named.map((doctor) => bareName(doctor).length));
    return named.filter((doctor) => bareName(doctor).length === longest);
  }

  // Otherwise a surname on its own, which is how people usually refer to a
  // doctor. Short surnames are skipped: they collide with ordinary words.
  return all.filter((doctor) => {
    const parts = bareName(doctor).split(' ');
    const surname = parts[parts.length - 1] ?? '';
    return surname.length > 3 && containsWords(asked, surname);
  });
}
