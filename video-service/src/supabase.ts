import { createClient } from '@supabase/supabase-js';
import fs from 'fs';
import path from 'path';

const SUPABASE_URL = process.env.SUPABASE_URL ?? '';
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY ?? '';
const SUPABASE_BUCKET = process.env.SUPABASE_BUCKET_NAME ?? 'lecture-videos';

/**
 * Fail fast nếu UPLOAD_TO_SUPABASE=true nhưng thiếu credentials.
 * Hàm này được gọi một lần khi server khởi động.
 */
export function validateSupabaseConfig(): void {
  if (process.env.UPLOAD_TO_SUPABASE !== 'true') return;

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    throw new Error(
      '[Supabase] UPLOAD_TO_SUPABASE=true nhưng thiếu SUPABASE_URL hoặc SUPABASE_SERVICE_ROLE_KEY. ' +
      'Kiểm tra lại biến môi trường hoặc tắt UPLOAD_TO_SUPABASE để chạy local.'
    );
  }

  console.log(`[Supabase] Configured ✓ → bucket: ${SUPABASE_BUCKET}`);
}

/**
 * Upload một file MP4 lên Supabase Storage.
 * @param localPath - Đường dẫn local tới file MP4 đã render xong.
 * @param jobId - Job ID dùng làm tên file trên Supabase.
 * @returns Public URL để truy cập video từ bất kỳ đâu.
 */
export async function uploadVideoToSupabase(localPath: string, jobId: string): Promise<string> {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  const fileName = `${jobId}.mp4`;
  const fileBuffer = await fs.promises.readFile(localPath);

  const { error } = await supabase.storage
    .from(SUPABASE_BUCKET)
    .upload(fileName, fileBuffer, {
      contentType: 'video/mp4',
      upsert: true, // Ghi đè nếu đã tồn tại (re-render case)
    });

  if (error) {
    throw new Error(`[Supabase] Upload thất bại: ${error.message}`);
  }

  const { data } = supabase.storage
    .from(SUPABASE_BUCKET)
    .getPublicUrl(fileName);

  if (!data?.publicUrl) {
    throw new Error('[Supabase] Không lấy được publicUrl sau khi upload.');
  }

  console.log(`[Supabase] Upload thành công → ${data.publicUrl}`);
  return data.publicUrl;
}
