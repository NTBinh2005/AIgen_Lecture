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
  /** Normalized voice energy sampled at the video frame rate. */
  mouthEnvelope?: number[];
}

export interface TtsProvider {
  /**
   * Tổng hợp giọng đọc từ văn bản.
   * @param text Nội dung văn bản cần đọc
   * @returns Promise<TtsResult>
   */
  synthesize(text: string, language?: 'auto' | 'vi' | 'en'): Promise<TtsResult>;
}

function describeTtsError(error: unknown): string {
  if (!(error instanceof Error)) return String(error) || 'unknown TTS error';
  if (error.message.trim()) return error.message.trim();

  const nested = (error as Error & {errors?: unknown[]}).errors;
  if (Array.isArray(nested)) {
    const details = [...new Set(nested.map(describeTtsError).filter(Boolean))];
    if (details.length > 0) return details.slice(0, 3).join('; ');
  }

  const cause = (error as Error & {cause?: unknown}).cause;
  if (cause && cause !== error) return describeTtsError(cause);
  return error.name || 'unknown TTS error';
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
  async synthesize(text: string, language?: 'auto' | 'vi' | 'en'): Promise<TtsResult> {
    const durationMs = Math.max(3000, text.length * 60);
    return {
      audioUrl: null,
      durationMs,
    };
  }
}

class FallbackTtsProvider implements TtsProvider {
  constructor(
    private readonly primary: TtsProvider,
    private readonly fallback: TtsProvider = new MockTtsProvider(),
  ) {}

  async synthesize(text: string, language?: 'auto' | 'vi' | 'en'): Promise<TtsResult> {
    try {
      return await this.primary.synthesize(text, language);
    } catch (error) {
      const lipSyncRequired = (process.env.REQUIRE_LIP_SYNCED_AVATAR ?? 'true')
        .trim()
        .toLowerCase() !== 'false';
      if (lipSyncRequired) {
        const reason = describeTtsError(error);
        throw new Error(
          `Không thể tạo giọng đọc để lip-sync. Kiểm tra kết nối mạng của video-service. Chi tiết: ${reason}`,
          {cause: error},
        );
      }
      console.warn('[TTS] Primary provider failed; rendering with silent timing fallback:', error);
      return this.fallback.synthesize(text, language);
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// FACTORY — điểm thay thế duy nhất khi chuyển sang TTS thật
// ─────────────────────────────────────────────────────────────────────────────

import { GoogleTtsProvider } from './GoogleTtsProvider';

/**
 * Trả về TTS provider hiện tại dựa trên biến môi trường TTS_PROVIDER.
 * - "google" (mặc định): giọng đọc tiếng Việt qua google-tts-api
 * - "mock": chỉ tạo thời lượng im lặng khi phát triển offline
 * - "elevenlabs": dùng ElevenLabsTtsProvider
 */
export function getTtsProvider(): TtsProvider {
  const provider = getTtsProviderName();

  switch (provider) {
    case 'google':
      return new FallbackTtsProvider(new GoogleTtsProvider());
    case 'elevenlabs': {
      const apiKey = process.env.ELEVENLABS_API_KEY;
      if (!apiKey) {
        console.warn('[TTS] ELEVENLABS_API_KEY is not set. Falling back to MockTtsProvider.');
        return new MockTtsProvider();
      }
      return new FallbackTtsProvider(new ElevenLabsTtsProvider(apiKey));
    }
    case 'mock':
    default:
      return new MockTtsProvider();
  }
}

export function getTtsProviderName(): string {
  return (process.env.TTS_PROVIDER ?? 'google').trim().toLowerCase();
}
