import { ElevenLabsTtsProvider } from './ElevenLabsTtsProvider';

/**
 * TTS (Text-to-Speech) Provider Interface
 *
 * Định nghĩa contract cho TTS provider. Khi cần swap sang Google Cloud TTS,
 * ElevenLabs hoặc bất kỳ provider thật nào, chỉ cần implement interface này
 * và thay thế MockTtsProvider mà không cần sửa code gọi nó ở bất cứ nơi nào.
 */

export interface TtsResult {
  /** URL tới file audio. null nếu là mock (không có audio thật). */
  audioUrl: string | null;
  /** Thời lượng ước tính của audio (ms). */
  durationMs: number;
}

export interface TtsProvider {
  /**
   * Tổng hợp giọng đọc từ văn bản.
   * @param text Nội dung văn bản cần đọc
   * @returns Promise<TtsResult>
   */
  synthesize(text: string): Promise<TtsResult>;
}

// ─────────────────────────────────────────────────────────────────────────────
// MOCK IMPLEMENTATION (dùng trong giai đoạn 1 — dev nhanh, không cần API key)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MockTtsProvider — Không gọi API thật, trả về audioUrl = null và ước tính
 * durationMs dựa trên độ dài văn bản.
 *
 * Công thức: Math.max(3000, text.length * 60)
 *   - ~60ms/ký tự: mô phỏng tốc độ đọc ~250 từ/phút (≈ 1 ký tự = 60ms)
 *   - Tối thiểu 3000ms (3 giây) mỗi slide, dù slide rất ngắn
 */
export class MockTtsProvider implements TtsProvider {
  async synthesize(text: string): Promise<TtsResult> {
    const durationMs = Math.max(3000, text.length * 60);
    return {
      audioUrl: null,
      durationMs,
    };
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// FACTORY — điểm thay thế duy nhất khi chuyển sang TTS thật
// ─────────────────────────────────────────────────────────────────────────────

import { GoogleTtsProvider } from './GoogleTtsProvider';

/**
 * Trả về TTS provider hiện tại dựa trên biến môi trường TTS_PROVIDER.
 * - "mock" (mặc định): dùng MockTtsProvider
 * - "google": dùng GoogleTtsProvider (gọi google-tts-api)
 * - "elevenlabs": dùng ElevenLabsTtsProvider
 */
export function getTtsProvider(): TtsProvider {
  const provider = process.env.TTS_PROVIDER ?? 'mock';

  switch (provider) {
    case 'google':
      return new GoogleTtsProvider();
    case 'elevenlabs': {
      const apiKey = process.env.ELEVENLABS_API_KEY;
      if (!apiKey) {
        console.warn('[TTS] ELEVENLABS_API_KEY is not set. Falling back to MockTtsProvider.');
        return new MockTtsProvider();
      }
      return new ElevenLabsTtsProvider(apiKey);
    }
    case 'mock':
    default:
      return new MockTtsProvider();
  }
}
