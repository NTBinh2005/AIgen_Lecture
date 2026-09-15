import { v4 as uuidv4 } from 'uuid';
import path from 'path';
import fs from 'fs';
import type { TtsProvider, TtsResult } from './TtsProvider';

// Dùng node-fetch hoặc fetch native của Node 18+
const ELEVENLABS_API_URL = 'https://api.elevenlabs.io/v1/text-to-speech/21m00Tcm4TlvDq8ikWAM';
const AUDIO_DIR = path.resolve(process.cwd(), 'out/audio');

export class ElevenLabsTtsProvider implements TtsProvider {
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  async synthesize(text: string): Promise<TtsResult> {
    if (!text || text.trim() === '') {
      return { audioUrl: null, durationMs: 3000 };
    }

    try {
      const response = await fetch(ELEVENLABS_API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'xi-api-key': this.apiKey,
        },
        body: JSON.stringify({
          text,
          model_id: 'eleven_multilingual_v2',
          voice_settings: {
            stability: 0.5,
            similarity_boost: 0.75,
          },
        }),
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(`ElevenLabs API error: ${response.status} - ${errText}`);
      }

      // Đảm bảo thư mục tồn tại
      await fs.promises.mkdir(AUDIO_DIR, { recursive: true });

      const audioBuffer = await response.arrayBuffer();
      const uuid = uuidv4();
      const filename = `${uuid}.mp3`;
      const filePath = path.join(AUDIO_DIR, filename);

      await fs.promises.writeFile(filePath, Buffer.from(audioBuffer));

      const port = process.env.PORT ?? '3001';
      const audioUrl = `http://localhost:${port}/audio/${filename}`;

      // Ước lượng durationMs thay vì cài thêm music-metadata
      // Tốc độ chuẩn đọc tiếng Anh của ElevenLabs khoảng 15 chữ / giây -> ~1 ký tự = 60ms
      const durationMs = Math.max(3000, text.length * 60);

      return {
        audioUrl,
        durationMs,
      };
    } catch (error) {
      console.error('[ElevenLabs] Failed to synthesize speech:', error);
      throw error;
    }
  }
}
