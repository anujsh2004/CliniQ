import { beforeEach, describe, expect, it, vi } from 'vitest';

// Mocked before the module under test is imported, because the Tamil
// assistant reads real doctors for anything about hours.
vi.mock('@/api/endpoints', () => ({
  doctors: {
    list: vi.fn(),
    availability: vi.fn(),
  },
}));

import { doctors } from '@/api/endpoints';
import { answer } from './assistant';
import { answerTamil, isTamil } from './assistant.ta';

const listMock = vi.mocked(doctors.list);
const availabilityMock = vi.mocked(doctors.availability);

const MEERA = {
  doctorId: 'd-1',
  name: 'Dr. Meera Sharma',
  specialization: 'Dentist',
  consultationFee: 700,
} as never;

beforeEach(() => {
  vi.clearAllMocks();
  listMock.mockResolvedValue({ content: [MEERA], totalPages: 1 } as never);
  availabilityMock.mockResolvedValue([
    {
      dayOfWeek: 'MONDAY',
      startTime: '09:00:00',
      endTime: '13:00:00',
      slotDurationMinutes: 15,
    },
  ] as never);
});

describe('Tamil detection', () => {
  it('recognises Tamil script', () => {
    expect(isTamil('கிளினிக் எங்கே இருக்கிறது?')).toBe(true);
  });

  it('does not claim English is Tamil', () => {
    expect(isTamil('Where is the clinic?')).toBe(false);
  });

  it('treats a code-switched sentence as Tamil', () => {
    // How people actually speak: one Tamil word is enough to answer in Tamil.
    expect(isTamil('நாளைக்கு appointment வேணும்')).toBe(true);
  });
});

describe('Tamil intents', () => {
  it('explains booking', async () => {
    const reply = await answerTamil('அப்பாயிண்ட்மென்ட் எப்படி பதிவு செய்வது?');
    expect(reply.text).toContain('பதிவு செய்ய');
    expect(reply.text).toContain('5 நிமிடங்கள');
  });

  it('gives the address with a map', async () => {
    const reply = await answerTamil('கிளினிக் எங்கே இருக்கிறது?');
    expect(reply.map).toBe(true);
    expect(reply.text).toContain('MG Road');
  });

  it('explains the hold when asked about payment', async () => {
    const reply = await answerTamil('பணம் செலுத்தவில்லை என்றால் என்ன ஆகும்?');
    expect(reply.text).toContain('ரத்து');
    expect(reply.text).toContain('5 நிமிடங்கள');
  });

  it('treats a question about paying in time as being about money, not hours', async () => {
    // The same ordering trap as the English assistant: this sentence contains
    // the word for "time", which would otherwise pull it into the doctor
    // branch and produce an answer about opening hours.
    const reply = await answerTamil('நேரத்திற்குள் பணம் செலுத்தவில்லை என்றால்?');
    expect(reply.text).toContain('ரத்து');
    expect(availabilityMock).not.toHaveBeenCalled();
  });

  it('explains cancelling', async () => {
    const reply = await answerTamil('அப்பாயிண்ட்மென்ட்டை ரத்து செய்ய வேண்டும்');
    expect(reply.text).toContain('Cancel');
  });

  it('explains reminders', async () => {
    const reply = await answerTamil('நினைவூட்டல் வருமா?');
    expect(reply.text).toContain('நினைவூட்டல்');
  });

  it('reads a doctor’s hours from the real API, in Tamil', async () => {
    const reply = await answerTamil('Meera Sharma அவர்களின் நேரம் என்ன?');
    expect(availabilityMock).toHaveBeenCalledWith('d-1');
    expect(reply.text).toContain('திங்கள்');
    expect(reply.text).toContain('காலை 9 மணி');
  });

  it('asks which doctor when the question names none', async () => {
    const reply = await answerTamil('மருத்துவர் நேரம் என்ன?');
    expect(reply.text).toContain('எந்த மருத்துவர்');
  });

  it('declines rather than inventing an answer', async () => {
    const reply = await answerTamil('எனக்கு தலைவலி, என்ன மருந்து சாப்பிடலாம்?');
    expect(reply.text).toContain('மன்னிக்கவும்');
  });

  it('greets back', async () => {
    const reply = await answerTamil('வணக்கம்');
    expect(reply.text).toContain('வணக்கம்');
    expect(reply.suggestions?.length).toBeGreaterThan(0);
  });
});

describe('language routing', () => {
  it('answers a Tamil question in Tamil through the shared entry point', async () => {
    // The widget calls answer(); nothing in the UI decides the language.
    const reply = await answer('கிளினிக் எங்கே இருக்கிறது?');
    expect(reply.text).toContain('MG Road');
    expect(reply.map).toBe(true);
    expect(reply.text).not.toContain('is at');
  });

  it('still answers an English question in English', async () => {
    const reply = await answer('Where is the clinic?');
    expect(reply.text).toContain('is at');
  });
});
