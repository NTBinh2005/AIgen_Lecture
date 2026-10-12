import fs from 'fs';
import path from 'path';
import {spawn} from 'child_process';

const resolveFfmpeg = (): string => {
  const configured = process.env.FFMPEG_PATH?.trim();
  if (configured) return configured;
  if (process.platform === 'win32') {
    const bundled = path.resolve(process.cwd(), 'node_modules', '@remotion', 'compositor-win32-x64-msvc', 'ffmpeg.exe');
    if (fs.existsSync(bundled)) return bundled;
  }
  return 'ffmpeg';
};

const decodeMonoPcm = (filePath: string): Promise<Buffer> => new Promise((resolve, reject) => {
  const child = spawn(resolveFfmpeg(), [
    '-loglevel', 'error', '-i', filePath, '-f', 'wav', '-acodec', 'pcm_s16le', '-ac', '1', '-ar', '16000', 'pipe:1',
  ], {windowsHide: true});
  const chunks: Buffer[] = [];
  const errors: Buffer[] = [];
  child.stdout.on('data', (chunk: Buffer) => chunks.push(chunk));
  child.stderr.on('data', (chunk: Buffer) => errors.push(chunk));
  child.on('error', reject);
  child.on('close', (code) => {
    if (code !== 0) {
      reject(new Error(Buffer.concat(errors).toString('utf8') || `ffmpeg exited with ${code}`));
      return;
    }
    const wav = Buffer.concat(chunks);
    const dataMarker = wav.indexOf(Buffer.from('data'));
    if (dataMarker < 0 || dataMarker + 8 >= wav.length) {
      reject(new Error('ffmpeg returned a WAV stream without a data chunk'));
      return;
    }
    resolve(wav.subarray(dataMarker + 8));
  });
});

/** One normalized voice-energy value per video frame. */
export async function readSpeechEnvelope(filePath: string, fps = 30): Promise<number[]> {
  try {
    const pcm = await decodeMonoPcm(filePath);
    const samplesPerFrame = Math.round(16000 / fps);
    const frameCount = Math.ceil((pcm.length / 2) / samplesPerFrame);
    const rms: number[] = [];
    for (let frame = 0; frame < frameCount; frame++) {
      const start = frame * samplesPerFrame;
      const end = Math.min(pcm.length / 2, start + samplesPerFrame);
      let energy = 0;
      for (let sample = start; sample < end; sample++) {
        const value = pcm.readInt16LE(sample * 2) / 32768;
        energy += value * value;
      }
      rms.push(end > start ? Math.sqrt(energy / (end - start)) : 0);
    }

    const peak = Math.max(...rms, 0.001);
    const normalized = rms.map((value) => value / peak);
    // Smooth over 3 frames so consonants do not flicker open/closed.
    return normalized.map((_, index) => {
      const from = Math.max(0, index - 1);
      const to = Math.min(normalized.length, index + 2);
      return normalized.slice(from, to).reduce((sum, value) => sum + value, 0) / (to - from);
    });
  } catch (error) {
    console.warn('[TTS] Could not create speech envelope; using text timing:', error);
    return [];
  }
}
