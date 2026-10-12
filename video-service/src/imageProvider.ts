import fs from 'fs';
import path from 'path';
import type {Slide} from './types';

const IMAGE_DIR = path.resolve(process.cwd(), 'out/images');
const DEFAULT_TIMEOUT_MS = 30_000;

type SupportedImage = {
  extension: 'jpg' | 'png' | 'webp';
};

function imageType(contentType: string | null): SupportedImage | null {
  const normalized = contentType?.split(';', 1)[0].trim().toLowerCase();
  switch (normalized) {
    case 'image/jpeg':
      return {extension: 'jpg'};
    case 'image/png':
      return {extension: 'png'};
    case 'image/webp':
      return {extension: 'webp'};
    default:
      return null;
  }
}

/**
 * Resolve optional AI illustrations before Chromium starts rendering.
 *
 * Remote images must never be loaded directly by Remotion: a 401/402 response
 * from an image provider would otherwise abort the complete video render. If
 * the provider is disabled or unavailable, SlideScene renders its local visual
 * fallback and the video can still complete.
 */
export async function prepareSlideImages(slides: Slide[], jobId: string): Promise<void> {
  const apiKey = process.env.POLLINATIONS_API_KEY?.trim();
  if (!apiKey) {
    console.log('[images] POLLINATIONS_API_KEY is not configured; using local slide visuals.');
    return;
  }

  await fs.promises.mkdir(IMAGE_DIR, {recursive: true});

  for (let index = 0; index < slides.length; index++) {
    const prompt = slides[index].imagePrompt?.trim();
    if (!prompt) continue;

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
      let response: Response;
      try {
        const url = new URL(`https://gen.pollinations.ai/image/${encodeURIComponent(prompt)}`);
        url.searchParams.set('model', process.env.POLLINATIONS_IMAGE_MODEL?.trim() || 'flux');
        url.searchParams.set('width', '640');
        url.searchParams.set('height', '360');
        url.searchParams.set('nologo', 'true');

        response = await fetch(url, {
          headers: {Authorization: `Bearer ${apiKey}`},
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timeout);
      }

      if (!response.ok) {
        throw new Error(`provider returned HTTP ${response.status}`);
      }

      const type = imageType(response.headers.get('content-type'));
      if (!type) {
        throw new Error(`provider returned unsupported content type: ${response.headers.get('content-type') ?? 'unknown'}`);
      }

      const filename = `${jobId}-${index}.${type.extension}`;
      await fs.promises.writeFile(path.join(IMAGE_DIR, filename), Buffer.from(await response.arrayBuffer()));

      const port = process.env.PORT ?? '3001';
      slides[index].imageUrl = `http://127.0.0.1:${port}/images/${filename}`;
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      console.warn(`[images] Slide ${index + 1} image unavailable (${reason}); using local visual fallback.`);
    }
  }
}
