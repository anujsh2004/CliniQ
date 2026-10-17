import { useCallback, useEffect, useRef, useState } from 'react';
import { voice } from '@/api/endpoints';
import { ApiRequestError } from '@/api/client';

/**
 * Recording from the microphone, and getting text back.
 *
 * <p>Kept out of the component because the interesting part is the teardown,
 * not the markup: a MediaStream that is not stopped leaves the browser's
 * recording indicator lit after the user has moved on, which looks to a
 * patient exactly like a clinic that is still listening. Every exit path here
 * stops the tracks.
 */

export type VoiceState = 'idle' | 'recording' | 'transcribing';

/** The three languages this clinic has actually been tested in. */
export type VoiceLanguage = 'hi' | 'ta' | 'en';

/**
 * Languages the microphone can handle.
 *
 * <p>All three, via two recognisers: IndicConformer for Hindi and Tamil,
 * Whisper for English. Kept as a list rather than assumed, because a language
 * offered in the picker but refused by the service is a broken button.
 */
export const SPOKEN_INPUT: readonly VoiceLanguage[] = ['hi', 'ta', 'en'];

export function canSpeakInto(language: VoiceLanguage): boolean {
  return SPOKEN_INPUT.includes(language);
}

/** Recordings longer than this are almost always a forgotten open mic. */
const MAX_MS = 30_000;

interface VoiceInput {
  state: VoiceState;
  error: string | null;
  /** True when this browser can record at all. */
  supported: boolean;
  start: () => Promise<void>;
  stop: () => void;
  cancel: () => void;
}

export function useVoiceInput(options: {
  language?: VoiceLanguage;
  onTranscript: (text: string) => void;
}): VoiceInput {
  const { language = 'hi', onTranscript } = options;

  const [state, setState] = useState<VoiceState>('idle');
  const [error, setError] = useState<string | null>(null);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const discardRef = useRef(false);

  // Read once: navigator.mediaDevices is absent entirely on an insecure
  // origin, so this is a capability check rather than a browser check.
  const supported =
    typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices?.getUserMedia &&
    typeof MediaRecorder !== 'undefined';

  const releaseStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  // A patient who closes the chat mid-recording must not leave the microphone
  // open behind them.
  useEffect(() => releaseStream, [releaseStream]);

  const start = useCallback(async () => {
    if (!supported) {
      setError('This browser cannot record audio.');
      return;
    }
    setError(null);
    discardRef.current = false;

    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      // Permission denied, or no microphone. Both are the patient's to fix,
      // and neither is worth a stack trace.
      setError('Microphone access was not allowed.');
      return;
    }

    streamRef.current = stream;
    chunksRef.current = [];

    const recorder = new MediaRecorder(stream);
    recorderRef.current = recorder;

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunksRef.current.push(event.data);
      }
    };

    recorder.onstop = async () => {
      const blob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' });
      releaseStream();

      if (discardRef.current || blob.size === 0) {
        setState('idle');
        return;
      }

      setState('transcribing');
      try {
        const transcript = await voice.transcribe(blob, language);
        if (transcript.text.trim()) {
          onTranscript(transcript.text.trim());
        } else {
          setError('Nothing was heard. Try again, a little closer to the microphone.');
        }
      } catch (caught) {
        setError(
          caught instanceof ApiRequestError
            ? caught.message
            : 'Speech recognition is unavailable right now.',
        );
      } finally {
        setState('idle');
      }
    };

    recorder.start();
    setState('recording');

    // Stops itself rather than uploading minutes of room noise.
    timerRef.current = window.setTimeout(() => {
      if (recorderRef.current?.state === 'recording') {
        recorderRef.current.stop();
      }
    }, MAX_MS);
  }, [supported, language, onTranscript, releaseStream]);

  const stop = useCallback(() => {
    if (recorderRef.current?.state === 'recording') {
      recorderRef.current.stop();
    }
  }, []);

  const cancel = useCallback(() => {
    discardRef.current = true;
    stop();
    releaseStream();
    setState('idle');
  }, [stop, releaseStream]);

  return { state, error, supported, start, stop, cancel };
}

/**
 * Speaks a reply aloud.
 *
 * <p>All three languages go to the clinic's own voice models, so every patient
 * hears the same voice rather than whichever one their operating system
 * happens to have installed — browsers ship few or no Indic voices, and the
 * ones they do ship read Devanagari in an English accent.
 *
 * <p>If the model is not running it falls back to the browser's own synthesis,
 * and then to silence. The text is on screen either way, so a failure here
 * costs the patient nothing but the audio.
 */
/**
 * Whatever is currently being read aloud.
 *
 * <p>Module-level rather than per-component because only one thing may speak
 * at a time: starting a second reply while the first is still talking produces
 * two overlapping voices, which is worse than either alone.
 */
let playing: HTMLAudioElement | null = null;

/** Stops anything currently speaking. Safe to call when nothing is. */
export function stopSpeaking(): void {
  if (playing) {
    playing.pause();
    playing.currentTime = 0;
    playing = null;
  }
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.cancel();
  }
}

export async function speak(
  text: string,
  language: VoiceLanguage = 'hi',
  onFinished?: () => void,
): Promise<boolean> {
  // Whatever was talking stops first, so a second tap never layers voices.
  stopSpeaking();

  try {
    const wav = await voice.speak(text, language);
    const url = URL.createObjectURL(wav);
    const audio = new Audio(url);
    playing = audio;

    const done = () => {
      // An object URL that is never released holds the whole clip in memory
      // for the life of the page, so it is revoked on every exit path.
      URL.revokeObjectURL(url);
      if (playing === audio) {
        playing = null;
      }
      onFinished?.();
    };
    audio.onended = done;
    audio.onerror = done;
    audio.onpause = () => {
      if (audio.currentTime === 0) {
        done();
      }
    };

    await audio.play();
    return true;
  } catch {
    // Falls through to the browser, which may still have a voice for it.
  }
  return speakInBrowser(text, language, onFinished);
}

/** The browser's own synthesis. Used for English, and as a last resort. */
function speakInBrowser(
  text: string,
  language: VoiceLanguage,
  onFinished?: () => void,
): boolean {
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    return false;
  }
  const match = window.speechSynthesis
    .getVoices()
    .find((candidate) => candidate.lang?.toLowerCase().startsWith(language));
  if (!match) {
    // Reading Tamil words with an English voice is worse than saying nothing.
    return false;
  }

  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.voice = match;
  utterance.lang = match.lang;
  utterance.rate = 0.95; // Clinic instructions, read a shade slower than default.
  utterance.onend = () => onFinished?.();
  utterance.onerror = () => onFinished?.();
  window.speechSynthesis.speak(utterance);
  return true;
}
