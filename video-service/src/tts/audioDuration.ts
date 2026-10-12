import {parseMedia} from '@remotion/media-parser';
import {nodeReader} from '@remotion/media-parser/node';

/** Read the encoded duration so narration is never clipped by an estimate. */
export async function readAudioDurationMs(filePath: string, fallbackMs: number): Promise<number> {
  try {
    const metadata = await parseMedia({
      src: filePath,
      fields: {durationInSeconds: true},
      reader: nodeReader,
      acknowledgeRemotionLicense: true,
    });
    const seconds = metadata.durationInSeconds;
    if (typeof seconds === 'number' && Number.isFinite(seconds) && seconds > 0) {
      return Math.ceil(seconds * 1000) + 700;
    }
  } catch (error) {
    console.warn('[TTS] Could not inspect audio duration, using estimate:', error);
  }
  return fallbackMs;
}
