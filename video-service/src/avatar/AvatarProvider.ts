import fs from 'fs';
import http from 'http';
import https from 'https';
import path from 'path';
import type { Slide } from '../types';

/**
 * Thư mục lưu avatar videos được download về local,
 * để Remotion headless Chromium fetch từ cùng port (3001) thay vì port 5000.
 */
const AVATAR_OUT_DIR = path.resolve(process.cwd(), 'out', 'avatar');
const DOWNLOAD_TIMEOUT_MS = 60_000;
const MAX_AVATAR_BYTES = 100 * 1024 * 1024;
const HEALTH_CHECK_TIMEOUT_MS = 5_000;
const DEFAULT_AVATAR_GENERATION_TIMEOUT_MS = 20 * 60_000;
const MAX_JSON_RESPONSE_BYTES = 2 * 1024 * 1024;

export function isLipSyncedAvatarRequired(): boolean {
  return (process.env.REQUIRE_LIP_SYNCED_AVATAR ?? 'true').trim().toLowerCase() !== 'false';
}

function avatarGenerationTimeoutMs(): number {
  const configured = Number(process.env.AVATAR_GENERATION_TIMEOUT_MS);
  return Number.isFinite(configured) && configured >= 60_000
    ? configured
    : DEFAULT_AVATAR_GENERATION_TIMEOUT_MS;
}

/**
 * Node's built-in fetch aborts when a server takes roughly five minutes to
 * send response headers. SadTalker legitimately exceeds that on CPU/smaller
 * GPUs, so use the native HTTP client with an explicit inference timeout.
 */
function postLongRunningJson(
  url: string,
  payload: Record<string, unknown>,
): Promise<{statusCode: number; body: string}> {
  const target = new URL(url);
  const transport = target.protocol === 'https:' ? https : http;
  const body = JSON.stringify(payload);
  const timeoutMs = avatarGenerationTimeoutMs();

  return new Promise((resolve, reject) => {
    const request = transport.request(
      target,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
        },
      },
      (response) => {
        const chunks: Buffer[] = [];
        let receivedBytes = 0;

        response.on('data', (chunk: Buffer) => {
          receivedBytes += chunk.length;
          if (receivedBytes > MAX_JSON_RESPONSE_BYTES) {
            request.destroy(new Error('SadTalker response exceeded the JSON size limit'));
            return;
          }
          chunks.push(chunk);
        });
        response.on('end', () => {
          resolve({
            statusCode: response.statusCode ?? 0,
            body: Buffer.concat(chunks).toString('utf8'),
          });
        });
        response.on('error', reject);
      },
    );

    request.setTimeout(timeoutMs, () => {
      request.destroy(
        new Error(`SadTalker generation timed out after ${Math.round(timeoutMs / 60_000)} minutes`),
      );
    });
    request.on('error', reject);
    request.end(body);
  });
}

async function ensureAvatarDir() {
  await fs.promises.mkdir(AVATAR_OUT_DIR, { recursive: true });
}

/**
 * Download một URL về local và trả về đường dẫn file.
 * Return null nếu download thất bại.
 */
async function downloadToLocal(url: string, destPath: string): Promise<boolean> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), DOWNLOAD_TIMEOUT_MS);
  const partialPath = `${destPath}.part`;
  try {
    const res = await fetch(url, {signal: controller.signal});
    if (!res.ok) {
      console.error(`[AvatarProvider] Download failed (${res.status}): ${url}`);
      return false;
    }
    const declaredLength = Number(res.headers.get('content-length') ?? 0);
    if (declaredLength > MAX_AVATAR_BYTES) {
      console.error(`[AvatarProvider] Avatar video is too large: ${declaredLength} bytes`);
      return false;
    }
    const buffer = await res.arrayBuffer();
    if (buffer.byteLength > MAX_AVATAR_BYTES) {
      console.error(`[AvatarProvider] Avatar video exceeded ${MAX_AVATAR_BYTES} bytes`);
      return false;
    }
    await fs.promises.writeFile(partialPath, Buffer.from(buffer));
    await fs.promises.rename(partialPath, destPath);
    return true;
  } catch (err) {
    console.error(`[AvatarProvider] Download error from ${url}:`, err);
    return false;
  } finally {
    clearTimeout(timeout);
    await fs.promises.unlink(partialPath).catch(() => undefined);
  }
}

function toInternalMediaUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const internalBase = process.env.VIDEO_SERVICE_INTERNAL_URL?.trim().replace(/\/$/, '');
  if (!internalBase) return url;
  try {
    const source = new URL(url);
    if (!['localhost', '127.0.0.1', '::1'].includes(source.hostname)) return url;
    const internal = new URL(internalBase);
    source.protocol = internal.protocol;
    source.host = internal.host;
    return source.toString();
  } catch {
    return url;
  }
}

async function cacheAvatarVideo(
  remoteUrl: string,
  jobId: string,
  slideIndex: number,
): Promise<string | null> {
  await ensureAvatarDir();
  const filename = `${jobId}_slide_${slideIndex}.mp4`;
  const localPath = path.join(AVATAR_OUT_DIR, filename);
  const downloaded = await downloadToLocal(remoteUrl, localPath);
  if (!downloaded) return null;

  const port = process.env.PORT ?? '3001';
  return `http://127.0.0.1:${port}/avatar/${filename}`;
}

export interface AvatarProvider {
  name: string;
  /** Fail fast before rendering expensive scene assets when the provider is unavailable. */
  assertReady?(): Promise<void>;
  generateAvatarVideo(
    slide: Slide,
    slideIndex: number,
    jobId: string,
    durationMs: number,
  ): Promise<string | null>;
}

/**
 * Fallback / Mock 3D Avatar Provider.
 * Serves a 3D animated talking avatar loop or creates a demo 3D video clip.
 */
export class Mock3dAvatarProvider implements AvatarProvider {
  name = 'mock-3d';

  async generateAvatarVideo(
    slide: Slide,
    slideIndex: number,
    jobId: string,
    durationMs: number,
  ): Promise<string | null> {
    console.log(`[AvatarProvider: Mock3D] Generating 3D avatar video for slide ${slideIndex + 1}...`);

    // If a custom local 3D avatar video is available in public/models/avatar3d.mp4, use it
    const public3dPath = path.join(process.cwd(), 'public', 'avatar3d.mp4');
    if (fs.existsSync(public3dPath)) {
      const port = process.env.PORT ?? '3001';
      return `http://localhost:${port}/avatar3d.mp4`;
    }

    // Return null to let Avatar component use the procedural 3D/2D animation fallback
    return null;
  }
}

/**
 * HeyGen AI 3D Talking Avatar API Provider.
 * Documentation: https://docs.heygen.com
 */
export class HeyGenAvatarProvider implements AvatarProvider {
  name = 'heygen';

  private apiKey: string;
  private avatarId: string;

  constructor() {
    this.apiKey = process.env.HEYGEN_API_KEY ?? '';
    this.avatarId = process.env.HEYGEN_AVATAR_ID ?? 'Daisy-innocent-20220818';
  }

  async generateAvatarVideo(
    slide: Slide,
    slideIndex: number,
    jobId: string,
    durationMs: number,
  ): Promise<string | null> {
    if (!this.apiKey) {
      console.warn('[AvatarProvider: HeyGen] HEYGEN_API_KEY chưa được cấu hình. Bỏ qua sinh avatar 3D.');
      return null;
    }

    try {
      console.log(`[AvatarProvider: HeyGen] Sending request for slide ${slideIndex + 1}...`);
      const response = await fetch('https://api.heygen.com/v2/video/generate', {
        method: 'POST',
        headers: {
          'X-Api-Key': this.apiKey,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          video_inputs: [
            {
              character: {
                type: 'avatar',
                avatar_id: this.avatarId,
                avatar_style: 'normal',
              },
              voice: {
                type: 'text',
                input_text: slide.narrationText,
                voice_id: 'vi-VN-Standard-A', // Giọng tiếng Việt
              },
            },
          ],
          dimension: {
            width: 720,
            height: 720,
          },
        }),
      });

      if (!response.ok) {
        const errText = await response.text();
        console.error(`[AvatarProvider: HeyGen] API error (${response.status}):`, errText);
        return null;
      }

      const data = (await response.json()) as { data?: { video_id?: string } };
      const videoId = data.data?.video_id;

      if (!videoId) {
        console.error('[AvatarProvider: HeyGen] Không nhận được video_id.');
        return null;
      }

      // Poll HeyGen API until video status is completed
      const remoteUrl = await this.pollVideoStatus(videoId);
      return remoteUrl ? cacheAvatarVideo(remoteUrl, jobId, slideIndex) : null;
    } catch (err) {
      console.error('[AvatarProvider: HeyGen] Lỗi khi tạo 3D avatar:', err);
      return null;
    }
  }

  private async pollVideoStatus(videoId: string): Promise<string | null> {
    const maxAttempts = 30;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 3000));
      try {
        const res = await fetch(`https://api.heygen.com/v1/video_status.get?video_id=${videoId}`, {
          headers: { 'X-Api-Key': this.apiKey },
        });
        if (!res.ok) continue;
        const result = (await res.json()) as { data?: { status?: string; video_url?: string } };
        if (result.data?.status === 'completed' && result.data.video_url) {
          console.log(`[AvatarProvider: HeyGen] Video 3D avatar sẵn sàng: ${result.data.video_url}`);
          return result.data.video_url;
        }
        if (result.data?.status === 'failed') {
          console.error('[AvatarProvider: HeyGen] Render video 3D avatar bị lỗi từ HeyGen.');
          return null;
        }
      } catch (e) {
        console.warn(`[AvatarProvider: HeyGen] Lỗi khi poll status (${attempt + 1}/${maxAttempts}):`, e);
      }
    }
    return null;
  }
}

/**
 * D-ID 3D Talking Head API Provider.
 * Documentation: https://docs.d-id.com
 */
export class DIdAvatarProvider implements AvatarProvider {
  name = 'd-id';

  private apiKey: string;
  private sourceUrl: string;

  constructor() {
    this.apiKey = process.env.DID_API_KEY ?? '';
    // Ảnh mô hình 3D mặc định nếu dùng D-ID
    this.sourceUrl = process.env.AVATAR_IMAGE_URL ?? 'https://create-images-results.d-id.com/DefaultPresenters/Noam_m/image.jpeg';
  }

  async generateAvatarVideo(
    slide: Slide,
    slideIndex: number,
    jobId: string,
    durationMs: number,
  ): Promise<string | null> {
    if (!this.apiKey) {
      console.warn('[AvatarProvider: D-ID] DID_API_KEY chưa được cấu hình. Bỏ qua.');
      return null;
    }

    try {
      console.log(`[AvatarProvider: D-ID] Sending request for slide ${slideIndex + 1}...`);
      const response = await fetch('https://api.d-id.com/talks', {
        method: 'POST',
        headers: {
          Authorization: `Basic ${this.apiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          source_url: this.sourceUrl,
          script: {
            type: 'text',
            subtitles: 'false',
            provider: { type: 'microsoft', voice_id: 'vi-VN-HoaiMyNeural' },
            input: slide.narrationText,
          },
          config: {
            fluent: 'false',
            pad_audio: '0.0',
          },
        }),
      });

      if (!response.ok) {
        const errText = await response.text();
        console.error(`[AvatarProvider: D-ID] API error (${response.status}):`, errText);
        return null;
      }

      const data = (await response.json()) as { id?: string };
      if (!data.id) return null;

      // Poll D-ID for result
      const remoteUrl = await this.pollTalkStatus(data.id);
      return remoteUrl ? cacheAvatarVideo(remoteUrl, jobId, slideIndex) : null;
    } catch (err) {
      console.error('[AvatarProvider: D-ID] Lỗi khi tạo 3D avatar:', err);
      return null;
    }
  }

  private async pollTalkStatus(talkId: string): Promise<string | null> {
    const maxAttempts = 30;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 3000));
      try {
        const res = await fetch(`https://api.d-id.com/talks/${talkId}`, {
          headers: { Authorization: `Basic ${this.apiKey}` },
        });
        if (!res.ok) continue;
        const result = (await res.json()) as { status?: string; result_url?: string };
        if (result.status === 'done' && result.result_url) {
          console.log(`[AvatarProvider: D-ID] Video 3D avatar sẵn sàng: ${result.result_url}`);
          return result.result_url;
        }
        if (result.status === 'error') {
          console.error('[AvatarProvider: D-ID] Lỗi khi render D-ID talk.');
          return null;
        }
      } catch (e) {
        console.warn(`[AvatarProvider: D-ID] Lỗi khi poll talk (${attempt + 1}/${maxAttempts}):`, e);
      }
    }
    return null;
  }
}

/**
 * SadTalker / Custom Self-hosted Python AI Service Provider.
 *
 * Sau khi Python service tạo video, provider sẽ:
 * 1. Download MP4 từ http://localhost:5000/outputs/... về out/avatar/ của video-service
 * 2. Serve qua http://localhost:3001/avatar/<filename>
 * → Remotion headless Chromium fetch được (cùng origin với audio)
 */
export class SadTalkerAvatarProvider implements AvatarProvider {
  name = 'sadtalker';

  private apiUrl: string;

  constructor() {
    this.apiUrl = process.env.SADTALKER_API_URL ?? 'http://localhost:5000/api/talk';
  }

  async assertReady(): Promise<void> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), HEALTH_CHECK_TIMEOUT_MS);
    try {
      const healthUrl = new URL('/health', this.apiUrl).toString();
      const response = await fetch(healthUrl, {signal: controller.signal});
      if (!response.ok) {
        throw new Error(`health check returned HTTP ${response.status}`);
      }

      const data = (await response.json()) as {
        sadTalkerReady?: boolean;
        selectedEngine?: string;
        sadTalkerIssues?: string[];
      };
      if (!data.sadTalkerReady || data.selectedEngine !== 'sadtalker') {
        const issues = data.sadTalkerIssues?.filter(Boolean).join('; ');
        throw new Error(
          `engine=${data.selectedEngine ?? 'unknown'}, sadTalkerReady=${String(data.sadTalkerReady)}` +
          (issues ? ` (${issues})` : ''),
        );
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      throw new Error(
        `SadTalker chưa sẵn sàng tại ${this.apiUrl}. ` +
        `Hãy chạy avatar-ai-service với AVATAR_ENGINE=sadtalker. Chi tiết: ${reason}`,
      );
    } finally {
      clearTimeout(timeout);
    }
  }

  /**
   * The Python service can return its Docker hostname even when this Node
   * service is running directly on Windows. For service-local URLs, use the
   * same origin that successfully served /api/talk.
   */
  private resolveAvatarOutputUrl(remoteUrl: string): string {
    try {
      const outputUrl = new URL(remoteUrl);
      const apiUrl = new URL(this.apiUrl);
      const localServiceHosts = new Set(['avatar-ai-service', 'localhost', '127.0.0.1', '::1']);
      if (localServiceHosts.has(outputUrl.hostname)) {
        outputUrl.protocol = apiUrl.protocol;
        outputUrl.host = apiUrl.host;
      }
      return outputUrl.toString();
    } catch {
      return remoteUrl;
    }
  }

  async generateAvatarVideo(
    slide: Slide,
    slideIndex: number,
    jobId: string,
    durationMs: number,
  ): Promise<string | null> {
    try {
      console.log(`[AvatarProvider: SadTalker] Calling local AI service at ${this.apiUrl}...`);
      const response = await postLongRunningJson(this.apiUrl, {
          audioUrl: toInternalMediaUrl(slide.audioUrl),
          text: slide.narrationText,
          slideIndex,
          jobId,
          durationMs,
          avatarImageUrl: process.env.AVATAR_IMAGE_URL?.trim() || undefined,
          lessonPhase: slide.lessonPhase,
          teachingGoal: slide.teachingGoal,
          teacherAction: slide.teacherAction,
      });

      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new Error(
          `SadTalker API returned HTTP ${response.statusCode}: ${response.body.slice(0, 1_000)}`,
        );
      }

      const data = JSON.parse(response.body) as {
        avatarVideoUrl?: string;
        status?: string;
        engine?: string;
      };
      const requireLipSync = isLipSyncedAvatarRequired();
      if (requireLipSync && data.engine !== 'sadtalker') {
        throw new Error(
          `SadTalker service returned engine=${data.engine ?? 'unknown'}; ` +
          'REQUIRE_LIP_SYNCED_AVATAR=true nên không chấp nhận ảnh động fallback.',
        );
      }
      const remoteUrl = data.avatarVideoUrl;
      if (!remoteUrl) {
        throw new Error('SadTalker API không trả về avatarVideoUrl.');
      }

      const localUrl = await cacheAvatarVideo(
        this.resolveAvatarOutputUrl(remoteUrl),
        jobId,
        slideIndex,
      );
      if (!localUrl) {
        throw new Error('Không tải được MP4 avatar từ SadTalker service.');
      }
      console.log(`[AvatarProvider: SadTalker] ✅ Avatar video ready → ${localUrl}`);
      return localUrl;
    } catch (err) {
      if (isLipSyncedAvatarRequired()) {
        const reason = err instanceof Error ? err.message : String(err);
        throw new Error(`Không thể tạo avatar lip-sync cho slide ${slideIndex + 1}: ${reason}`);
      }
      console.warn(`[AvatarProvider: SadTalker] Không kết nối được SadTalker API:`, err);
      return null;
    }
  }
}

/**
 * Factory function to retrieve the configured 3D Avatar Provider.
 */
export function getAvatarProvider(): AvatarProvider {
  const providerName = (process.env.AVATAR_PROVIDER ?? 'mock').toLowerCase();

  switch (providerName) {
    case 'heygen':
      return new HeyGenAvatarProvider();
    case 'd-id':
    case 'did':
      return new DIdAvatarProvider();
    case 'sadtalker':
      return new SadTalkerAvatarProvider();
    case 'mock':
    case 'mock-3d':
    default:
      return new Mock3dAvatarProvider();
  }
}
