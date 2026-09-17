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
  language?: string;
  onTranscript: (text: string) => void;
}): VoiceInput {
  const { language = 'ta', onTranscript } = options;

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
 * <p>Uses the browser's own speech synthesis rather than another model: it
 * supports ta-IN, costs nothing, needs no GPU, and keeps the demo working when
 * the speech service is not running. If no Tamil voice is installed it stays
 * silent instead of reading Tamil words in an English accent, which is worse
 * than saying nothing.
 */
export function speak(text: string, language = 'ta-IN'): boolean {
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    return false;
  }
  const voices = window.speechSynthesis.getVoices();
  const match = voices.find((candidate) => candidate.lang?.toLowerCase().startsWith(
    language.slice(0, 2).toLowerCase(),
  ));
  if (!match) {
    return false;
  }

  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.voice = match;
  utterance.lang = match.lang;
  utterance.rate = 0.95; // Clinic instructions, read a shade slower than default.
  window.speechSynthesis.speak(utterance);
  return true;
}
