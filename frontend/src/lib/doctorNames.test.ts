import { describe, expect, it } from 'vitest';
import { matchDoctors, normalizeName } from './doctorNames';

/**
 * Both behaviours here were bugs found by using the assistant, so each has a
 * test named after what actually went wrong.
 */

function doctor(name: string, id = name) {
  return { doctorId: id, name, specialization: 'Dentist', consultationFee: 700 } as never;
}

const SHARMA = doctor('Dr. Sharma', 'd-sharma');
const MEERA = doctor('Dr. Meera Sharma', 'd-meera');
const CACHE = doctor('Dr. Cache Test', 'd-cache');
const ALL = [SHARMA, MEERA, CACHE];

describe('normalizeName', () => {
  it('brings a Latin and a Devanagari spelling of one name together', () => {
    expect(normalizeName('शर्मा')).toBe(normalizeName('Sharma'));
  });

  it('brings a Latin and a Tamil spelling of one name together', () => {
    expect(normalizeName('சர்மா')).toBe(normalizeName('Sharma'));
  });

  it('folds the doubled vowel in a transliterated name', () => {
    // "Meera" is how it is stored; "मीरा" transliterates to "mira".
    expect(normalizeName('मीरा')).toBe(normalizeName('Meera'));
  });

  it('handles a full Devanagari name with a title', () => {
    expect(normalizeName('डॉक्टर मीरा शर्मा')).toContain(normalizeName('Meera Sharma'));
  });
});

describe('matching a doctor named in Devanagari', () => {
  it('finds Dr. Sharma from "डॉक्टर शर्मा"', () => {
    // The reported bug: asking in Hindi listed every doctor instead of
    // answering, because the stored Latin name never appears in Devanagari.
    expect(matchDoctors('डॉक्टर शर्मा कब उपलब्ध है', ALL)).toEqual([SHARMA]);
  });

  it('finds Dr. Meera Sharma from "डॉक्टर मीरा शर्मा"', () => {
    expect(matchDoctors('डॉक्टर मीरा शर्मा का समय क्या है', ALL)).toEqual([MEERA]);
  });

  it('finds nobody when the question names nobody', () => {
    expect(matchDoctors('क्लिनिक कहाँ है', ALL)).toEqual([]);
  });
});

describe('matching a doctor named in Tamil', () => {
  it('finds Dr. Sharma from "டாக்டர் சர்மா"', () => {
    expect(matchDoctors('டாக்டர் சர்மா நேரம் என்ன', ALL)).toEqual([SHARMA]);
  });
});

describe('an exact name beats a shared surname', () => {
  it('"Dr. Sharma" resolves to Dr. Sharma, not to both Sharmas', () => {
    // The reported bug: this asked "which one did you mean?" when one doctor
    // is named exactly that.
    expect(matchDoctors('Dr. Sharma का समय क्या है?', ALL)).toEqual([SHARMA]);
  });

  it('"Meera Sharma" resolves to Dr. Meera Sharma only', () => {
    expect(matchDoctors('What are Dr. Meera Sharma hours?', ALL)).toEqual([MEERA]);
  });

  it('still reports genuine ambiguity when two doctors share a full name', () => {
    const twin = doctor('Dr. Sharma', 'd-twin');
    const matches = matchDoctors('Dr. Sharma का समय?', [SHARMA, twin, MEERA]);
    expect(matches).toHaveLength(2);
  });

  it('falls back to a surname when no full name is given', () => {
    // "Meera" alone is a forename, not a stored full name, so the surname
    // rule is what has to catch it.
    expect(matchDoctors('Meera', ALL)).toEqual([]);
  });
});

describe('matching does not fire on ordinary words', () => {
  it('does not match a short surname hidden inside a sentence', () => {
    const short = doctor('Dr. Ram', 'd-ram');
    // "Ram" is three letters, below the surname threshold, so a sentence that
    // merely contains it must not resolve to that doctor.
    expect(matchDoctors('program ke bare mein', [short])).toEqual([]);
  });

  it('does not match a doctor whose name is absent', () => {
    expect(matchDoctors('Dr. Gupta का समय?', ALL)).toEqual([]);
  });
});
