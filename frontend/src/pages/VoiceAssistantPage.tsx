import { useCallback, useEffect, useRef, useState } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { answer, type AssistantReply } from '@/lib/assistant';
import { HINDI_STARTERS } from '@/lib/assistant.hi';
import { TAMIL_STARTERS } from '@/lib/assistant.ta';
import {
  canSpeakInto,
  speak,
  stopSpeaking,
  useVoiceInput,
  type VoiceLanguage,
} from '@/lib/useVoiceInput';

/**
 * The voice assistant, as its own page in the patient dashboard.
 *
 * <p>Separate from the chat widget on purpose. Speaking is a different posture
 * from typing: it wants room for the transcript, a visible recording state,
 * and the reply in large text, none of which fit in a corner box.
 *
 * <p>Read-only by design. It answers questions about doctors, hours, fees and
 * the clinic, and it explains how to book — but it never books or cancels
 * anything itself. Speech recognition mishears, and a misheard cancellation
 * would destroy a real appointment. Acting on speech needs a spoken
 * confirmation step, which is a later phase.
 */

const LANGUAGES: { code: VoiceLanguage; label: string; native: string }[] = [
  { code: 'hi', label: 'Hindi', native: 'हिंदी' },
  { code: 'ta', label: 'Tamil', native: 'தமிழ்' },
  { code: 'en', label: 'English', native: 'English' },
];

const STARTERS: Record<VoiceLanguage, string[]> = {
  hi: HINDI_STARTERS,
  ta: TAMIL_STARTERS,
  en: ['Which doctors are available?', 'How do I book an appointment?', 'Where is the clinic?'],
};

const COPY: Record<VoiceLanguage, Record<string, string>> = {
  hi: {
    prompt: 'माइक दबाकर अपना सवाल बोलिए',
    listening: 'सुन रहा हूँ… रोकने के लिए दबाएँ',
    thinking: 'देख रहा हूँ…',
    understanding: 'आपने जो कहा उसे समझ रहा हूँ…',
    heard: 'आपने कहा',
    speak: 'बोलकर पूछें',
    stop: 'रिकॉर्डिंग रोकें',
    or: 'या टाइप करें',
    send: 'भेजें',
    replay: 'सुनें',
    stopPlaying: 'रोकें',
    typeOnly: '',
  },
  ta: {
    prompt: 'மைக்கை அழுத்தி உங்கள் கேள்வியைப் பேசுங்கள்',
    listening: 'கேட்கிறேன்… நிறுத்த அழுத்தவும்',
    thinking: 'பார்க்கிறேன்…',
    understanding: 'நீங்கள் சொன்னதைப் புரிந்துகொள்கிறேன்…',
    heard: 'நீங்கள் சொன்னது',
    speak: 'பேசிக் கேளுங்கள்',
    stop: 'பதிவை நிறுத்தவும்',
    or: 'அல்லது தட்டச்சு செய்யவும்',
    send: 'அனுப்பு',
    replay: 'கேட்க',
    stopPlaying: 'நிறுத்து',
    typeOnly: '',
  },
  en: {
    prompt: 'Press the microphone and ask your question',
    listening: 'Listening… press to stop',
    thinking: 'Looking that up…',
    understanding: 'Working out what you said…',
    heard: 'You said',
    speak: 'Ask by voice',
    stop: 'Stop recording',
    or: 'Or type instead',
    send: 'Send',
    replay: 'Play',
    stopPlaying: 'Stop',
    typeOnly: '',
  },
};

interface Turn {
  question: string;
  reply: AssistantReply;
  /** Set once we know, so the demo can show a real number rather than a claim. */
  elapsedMs?: number;
}

export function VoiceAssistantPage() {
  const [language, setLanguage] = useState<VoiceLanguage>('hi');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState('');
  const [thinking, setThinking] = useState(false);
  // Which reply is being read aloud, so its button can offer Stop instead of
  // Play. Only one can speak at a time, so a single index is enough.
  const [speakingIndex, setSpeakingIndex] = useState<number | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const copy = COPY[language];

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' });
  }, [turns, thinking]);

  // A patient who navigates away must not hear the clinic talking from a page
  // they have left.
  useEffect(() => () => stopSpeaking(), []);

  const ask = useCallback(
    async (question: string) => {
      if (!question.trim() || thinking) {
        return;
      }
      setDraft('');
      setThinking(true);
      const started = performance.now();
      try {
        const reply = await answer(question);
        const elapsedMs = Math.round(performance.now() - started);
        let index = 0;
        setTurns((current) => {
          index = current.length;
          return [...current, { question, reply, elapsedMs }];
        });
        setSpeakingIndex(index);
        void speak(reply.text, language, () => {
          setSpeakingIndex((current) => (current === index ? null : current));
        });
      } catch {
        setTurns((current) => [
          ...current,
          {
            question,
            reply: {
              text:
                language === 'hi'
                  ? 'क्लिनिक की जानकारी अभी नहीं मिल पा रही है। कृपया दोबारा कोशिश करें।'
                  : language === 'ta'
                    ? 'கிளினிக் தகவலை இப்போது அணுக முடியவில்லை. மீண்டும் முயற்சிக்கவும்.'
                    : 'I could not reach the clinic’s information just now. Please try again.',
            },
          },
        ]);
      } finally {
        setThinking(false);
      }
    },
    [thinking, language],
  );

  const onTranscript = useCallback((text: string) => void ask(text), [ask]);
  const mic = useVoiceInput({ language, onTranscript });

  const switchLanguage = (next: VoiceLanguage) => {
    stopSpeaking();
    setSpeakingIndex(null);
    mic.cancel();
    setLanguage(next);
    setTurns([]);
    setDraft('');
  };

  const busy = thinking || mic.state === 'transcribing';

  return (
    <div>
      <PageHeader
        title="Voice assistant"
        description="Ask about doctors, hours, fees or the clinic — by voice, in your own language."
      />

      {/* Said plainly rather than discovered: it answers, it does not act. */}
      <p className="mb-4 rounded-card border border-border bg-bg px-4 py-3 text-meta text-text-secondary">
        This assistant answers questions. It cannot book or cancel an appointment for you — use
        My appointments for that. It also cannot give medical advice.
      </p>

      <div
        role="group"
        aria-label="Language"
        className="mb-6 flex flex-wrap gap-2"
      >
        {LANGUAGES.map((option) => (
          <button
            key={option.code}
            type="button"
            onClick={() => switchLanguage(option.code)}
            aria-pressed={language === option.code}
            lang={option.code}
            className={`rounded-pill border px-4 py-2 text-body transition-colors ${
              language === option.code
                ? 'border-accent bg-accent text-white'
                : 'border-border bg-surface text-text-secondary hover:border-accent hover:text-accent'
            }`}
          >
            {option.native}
          </button>
        ))}
      </div>

      <div className="rounded-modal border border-border bg-surface">
        <div className="min-h-[18rem] px-5 py-5">
          {turns.length === 0 && !busy && (
            <p lang={language} className="text-body text-text-muted">
              {copy.prompt}
            </p>
          )}

          <ul className="flex flex-col gap-6">
            {turns.map((turn, index) => (
              <li key={index}>
                {/* The transcript is shown, not hidden: recognition mishears
                    names, and a patient who can see what was heard can correct
                    it instead of being puzzled by a wrong answer. */}
                <p className="text-meta uppercase tracking-wide text-text-muted">{copy.heard}</p>
                <p lang={language} className="mt-1 text-body font-medium text-text-primary">
                  “{turn.question}”
                </p>

                <div className="mt-3 rounded-card bg-bg px-4 py-3">
                  <p lang={language} className="whitespace-pre-line text-body text-text-primary">
                    {turn.reply.text}
                  </p>

                  <div className="mt-3 flex flex-wrap items-center gap-3">
                    {/* A toggle, not a fire-and-forget play: a reply can run
                        for several seconds and a patient who started it by
                        accident needs a way to stop it. */}
                    <button
                      type="button"
                      onClick={() => {
                        if (speakingIndex === index) {
                          stopSpeaking();
                          setSpeakingIndex(null);
                          return;
                        }
                        setSpeakingIndex(index);
                        void speak(turn.reply.text, language, () => {
                          setSpeakingIndex((current) =>
                            current === index ? null : current,
                          );
                        });
                      }}
                      className={`rounded-pill border px-3 py-1 text-meta ${
                        speakingIndex === index
                          ? 'border-accent bg-accent-soft text-accent'
                          : 'border-border bg-surface text-text-secondary hover:border-accent hover:text-accent'
                      }`}
                    >
                      {speakingIndex === index
                        ? `■ ${copy.stopPlaying}`
                        : `▶ ${copy.replay}`}
                    </button>
                    {turn.elapsedMs !== undefined && (
                      <span className="tabular text-meta text-text-muted">
                        {(turn.elapsedMs / 1000).toFixed(1)}s
                      </span>
                    )}
                  </div>
                </div>

                {turn.reply.suggestions && turn.reply.suggestions.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {turn.reply.suggestions.map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        lang={language}
                        onClick={() => void ask(suggestion)}
                        className="rounded-pill border border-border bg-surface px-3 py-1 text-meta text-text-secondary hover:border-accent hover:text-accent"
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>

          <div aria-live="polite">
            {mic.state === 'transcribing' && (
              <p lang={language} className="mt-4 text-body text-text-muted">
                {copy.understanding}
              </p>
            )}
            {thinking && (
              <p lang={language} className="mt-4 text-body text-text-muted">
                {copy.thinking}
              </p>
            )}
            {mic.state === 'recording' && (
              <p lang={language} className="mt-4 text-body text-accent">
                {copy.listening}
              </p>
            )}
            {mic.error && <p className="mt-4 text-body text-danger">{mic.error}</p>}
          </div>

          <div ref={endRef} />
        </div>

        <div className="border-t border-border px-5 py-4">
          {turns.length === 0 && (
            <div className="mb-4 flex flex-wrap gap-2">
              {STARTERS[language].map((starter) => (
                <button
                  key={starter}
                  type="button"
                  lang={language}
                  onClick={() => void ask(starter)}
                  className="rounded-pill border border-border bg-surface px-3 py-1 text-meta text-text-secondary hover:border-accent hover:text-accent"
                >
                  {starter}
                </button>
              ))}
            </div>
          )}

          <div className="flex items-center gap-3">
            {mic.supported && canSpeakInto(language) && (
              <button
                type="button"
                onClick={() => (mic.state === 'recording' ? mic.stop() : void mic.start())}
                disabled={busy}
                lang={language}
                className={`flex items-center gap-2 rounded-pill px-5 py-3 text-body font-medium transition-colors disabled:opacity-50 ${
                  mic.state === 'recording'
                    ? 'bg-danger text-white'
                    : 'bg-accent text-white hover:bg-accent-hover'
                }`}
              >
                <span aria-hidden="true">{mic.state === 'recording' ? '■' : '🎤'}</span>
                {mic.state === 'recording' ? copy.stop : copy.speak}
              </button>
            )}

            <form
              className="flex flex-1 gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                void ask(draft);
              }}
            >
              <label className="sr-only" htmlFor="voice-draft">
                {copy.or}
              </label>
              <input
                id="voice-draft"
                value={draft}
                lang={language}
                onChange={(event) => setDraft(event.target.value)}
                placeholder={copy.or}
                className="flex-1 rounded-card border border-border bg-surface px-3 py-2 text-body"
              />
              <button
                type="submit"
                lang={language}
                disabled={!draft.trim() || busy}
                className="rounded-card border border-border px-4 py-2 text-body text-text-secondary disabled:opacity-50"
              >
                {copy.send}
              </button>
            </form>
          </div>

          {!mic.supported && (
            <p className="mt-3 text-meta text-text-muted">
              This browser cannot record audio, so questions can only be typed.
            </p>
          )}
          {mic.supported && !canSpeakInto(language) && copy.typeOnly && (
            <p className="mt-3 text-meta text-text-muted">{copy.typeOnly}</p>
          )}
        </div>
      </div>
    </div>
  );
}
