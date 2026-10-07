/** Một slide trong bài giảng */
export interface Slide {
  /** Tiêu đề slide */
  title: string;
  /** Các bullet points nội dung */
  bulletPoints: string[];
  /** Văn bản dùng để TTS (giọng đọc) */
  narrationText: string;
  /** Audio URL của giọng đọc đã tổng hợp */
  audioUrl?: string | null;
  /** Từ khóa/prompt ảnh để sinh AI */
  imagePrompt?: string;
  /** URL ảnh đã tải sẵn về local (null nếu không tải được → slide không có ảnh) */
  imageUrl?: string | null;
  /** URL clip hoạt hình toàn màn hình được sinh cho cảnh (Veo hoặc provider tương đương). */
  sceneVideoUrl?: string | null;
  /** Dùng cảnh parallax 3D cục bộ khi provider video không khả dụng. */
  cinematicMode?: boolean;
  /** URL video Avatar 3D (nói chuyện/talking head) đã được sinh qua AI */
  avatarVideoUrl?: string | null;
  /** Pedagogical role of this scene in the lesson plan. */
  lessonPhase?: 'HOOK' | 'OBJECTIVE' | 'EXPLAIN' | 'EXAMPLE' | 'CHECK' | 'SUMMARY' | string;
  /** Learning outcome for this scene. */
  teachingGoal?: string;
  /** Gesture/presentation direction consumed by the avatar service. */
  teacherAction?: 'WELCOME' | 'EXPLAIN' | 'POINT' | 'EMPHASIZE' | 'QUESTION' | 'SUMMARIZE' | string;
  /** A learner-facing prompt used by CHECK scenes. */
  interactionPrompt?: string;
}

/** Input gửi lên POST /generate-video */
export interface GenerateVideoRequest {
  /** ID bài giảng từ backend */
  lectureId: string;
  /** Mảng slides cần render */
  slides: Slide[];
}

/** Trạng thái của một video render job */
export type JobStatus = 'pending' | 'processing' | 'done' | 'failed';

/** Một render job trong hệ thống */
export interface RenderJob {
  jobId: string;
  lectureId: string;
  status: JobStatus;
  /** Overall job progress from 0 to 1. */
  progress: number;
  /** Đường dẫn local tới file video (có sau khi done) */
  videoPath?: string;
  /** URL để truy cập video qua HTTP (có sau khi done) */
  videoUrl?: string;
  /** Thông báo lỗi (có sau khi failed) */
  error?: string;
  /** Cảnh báo không chặn render, ví dụ một cảnh Veo phải dùng fallback. */
  warnings?: string[];
  createdAt: Date;
  updatedAt: Date;
}
