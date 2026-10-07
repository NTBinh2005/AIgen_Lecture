# Avatar AI Service

Service này tạo clip giáo viên hoạt hình 3D cho từng slide. `video-service`
gửi đúng file thuyết minh và thời lượng, sau đó tải MP4 về trước khi Remotion
render video cuối.

## Chế độ chạy

- `AVATAR_ENGINE=fallback` (mặc định trong Docker): chỉ dành cho preview kỹ
  thuật. Video service mặc định không nhận clip này là lip-sync thật và sẽ hiển
  thị một tư thế giảng dạy tĩnh, tránh hiệu ứng ảnh rung/miệng giả.
- `AVATAR_ENGINE=auto`: dùng SadTalker nếu đủ dependency/checkpoint, nếu không
  tự động quay về fallback.
- `AVATAR_ENGINE=sadtalker`: bắt buộc dùng SadTalker và báo lỗi rõ ràng nếu
  môi trường chưa hoàn chỉnh.

Kiểm tra engine đang được chọn:

```powershell
Invoke-RestMethod http://localhost:5000/health
```

## Chạy toàn hệ thống

Tại thư mục gốc:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Mặc định `video-service` dùng `TTS_PROVIDER=google` và
`AVATAR_PROVIDER=sadtalker`; tên provider này trỏ tới service Python
self-hosted. Muốn có khẩu hình thật, chạy `start_all.ps1` với môi trường
SadTalker đầy đủ. Docker fallback không còn được giả nhận là clip lip-sync.

## Bật SadTalker thật trên máy có NVIDIA GPU

1. Tạo/cài Python 3.10–3.12 environment và cài `requirements312.txt`.
2. Đặt model trong `sadtalker_src/checkpoints/`, bao gồm tối thiểu:
   `SadTalker_V0.0.2_256.safetensors` và `mapping_00229-model.pth.tar`.
3. Đặt `SADTALKER_PYTHON` tới executable của environment đó.
4. Chạy service với `AVATAR_ENGINE=auto` hoặc `sadtalker`.

Endpoint `/health` liệt kê chính xác dependency/checkpoint còn thiếu, thay vì
âm thầm chạy một pipeline SadTalker không hoạt động.

## API

`POST /api/talk`

```json
{
  "jobId": "render-job-id",
  "slideIndex": 0,
  "audioUrl": "http://video-service:3001/audio/narration.mp3",
  "durationMs": 8500,
  "text": "Lời thuyết minh"
}
```

MP4 trả về luôn là H.264/yuv420p, không chứa audio. Narration được Remotion
phát đúng một lần để tránh chồng tiếng.
