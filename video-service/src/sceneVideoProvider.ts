import fs from 'fs';
import path from 'path';
import type {Slide} from './types';

const SCENE_DIR = path.resolve(process.cwd(), 'out', 'scenes');
const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta';
const DEFAULT_POLL_INTERVAL_MS = 10_000;
const DEFAULT_TIMEOUT_MS = 7 * 60_000;
const MAX_DOWNLOAD_BYTES = 150 * 1024 * 1024;

type LongRunningOperation = {
  name?: string;
  done?: boolean;
  error?: {message?: string};
  response?: {
    generateVideoResponse?: {
      generatedSamples?: Array<{video?: {uri?: string}}>;
    };
  };
};

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function responseJson<T>(response: Response, label: string): Promise<T> {
  if (!response.ok) {
    const raw = (await response.text()).slice(0, 4_000);
    let details = raw;
    try {
      const parsed = JSON.parse(raw) as {error?: {status?: string; message?: string}};
      details = [parsed.error?.status, parsed.error?.message].filter(Boolean).join(': ') || raw;
    } catch {
      // Preserve the provider's plain-text response.
    }
    throw new Error(`${label} returned HTTP ${response.status}: ${details}`);
  }
  return response.json() as Promise<T>;
}

async function loadStartingImage(imageUrl?: string): Promise<Record<string, unknown> | undefined> {
  if (!imageUrl) return undefined;

  const response = await fetch(imageUrl);
  if (!response.ok) throw new Error(`could not read starting image (HTTP ${response.status})`);
  const mimeType = response.headers.get('content-type')?.split(';', 1)[0] ?? 'image/png';
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(mimeType)) {
    throw new Error(`unsupported starting image type: ${mimeType}`);
  }
  const data = Buffer.from(await response.arrayBuffer()).toString('base64');
  return {inlineData: {mimeType, data}};
}

function buildPrompt(slide: Slide): string {
  const subject = slide.imagePrompt?.trim() || `${slide.title}. ${slide.teachingGoal ?? ''}`;
  return [
    subject,
    'Create an eight-second cinematic 3D educational animation in a polished family-friendly animated-film style.',
    'Use expressive character or object motion, clear visual storytelling, gentle camera movement, rich lighting, and strong depth.',
    'Landscape 16:9 composition. Keep the main action inside the center safe area.',
    'No written words, captions, logos, watermarks, talking, or narration. Ambient sound only.',
  ].join(' ');
}

async function generateVeoScene(slide: Slide, jobId: string, slideIndex: number): Promise<string> {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey) throw new Error('GEMINI_API_KEY is not configured');

  const model = process.env.VEO_MODEL?.trim() || 'veo-3.1-fast-generate-preview';
  const instance: Record<string, unknown> = {prompt: buildPrompt(slide)};
  try {
    const image = await loadStartingImage(slide.imageUrl);
    if (image) instance.image = image;
  } catch (error) {
    console.warn(`[scenes] Slide ${slideIndex + 1}: starting image unavailable; using text-to-video.`, error);
  }

  const startResponse = await fetch(`${GEMINI_API_BASE}/models/${encodeURIComponent(model)}:predictLongRunning`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json', 'x-goog-api-key': apiKey},
    body: JSON.stringify({
      instances: [instance],
      parameters: {aspectRatio: '16:9', durationSeconds: 8, resolution: '720p'},
    }),
  });
  let operation = await responseJson<LongRunningOperation>(startResponse, 'Veo generation');
  if (!operation.name) throw new Error('Veo returned no operation name');

  const deadline = Date.now() + Number(process.env.VEO_TIMEOUT_MS || DEFAULT_TIMEOUT_MS);
  while (!operation.done) {
    if (Date.now() >= deadline) throw new Error('Veo generation timed out');
    await sleep(Number(process.env.VEO_POLL_INTERVAL_MS || DEFAULT_POLL_INTERVAL_MS));
    const statusResponse = await fetch(`${GEMINI_API_BASE}/${operation.name}`, {
      headers: {'x-goog-api-key': apiKey},
    });
    operation = await responseJson<LongRunningOperation>(statusResponse, 'Veo status');
  }

  if (operation.error) throw new Error(operation.error.message || 'Veo generation failed');
  const videoUri = operation.response?.generateVideoResponse?.generatedSamples?.[0]?.video?.uri;
  if (!videoUri) throw new Error('Veo completed without a video URI');

  const videoResponse = await fetch(videoUri, {headers: {'x-goog-api-key': apiKey}});
  if (!videoResponse.ok) throw new Error(`Veo download returned HTTP ${videoResponse.status}`);
  const declaredLength = Number(videoResponse.headers.get('content-length') ?? 0);
  if (declaredLength > MAX_DOWNLOAD_BYTES) throw new Error('generated scene is too large');
  const bytes = Buffer.from(await videoResponse.arrayBuffer());
  if (bytes.byteLength > MAX_DOWNLOAD_BYTES) throw new Error('generated scene exceeded size limit');

  await fs.promises.mkdir(SCENE_DIR, {recursive: true});
  const filename = `${jobId}-${slideIndex}.mp4`;
  await fs.promises.writeFile(path.join(SCENE_DIR, filename), bytes);
  const port = process.env.PORT ?? '3001';
  return `http://127.0.0.1:${port}/scenes/${filename}`;
}

/**
 * Generates optional cinematic backgrounds. A provider failure affects only
 * that scene: Remotion falls back to the existing slide-and-teacher layout.
 */
export async function prepareSceneVideos(
  slides: Slide[],
  jobId: string,
  onProgress?: (completed: number, total: number) => void,
): Promise<string[]> {
  const warnings: string[] = [];
  const provider = (process.env.SCENE_VIDEO_PROVIDER ?? 'none').trim().toLowerCase();
  if (provider === 'none' || provider === 'off' || provider === 'disabled') {
    console.log('[scenes] Cinematic scene generation is disabled; using slide layout.');
    return warnings;
  }
  if (provider !== 'veo') {
    const warning = `Unknown scene video provider "${provider}"; using slide layout.`;
    console.warn(`[scenes] ${warning}`);
    warnings.push(warning);
    return warnings;
  }
  if (!process.env.GEMINI_API_KEY?.trim()) {
    const warning = 'GEMINI_API_KEY is not configured; using local cinematic animation.';
    console.warn(`[scenes] ${warning}`);
    warnings.push(warning);
    slides.forEach((slide) => { slide.cinematicMode = true; });
    return warnings;
  }

  for (let index = 0; index < slides.length; index++) {
    // Even if the remote generation fails, render a visibly animated local
    // cinematic scene instead of silently returning to the static slide UI.
    slides[index].cinematicMode = true;
    try {
      console.log(`[scenes] Generating Veo scene ${index + 1}/${slides.length}...`);
      slides[index].sceneVideoUrl = await generateVeoScene(slides[index], jobId, index);
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      console.warn(`[scenes] Slide ${index + 1} unavailable (${reason}); using local cinematic animation.`);
      warnings.push(`Scene ${index + 1}: ${reason}`);
      if (/HTTP 429|RESOURCE_EXHAUSTED|quota/i.test(reason)) {
        for (let remaining = index + 1; remaining < slides.length; remaining++) {
          slides[remaining].cinematicMode = true;
        }
        warnings.push('Veo quota is exhausted; remaining scenes use local cinematic animation.');
        onProgress?.(slides.length, slides.length);
        break;
      }
    }
    onProgress?.(index + 1, slides.length);
  }
  return warnings;
}
