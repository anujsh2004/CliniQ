import { useCallback, useEffect, useRef, useState } from 'react';
import { answer, CLINIC_ADDRESS, type AssistantReply } from '@/lib/assistant';
import { TAMIL_STARTERS } from '@/lib/assistant.ta';
import { speak, useVoiceInput } from '@/lib/useVoiceInput';

interface Message {
  from: 'you' | 'assistant';
  text: string;
  map?: boolean;
  suggestions?: string[];
  /** Set on Tamil replies so they can be read aloud in a Tamil voice. */
  tamil?: boolean;
}

type Language = 'en' | 'ta';

const OPENING: Record<Language, Message> = {
  en: {
    from: 'assistant',
    text: 'Hello. I can help with booking, a doctor’s hours, and where the clinic is.',
    suggestions: ['How do I book an appointment?', 'Where is the clinic?'],
  },
  ta: {
    from: 'assistant',
    text: 'வணக்கம். அப்பாயிண்ட்மென்ட் பதிவு, மருத்துவர் நேரம், கிளினிக் முகவரி பற்றி கேட்கலாம். பேசவும் முடியும் — மைக் பொத்தானை அழுத்தவும்.',
    suggestions: TAMIL_STARTERS,
    tamil: true,
  },
};

const COPY = {
  en: {
    title: 'Clinic assistant',
    subtitle: 'Answers a few common questions',
    placeholder: 'Ask about booking or hours…',
    send: 'Send',
    thinking: 'Looking that up…',
    inputLabel: 'Ask the clinic assistant',
    record: 'Record a question',
    stop: 'Stop recording',
    listening: 'Listening… tap to stop',
    transcribing: 'Understanding what you said…',
  },
  ta: {
    title: 'கிளினிக் உதவியாளர்',
    subtitle: 'சில பொதுவான கேள்விகளுக்குப் பதில் அளிக்கும்',
    placeholder: 'பதிவு அல்லது நேரம் பற்றி கேட்கவும்…',
    send: 'அனுப்பு',
    thinking: 'பார்க்கிறேன்…',
    inputLabel: 'கிளினிக் உதவியாளரிடம் கேட்கவும்',
    record: 'கேள்வியைப் பேசவும்',
    stop: 'பதிவை நிறுத்தவும்',
    listening: 'கேட்கிறேன்… நிறுத்த அழுத்தவும்',
    transcribing: 'நீங்கள் சொன்னதைப் புரிந்துகொள்கிறேன்…',
  },
} as const;

/**
 * A small OpenStreetMap embed. No API key, no third-party script, and no
 * tracking of the patient looking up a clinic address.
 */
function ClinicMap() {
  const { latitude, longitude } = CLINIC_ADDRESS;
  const box = [longitude - 0.004, latitude - 0.002, longitude + 0.004, latitude + 0.002].join(',');
  return (
    <iframe
      title={`Map showing ${CLINIC_ADDRESS.name}`}
      className="mt-2 h-40 w-full rounded-card border border-border"
      src={`https://www.openstreetmap.org/export/embed.html?bbox=${box}&layer=mapnik&marker=${latitude},${longitude}`}
    />
  );
}

/**
 * A scripted assistant in the corner of the app, in English or Tamil.
 *
 * <p>Rule-based, not generative: it answers a handful of questions from the
 * real API and declines everything else, in whichever of the two languages the
 * question was asked. What the model does add is the way in — a patient can
 * speak the question instead of typing it, which matters far more in Tamil,
 * where typing on a phone keyboard is slow enough that many people simply
 * would not bother.
 *
 * <p>Recognition happens on the clinic's own hardware. Nothing a patient says
 * is sent to a third party.
 */
export function AssistantWidget() {
  const [open, setOpen] = useState(false);
  const [language, setLanguage] = useState<Language>('en');
  const [messages, setMessages] = useState<Message[]>([OPENING.en]);
  const [draft, setDraft] = useState('');
  const [thinking, setThinking] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  const copy = COPY[language];

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [messages, open]);

  const ask = useCallback(
    async (question: string) => {
      if (!question.trim() || thinking) {
        return;
      }
      setMessages((current) => [...current, { from: 'you', text: question }]);
      setDraft('');
      setThinking(true);
      try {
        const reply: AssistantReply = await answer(question);
        // The reply is Tamil when the question was; the assistant follows the
        // patient rather than making them follow a setting.
        const inTamil = /[஀-௿]/.test(reply.text);
        setMessages((current) => [
          ...current,
          {
            from: 'assistant',
            text: reply.text,
            map: reply.map,
            suggestions: reply.suggestions,
            tamil: inTamil,
          },
        ]);
        if (inTamil) {
          speak(reply.text, 'ta-IN');
        }
      } catch {
        setMessages((current) => [
          ...current,
          {
            from: 'assistant',
            text:
              language === 'ta'
                ? 'கிளினிக் தகவலை இப்போது அணுக முடியவில்லை. மீண்டும் முயற்சிக்கவும்.'
                : 'I could not reach the clinic’s information just now. Please try again.',
          },
        ]);
      } finally {
        setThinking(false);
      }
    },
    [thinking, language],
  );

  // A spoken question goes straight through, rather than landing in the box
  // for the patient to press Send: they have already asked it.
  const onTranscript = useCallback(
    (text: string) => {
      void ask(text);
    },
    [ask],
  );

  const mic = useVoiceInput({ language, onTranscript });

  const switchLanguage = (next: Language) => {
    setLanguage(next);
    setMessages([OPENING[next]]);
    mic.cancel();
  };

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        // No aria-label: the visible text already names the button, and an
        // aria-label that differs from it breaks voice control, where people
        // say what they can see (WCAG 2.5.3).
        className="fixed bottom-4 right-4 z-40 rounded-pill bg-accent px-4 py-3 text-body font-medium text-white shadow-md hover:bg-accent-hover"
      >
        Ask a question
      </button>
    );
  }

  return (
    <div
      role="dialog"
      aria-label="Clinic assistant"
      className="fixed bottom-4 right-4 z-40 flex h-[30rem] w-[min(22rem,calc(100vw-2rem))] flex-col rounded-modal border border-border bg-surface shadow-md"
    >
      <header className="flex items-start justify-between gap-2 border-b border-border px-4 py-3">
        <div className="min-w-0">
          <p className="text-cardTitle font-semibold text-text-primary">{copy.title}</p>
          {/* Said plainly: it is a scripted helper, not an AI that knows things. */}
          <p className="text-meta text-text-muted">{copy.subtitle}</p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {/* A real toggle rather than a dropdown: there are two languages, and
              a patient should reach their own in one tap. */}
          <div role="group" aria-label="Language" className="flex rounded-pill border border-border">
            {(['en', 'ta'] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => switchLanguage(option)}
                aria-pressed={language === option}
                className={`rounded-pill px-2 py-1 text-meta ${
                  language === option
                    ? 'bg-accent text-white'
                    : 'text-text-secondary hover:text-accent'
                }`}
              >
                {option === 'en' ? 'EN' : 'தமிழ்'}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label="Close the clinic assistant"
            className="rounded-card px-2 py-1 text-body text-text-secondary hover:bg-bg"
          >
            ✕
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-3">
        <ul className="flex flex-col gap-3">
          {messages.map((message, index) => (
            <li
              key={index}
              className={message.from === 'you' ? 'flex justify-end' : 'flex justify-start'}
            >
              <div
                lang={message.tamil ? 'ta' : undefined}
                className={`max-w-[85%] rounded-card px-3 py-2 text-body ${
                  message.from === 'you' ? 'bg-accent text-white' : 'bg-bg text-text-primary'
                }`}
              >
                <p className="whitespace-pre-line">{message.text}</p>
                {message.map && <ClinicMap />}
                {message.suggestions && message.suggestions.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {message.suggestions.map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        onClick={() => void ask(suggestion)}
                        className="rounded-pill border border-border bg-surface px-3 py-1 text-meta text-text-secondary hover:border-accent hover:text-accent"
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </li>
          ))}
          {thinking && (
            <li className="flex justify-start">
              <div className="rounded-card bg-bg px-3 py-2 text-body text-text-muted">
                {copy.thinking}
              </div>
            </li>
          )}
        </ul>
        <div ref={endRef} />
      </div>

      {/* Recording state and any microphone problem are announced, because a
          patient who is speaking is not necessarily looking at the screen. */}
      <div aria-live="polite" className="px-3">
        {mic.state === 'recording' && (
          <p className="pb-2 text-meta text-accent">{copy.listening}</p>
        )}
        {mic.state === 'transcribing' && (
          <p className="pb-2 text-meta text-text-muted">{copy.transcribing}</p>
        )}
        {mic.error && <p className="pb-2 text-meta text-danger">{mic.error}</p>}
      </div>

      <form
        className="flex gap-2 border-t border-border px-3 py-3"
        onSubmit={(event) => {
          event.preventDefault();
          void ask(draft);
        }}
      >
        <label className="sr-only" htmlFor="assistant-input">
          {copy.inputLabel}
        </label>
        <input
          id="assistant-input"
          value={draft}
          lang={language}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={copy.placeholder}
          className="flex-1 rounded-card border border-border bg-surface px-3 py-2 text-body"
        />
        {mic.supported && (
          <button
            type="button"
            onClick={() => (mic.state === 'recording' ? mic.stop() : void mic.start())}
            disabled={mic.state === 'transcribing' || thinking}
            // Icon-only, so it needs a name of its own. It changes with the
            // state so the label always describes what pressing it will do.
            aria-label={mic.state === 'recording' ? copy.stop : copy.record}
            className={`rounded-card px-3 py-2 text-body disabled:opacity-50 ${
              mic.state === 'recording'
                ? 'bg-danger text-white'
                : 'border border-border text-text-secondary hover:border-accent hover:text-accent'
            }`}
          >
            {mic.state === 'recording' ? '■' : '🎤'}
          </button>
        )}
        <button
          type="submit"
          disabled={!draft.trim() || thinking}
          className="rounded-card bg-accent px-3 py-2 text-body font-medium text-white disabled:opacity-50"
        >
          {copy.send}
        </button>
      </form>
    </div>
  );
}
