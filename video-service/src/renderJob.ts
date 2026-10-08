import path from 'path';
import { v4 as uuidv4 } from 'uuid';
import { bundle } from '@remotion/bundler';
import { renderMedia, selectComposition } from '@remotion/renderer';
import { getTtsProvider } from './tts/TtsProvider';
import { getAvatarProvider, isLipSyncedAvatarRequired } from './avatar/AvatarProvider';
import { uploadVideoToSupabase } from './supabase';
import {prepareSlideImages} from './imageProvider';
import {prepareSceneVideos} from './sceneVideoProvider';
import type { GenerateVideoRequest, RenderJob } from './types';

/** In-memory job store (không cần DB ở giai đoạn này) */
const jobs = new Map<string, RenderJob>();

class TaskQueue {
  private queue: (() => Promise<void>)[] = [];
  private activeCount = 0;
  private concurrencyLimit: number;

  constructor(concurrencyLimit: number) {
    this.concurrencyLimit = concurrencyLimit;
  }

  enqueue(task: () => Promise<void>) {
    this.queue.push(task);
    this.processNext();
  }

  private processNext() {
    if (this.activeCount >= this.concurrencyLimit || this.queue.length === 0) {
      return;
    }
    const task = this.queue.shift();
    if (!task) return;
    this.activeCount++;
    task().finally(() => {
      this.activeCount--;
      this.processNext();
    });
  }
}

const maxConcurrent = parseInt(process.env.MAX_CONCURRENT_RENDERS ?? '1', 10);
const renderQueue = new TaskQueue(maxConcurrent);

/** Thư mục lưu video output */
const OUTPUT_DIR = path.resolve(process.cwd(), 'out');

/** Remotion composition entry point */
const REMOTION_ENTRY = path.resolve(process.cwd(), 'src/remotion/index.ts');

/**
 * Cache bundled path — bundle một lần khi server khởi động,
 * tái sử dụng cho tất cả các render job tiếp theo.
 * Đặt là `null` để lazy-init lần đầu tiên có job được gửi đến.
 */
let cachedBundlePath: string | null = null;

/**
 * Khởi tạo (hoặc lấy từ cache) Remotion Webpack bundle.
 * Quá trình bundle chạy 1 lần duy nhất, các job sau tái dùng lại.
 */
async function getBundlePath(): Promise<string> {
  if (cachedBundlePath) return cachedBundlePath;

  console.log('[bundle] Đang tạo Remotion bundle (chỉ chạy 1 lần)...');
  const bundled = await bundle({
    entryPoint: REMOTION_ENTRY,
    // Bật webpackOverride nếu cần tùy chỉnh webpack về sau
  });
  cachedBundlePath = bundled;
  console.log(`[bundle] Bundle xong → ${bundled}`);
  return bundled;
}

export async function createRenderJob(request: GenerateVideoRequest): Promise<string> {
  const existing = Array.from(jobs.values()).find(
    (job) => job.lectureId === request.lectureId
      && (job.status === 'pending' || job.status === 'processing'),
  );
  if (existing) {
    console.log(`[render] Reusing active job ${existing.jobId} for lecture ${request.lectureId}`);
    return existing.jobId;
  }

  const jobId = uuidv4();
  const now = new Date();

  const job: RenderJob = {
    jobId,
    lectureId: request.lectureId,
    status: 'pending',
    progress: 0,
    createdAt: now,
    updatedAt: now,
  };
  jobs.set(jobId, job);

  // Bắt đầu render không đợi (fire-and-forget), thông qua queue
  renderQueue.enqueue(() => runRenderPipeline(jobId, request));

  return jobId;
}

/**
 * Lấy thông tin job theo jobId.
 */
export function getJob(jobId: string): RenderJob | undefined {
  return jobs.get(jobId);
}

/**
 * Lấy danh sách tất cả jobs (dùng để debug).
 */
export function getAllJobs(): RenderJob[] {
  return Array.from(jobs.values());
}

// ─────────────────────────────────────────────────────────────────────────────
// INTERNAL: render pipeline
// ─────────────────────────────────────────────────────────────────────────────

async function runRenderPipeline(jobId: string, request: GenerateVideoRequest): Promise<void> {
  updateJob(jobId, {status: 'processing', progress: 0.03});

  try {
    const avatarProvider = getAvatarProvider();
    const requireLipSyncedAvatar = isLipSyncedAvatarRequired();
    if (requireLipSyncedAvatar && avatarProvider.assertReady) {
      await avatarProvider.assertReady();
    }

    // ─── 1. Lấy Webpack bundle (có cache) ──────────────────────────────────
    const bundlePath = await getBundlePath();
    updateJob(jobId, {progress: 0.08});

    const tts = getTtsProvider();
    const fps = 30;

    await prepareSlideImages(request.slides, jobId);
    updateJob(jobId, {progress: 0.12});

    // Optional Veo mode: turn each visual into a full-screen animated scene.
    const sceneWarnings = await prepareSceneVideos(request.slides, jobId, (completed, total) => {
      updateJob(jobId, {progress: 0.12 + (completed / total) * 0.18});
    });
    if (sceneWarnings.length > 0) updateJob(jobId, {warnings: sceneWarnings});
    updateJob(jobId, {progress: 0.3});

    // ─── 2. Tính thời lượng mỗi slide từ TTS & Sinh Avatar 3D (Giải pháp 2) ──
    const slideDurationsFrames: number[] = [];
    for (let index = 0; index < request.slides.length; index++) {
      const slide = request.slides[index];
      const result = await tts.synthesize(slide.narrationText);
      slide.audioUrl = result.audioUrl;
      slideDurationsFrames.push(Math.round((result.durationMs / 1000) * fps));

      // A cinematic background and a talking teacher are separate layers.
      // Generate the lip-synced presenter for both remote Veo scenes and the
      // local cinematic fallback.
      let avatarVid: string | null = null;
      try {
        avatarVid = await avatarProvider.generateAvatarVideo(
          slide,
          index,
          jobId,
          result.durationMs,
        );
      } catch (avatarErr) {
        if (requireLipSyncedAvatar) throw avatarErr;
        console.warn(`[render] Lỗi sinh avatar 3D cho slide ${index + 1}:`, avatarErr);
      }

      if (!avatarVid && requireLipSyncedAvatar) {
        throw new Error(
          `Slide ${index + 1} không có video giáo viên lip-sync từ provider ${avatarProvider.name}.`,
        );
      }
      if (avatarVid) {
        slide.avatarVideoUrl = avatarVid;
        console.log(`[render] Slide ${index + 1}: Đã gắn avatar 3D video -> ${avatarVid}`);
      }

      updateJob(jobId, {progress: 0.3 + ((index + 1) / request.slides.length) * 0.15});
    }

    const totalFrames = slideDurationsFrames.reduce((a, b) => a + b, 0);

    const inputProps = {
      slides: request.slides,
      slideDurationsFrames,
    };

    console.log(`[render] Job ${jobId}: ${request.slides.length} slides, ${totalFrames} frames total`);

    // ─── 3. Tìm composition từ bundle ──────────────────────────────────────
    const composition = await selectComposition({
      serveUrl: bundlePath,
      id: 'SlideComposition',
      inputProps,
    });

    // Override duration dựa trên TTS thực tế
    composition.durationInFrames = totalFrames;

    // ─── 4. Đảm bảo output dir tồn tại ────────────────────────────────────
    const fs = await import('fs');
    await fs.promises.mkdir(OUTPUT_DIR, { recursive: true });

    const outputPath = path.join(OUTPUT_DIR, `${jobId}.mp4`);

    // ─── 5. Render video bằng Remotion (headless Chromium + FFmpeg) ────────
    console.log(`[render] Bắt đầu renderMedia → ${outputPath}`);
    await renderMedia({
      composition,
      serveUrl: bundlePath,
      codec: 'h264',
      outputLocation: outputPath,
      inputProps,
      timeoutInMilliseconds: 120000,
      onProgress: ({progress}) => updateJob(jobId, {progress: 0.45 + progress * 0.5}),
    });

    // ─── 6. Upload lên Supabase (nếu được bật) hoặc dùng URL local ──────────
    let videoUrl: string;

    if (process.env.UPLOAD_TO_SUPABASE === 'true') {
      console.log(`[render] Đang upload video lên Supabase...`);
      videoUrl = await uploadVideoToSupabase(outputPath, jobId);
      // Xóa file local sau khi upload thành công để tiết kiệm disk
      const fsModule = await import('fs');
      await fsModule.promises.unlink(outputPath).catch(() => { /* ignore */ });
    } else {
      // Fallback: serve trực tiếp từ video-service (chỉ dùng khi dev local)
      const port = process.env.PORT ?? '3001';
      videoUrl = `http://localhost:${port}/videos/${jobId}.mp4`;
    }

    updateJob(jobId, {
      status: 'done',
      videoPath: outputPath,
      videoUrl,
      progress: 1,
    });

    console.log(`[✓] Job ${jobId} done → ${videoUrl}`);
  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : String(err);
    console.error(`[✗] Job ${jobId} failed:`, errorMessage);
    updateJob(jobId, { status: 'failed', error: errorMessage });
  }
}

function updateJob(jobId: string, updates: Partial<RenderJob>): void {
  const job = jobs.get(jobId);
  if (!job) return;
  Object.assign(job, updates, { updatedAt: new Date() });
  jobs.set(jobId, job);
}
