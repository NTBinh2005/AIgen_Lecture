import 'dotenv/config';
import express from 'express';
import path from 'path';
import { createRenderJob, getJob, getAllJobs } from './renderJob';
import type { GenerateVideoRequest } from './types';

const app = express();
const PORT = parseInt(process.env.PORT ?? '3001', 10);
const OUTPUT_DIR = path.resolve(process.cwd(), 'out');

// ─── Middleware ────────────────────────────────────────────────────────────────
app.use(express.json());

// CORS cho phép backend Spring Boot gọi sang
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') {
    res.sendStatus(204);
    return;
  }
  next();
});

// Serve static video files từ output folder
app.use('/videos', express.static(OUTPUT_DIR));
app.use('/audio', express.static(path.join(OUTPUT_DIR, 'audio')));

// ─── Routes ───────────────────────────────────────────────────────────────────

/**
 * GET /
 * Root path redirect to health
 */
app.get('/', (_req, res) => {
  res.redirect('/health');
});

/**
 * GET /health
 * Health check endpoint
 */
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', service: 'video-service', timestamp: new Date().toISOString() });
});

/**
 * POST /generate-video
 * Tạo mới một video render job (bất đồng bộ).
 *
 * Request body:
 * {
 *   "lectureId": "string",
 *   "slides": [
 *     {
 *       "title": "string",
 *       "bulletPoints": ["string"],
 *       "narrationText": "string"
 *     }
 *   ]
 * }
 *
 * Response 202:
 * {
 *   "jobId": "uuid",
 *   "status": "pending",
 *   "message": "Video render đã bắt đầu, poll /video-status/:jobId để kiểm tra"
 * }
 */
app.post('/generate-video', async (req, res) => {
  try {
    const body = req.body as Partial<GenerateVideoRequest>;

    // Validate input
    if (!body.lectureId || typeof body.lectureId !== 'string') {
      res.status(400).json({ error: 'lectureId là bắt buộc và phải là string' });
      return;
    }

    if (!Array.isArray(body.slides) || body.slides.length === 0) {
      res.status(400).json({ error: 'slides là bắt buộc và phải là mảng không rỗng' });
      return;
    }

    for (let i = 0; i < body.slides.length; i++) {
      const slide = body.slides[i];
      if (!slide.title || !slide.narrationText || !Array.isArray(slide.bulletPoints)) {
        res.status(400).json({
          error: `Slide[${i}] thiếu field. Yêu cầu: title (string), bulletPoints (string[]), narrationText (string)`,
        });
        return;
      }
    }

    const jobId = await createRenderJob(body as GenerateVideoRequest);

    res.status(202).json({
      jobId,
      status: 'pending',
      message: 'Video render đã bắt đầu. Poll GET /video-status/:jobId để kiểm tra tiến trình.',
    });
  } catch (err) {
    if (err instanceof Error && err.message === 'Too many concurrent renders') {
      res.status(429).json({ error: 'Too many concurrent renders' });
      return;
    }
    console.error('[POST /generate-video] Error:', err);
    res.status(500).json({ error: 'Lỗi server nội bộ khi tạo render job' });
  }
});

/**
 * GET /video-status/:jobId
 * Lấy trạng thái của một render job.
 *
 * Response 200:
 * {
 *   "jobId": "uuid",
 *   "lectureId": "string",
 *   "status": "pending" | "processing" | "done" | "failed",
 *   "videoUrl": "string (nếu done)",
 *   "error": "string (nếu failed)"
 * }
 */
app.get('/video-status/:jobId', (req, res) => {
  const { jobId } = req.params;
  const job = getJob(jobId);

  if (!job) {
    res.status(404).json({ error: `Không tìm thấy job với jobId: ${jobId}` });
    return;
  }

  res.json({
    jobId: job.jobId,
    lectureId: job.lectureId,
    status: job.status,
    videoUrl: job.videoUrl ?? null,
    error: job.error ?? null,
    createdAt: job.createdAt.toISOString(),
    updatedAt: job.updatedAt.toISOString(),
  });
});

/**
 * GET /jobs (debug only)
 * Liệt kê tất cả jobs hiện có trong memory.
 */
app.get('/jobs', (_req, res) => {
  const allJobs = getAllJobs().map((j) => ({
    jobId: j.jobId,
    lectureId: j.lectureId,
    status: j.status,
    videoUrl: j.videoUrl ?? null,
    createdAt: j.createdAt.toISOString(),
    updatedAt: j.updatedAt.toISOString(),
  }));
  res.json({ count: allJobs.length, jobs: allJobs });
});

// ─── Start Server ──────────────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`
╔══════════════════════════════════════════════════════╗
║          video-service đang chạy                     ║
╠══════════════════════════════════════════════════════╣
║  URL:         http://localhost:${PORT}                  ║
║  Health:      GET  /health                           ║
║  Generate:    POST /generate-video                   ║
║  Status:      GET  /video-status/:jobId              ║
║  Jobs list:   GET  /jobs                             ║
║  TTS mode:    ${(process.env.TTS_PROVIDER ?? 'mock').padEnd(22)}                     ║
╚══════════════════════════════════════════════════════╝
  `.trim());
});

export default app;
