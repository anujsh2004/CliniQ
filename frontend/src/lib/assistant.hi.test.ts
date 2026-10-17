import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/endpoints', () => ({
  doctors: { list: vi.fn(), availability: vi.fn() },
}));

import { doctors } from '@/api/endpoints';
import { answer } from './assistant';
import { answerHindi, isHindi } from './assistant.hi';

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
    { dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '13:00:00', slotDurationMinutes: 15 },
  ] as never);
});

describe('Hindi detection', () => {
  it('recognises Devanagari', () => {
    expect(isHindi('क्लिनिक कहाँ है?')).toBe(true);
  });

  it('does not claim English is Hindi', () => {
    expect(isHindi('Where is the clinic?')).toBe(false);
  });

  it('treats a code-switched sentence as Hindi', () => {
    // How people actually speak. One Hindi word is enough.
    expect(isHindi('कल appointment चाहिए')).toBe(true);
  });
});

describe('Hindi intents', () => {
  it('explains booking', async () => {
    const reply = await answerHindi('अपॉइंटमेंट कैसे बुक करें?');
    expect(reply.text).toContain('बुक करने के लिए');
    expect(reply.text).toContain('5 मिनट');
  });

  it('gives the address with a map', async () => {
    const reply = await answerHindi('क्लिनिक कहाँ है?');
    expect(reply.map).toBe(true);
    expect(reply.text).toContain('MG Road');
  });

  it('explains the hold when asked about payment', async () => {
    const reply = await answerHindi('भुगतान न करूँ तो क्या होगा?');
    expect(reply.text).toContain('रद्द');
  });

  it('treats paying on time as a money question, not an hours question', async () => {
    // "समय" means "time", which would otherwise pull this into the doctor
    // branch and answer about opening hours.
    const reply = await answerHindi('समय पर भुगतान न करने पर क्या होगा?');
    expect(reply.text).toContain('रद्द');
    expect(availabilityMock).not.toHaveBeenCalled();
  });

  it('lists the doctors when asked who is available', async () => {
    const reply = await answerHindi('कौन से डॉक्टर उपलब्ध हैं?');
    expect(reply.text).toContain('Dr. Meera Sharma');
    expect(reply.text).toContain('700');
  });

  it('reads a doctor’s hours from the real API, in Hindi', async () => {
    const reply = await answerHindi('Meera Sharma का समय क्या है?');
    expect(availabilityMock).toHaveBeenCalledWith('d-1');
    expect(reply.text).toContain('सोमवार');
    expect(reply.text).toContain('सुबह 9 बजे');
  });

  it('explains cancelling', async () => {
    const reply = await answerHindi('अपॉइंटमेंट रद्द करनी है');
    expect(reply.text).toContain('Cancel');
  });

  it('explains reminders', async () => {
    const reply = await answerHindi('क्या रिमाइंडर मिलेगा?');
    expect(reply.text).toContain('रिमाइंडर');
  });

  it('declines rather than inventing an answer', async () => {
    const reply = await answerHindi('आपका पसंदीदा रंग कौन सा है?');
    expect(reply.text).toContain('माफ़ कीजिए');
  });
});

describe('medical questions are refused, in every language', () => {
  it('refuses a Hindi symptom question and does not answer about hours', async () => {
    const reply = await answerHindi('मुझे सीने में दर्द है, क्या करूँ?');
    expect(reply.text).toContain('चिकित्सा सलाह नहीं');
    expect(reply.text).toContain('अस्पताल');
    expect(availabilityMock).not.toHaveBeenCalled();
  });

  it('refuses a Hindi question about which medicine to take', async () => {
    const reply = await answerHindi('कौन सी दवा लूँ?');
    expect(reply.text).toContain('चिकित्सा सलाह नहीं');
  });

  it('refuses an English symptom question', async () => {
    const reply = await answer('I have chest pain, what should I do?');
    expect(reply.text).toContain('cannot give medical advice');
  });

  it('refuses a Tamil symptom question', async () => {
    const reply = await answer('எனக்கு நெஞ்சு வலி இருக்கிறது');
    expect(reply.text).toContain('மருத்துவ ஆலோசனை வழங்க முடியாது');
  });
});

describe('language routing', () => {
  it('answers a Hindi question in Hindi through the shared entry point', async () => {
    const reply = await answer('क्लिनिक कहाँ है?');
    expect(reply.text).toContain('क्लिनिक यहाँ है');
    expect(reply.map).toBe(true);
  });

  it('still answers English in English', async () => {
    const reply = await answer('Where is the clinic?');
    expect(reply.text).toContain('is at');
  });
});
