import path from 'path';
import { v4 as uuidv4 } from 'uuid';
import { bundle } from '@remotion/bundler';
import { renderMedia, selectComposition } from '@remotion/renderer';
import { getTtsProvider } from './tts/TtsProvider';
import { uploadVideoToSupabase } from './supabase';
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
  const jobId = uuidv4();
  const now = new Date();

  const job: RenderJob = {
    jobId,
    lectureId: request.lectureId,
    status: 'pending',
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
  updateJob(jobId, { status: 'processing' });

  try {
    // ─── 1. Lấy Webpack bundle (có cache) ──────────────────────────────────
    const bundlePath = await getBundlePath();

    const tts = getTtsProvider();
    const fps = 30;

    // ─── 2. Tính thời lượng mỗi slide từ TTS ──────────────────────────────
    const slideDurationsFrames: number[] = [];
    for (const slide of request.slides) {
      const result = await tts.synthesize(slide.narrationText);
      slide.audioUrl = result.audioUrl;
      slideDurationsFrames.push(Math.round((result.durationMs / 1000) * fps));
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
      timeoutInMilliseconds: 120000, // Tăng timeout lên 2 phút cho AI Image Generator
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
