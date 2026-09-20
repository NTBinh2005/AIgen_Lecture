import * as googleTTS from 'google-tts-api';
import { v4 as uuidv4 } from 'uuid';
import path from 'path';
import fs from 'fs';
import type { TtsProvider, TtsResult } from './TtsProvider';

const AUDIO_DIR = path.resolve(process.cwd(), 'out/audio');

export class GoogleTtsProvider implements TtsProvider {
  async synthesize(text: string): Promise<TtsResult> {
    if (!text || text.trim() === '') {
      return { audioUrl: null, durationMs: 3000 };
    }

    try {
      // Sử dụng getAllAudioBase64 để xử lý văn bản dài hơn 200 ký tự
      const results = await googleTTS.getAllAudioBase64(text, {
        lang: 'vi', // Mặc định dùng tiếng Việt
        slow: false,
        host: 'https://translate.google.com',
        splitPunct: ',.?', // Tách câu theo dấu câu
        timeout: 60000, // Tăng timeout lên 60s để tránh lỗi "timeout of 10000ms exceeded"
      });

      // Gộp các đoạn base64 lại thành 1 buffer duy nhất
      const buffers = results.map(r => Buffer.from(r.base64, 'base64'));
      const finalBuffer = Buffer.concat(buffers);

      // Đảm bảo thư mục tồn tại
      await fs.promises.mkdir(AUDIO_DIR, { recursive: true });
      
      const uuid = uuidv4();
      const filename = `${uuid}.mp3`;
      const filePath = path.join(AUDIO_DIR, filename);

      // Ghi ra file
      await fs.promises.writeFile(filePath, finalBuffer);

      const port = process.env.PORT ?? '3001';
      const audioUrl = `http://localhost:${port}/audio/${filename}`;

      // Ước lượng durationMs. Giọng Google tiếng Việt đọc khá chậm
      // Nâng hệ số lên 85ms/ký tự và cộng thêm 1 khoảng đệm (buffer) 1000ms
      // để đảm bảo chắc chắn âm thanh đọc xong mới nhảy slide.
      const durationMs = Math.max(4000, text.length * 85 + 1000);

      return {
        audioUrl,
        durationMs,
      };
    } catch (error) {
      console.error('[GoogleTTS] Failed to synthesize speech:', error);
      throw error;
    }
  }
}
