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
  /** Đường dẫn local tới file video (có sau khi done) */
  videoPath?: string;
  /** URL để truy cập video qua HTTP (có sau khi done) */
  videoUrl?: string;
  /** Thông báo lỗi (có sau khi failed) */
  error?: string;
  createdAt: Date;
  updatedAt: Date;
}
