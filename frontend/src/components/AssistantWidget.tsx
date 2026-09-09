import { useEffect, useRef, useState } from 'react';
import { answer, CLINIC_ADDRESS, type AssistantReply } from '@/lib/assistant';

interface Message {
  from: 'you' | 'assistant';
  text: string;
  map?: boolean;
  suggestions?: string[];
}

const OPENING: Message = {
  from: 'assistant',
  text: 'Hello. I can help with booking, a doctor’s hours, and where the clinic is.',
  suggestions: ['How do I book an appointment?', 'Where is the clinic?'],
};

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
 * A scripted assistant in the corner of the app.
 *
 * <p>Rule-based, not AI: it answers a handful of questions from the real API
 * and declines everything else. The AI layer in Phase 7 replaces it; until then
 * it is deliberately honest about its own limits rather than guessing.
 */
export function AssistantWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([OPENING]);
  const [draft, setDraft] = useState('');
  const [thinking, setThinking] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [messages, open]);

  const ask = async (question: string) => {
    if (!question.trim() || thinking) {
      return;
    }
    setMessages((current) => [...current, { from: 'you', text: question }]);
    setDraft('');
    setThinking(true);
    try {
      const reply: AssistantReply = await answer(question);
      setMessages((current) => [
        ...current,
        { from: 'assistant', text: reply.text, map: reply.map, suggestions: reply.suggestions },
      ]);
    } catch {
      setMessages((current) => [
        ...current,
        {
          from: 'assistant',
          text: 'I could not reach the clinic’s information just now. Please try again.',
        },
      ]);
    } finally {
      setThinking(false);
    }
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
      className="fixed bottom-4 right-4 z-40 flex h-[28rem] w-[min(22rem,calc(100vw-2rem))] flex-col rounded-modal border border-border bg-surface shadow-md"
    >
      <header className="flex items-center justify-between border-b border-border px-4 py-3">
        <div>
          <p className="text-cardTitle font-semibold text-text-primary">Clinic assistant</p>
          {/* Said plainly: it is a scripted helper, not an AI that knows things. */}
          <p className="text-meta text-text-muted">Answers a few common questions</p>
        </div>
        <button
          type="button"
          onClick={() => setOpen(false)}
          aria-label="Close the clinic assistant"
          className="rounded-card px-2 py-1 text-body text-text-secondary hover:bg-bg"
        >
          ✕
        </button>
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-3">
        <ul className="flex flex-col gap-3">
          {messages.map((message, index) => (
            <li
              key={index}
              className={message.from === 'you' ? 'flex justify-end' : 'flex justify-start'}
            >
              <div
                className={`max-w-[85%] rounded-card px-3 py-2 text-body ${
                  message.from === 'you'
                    ? 'bg-accent text-white'
                    : 'bg-bg text-text-primary'
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
                Looking that up…
              </div>
            </li>
          )}
        </ul>
        <div ref={endRef} />
      </div>

      <form
        className="flex gap-2 border-t border-border px-3 py-3"
        onSubmit={(event) => {
          event.preventDefault();
          void ask(draft);
        }}
      >
        <label className="sr-only" htmlFor="assistant-input">
          Ask the clinic assistant
        </label>
        <input
          id="assistant-input"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Ask about booking or hours…"
          className="flex-1 rounded-card border border-border bg-surface px-3 py-2 text-body"
        />
        <button
          type="submit"
          disabled={!draft.trim() || thinking}
          className="rounded-card bg-accent px-3 py-2 text-body font-medium text-white disabled:opacity-50"
        >
          Send
        </button>
      </form>
    </div>
  );
}
