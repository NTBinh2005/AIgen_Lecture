# 🚀 Hướng dẫn khởi động dự án EduMind AI Lecture

## ⚡ Khởi động nhanh (Dev Local)

Cần mở **5 terminal / cửa sổ riêng biệt**, chạy theo thứ tự sau:

---

## Bước 1 — PostgreSQL (Database) 🗄️
> Terminal 1 — Chỉ cần chạy 1 lần, giữ chạy nền

```powershell
cd "d:\FPTU\Semester 8\AIgen_Lecture"
docker-compose up -d postgres
```
✅ Kiểm tra: `docker ps` → thấy `aigen-lecture-postgres` đang `Up`

---

## Bước 2 — Avatar AI Service (SadTalker) 🤖
> Terminal 2 — Bắt buộc chạy TRƯỚC video-service

```powershell
cd "d:\FPTU\Semester 8\AIgen_Lecture\avatar-ai-service"
.\start_avatar_service.bat
```
✅ Kiểm tra: Mở http://localhost:5000/health → thấy `"sadTalkerReady": true`

---

## Bước 3 — Video Service (Remotion Render) 🎬
> Terminal 3

```powershell
cd "d:\FPTU\Semester 8\AIgen_Lecture\video-service"
npm run dev
```
✅ Kiểm tra: http://localhost:3001/health → `{"status":"ok"}`

---

## Bước 4 — Backend (Spring Boot) ☕
> Terminal 4

```powershell
cd "d:\FPTU\Semester 8\AIgen_Lecture\backend"
.\mvnw spring-boot:run
```
✅ Kiểm tra: Log hiện `Started DemoApplication` → http://localhost:8080

---

## Bước 5 — Frontend (React + Vite) 🌐
> Terminal 5

```powershell
cd "d:\FPTU\Semester 8\AIgen_Lecture\frontend"
npm run dev
```
✅ Kiểm tra: Mở http://localhost:5173

---

## 📋 Tóm tắt nhanh

| # | Service | Lệnh | Port | Ghi chú |
|---|---------|------|------|---------|
| 1 | **PostgreSQL** | `docker-compose up -d postgres` | 5436 | Docker |
| 2 | **Avatar AI (SadTalker)** | `.\start_avatar_service.bat` | 5000 | Python + GPU |
| 3 | **Video Service** | `npm run dev` | 3001 | Node.js |
| 4 | **Backend** | `.\mvnw spring-boot:run` | 8080 | Java 21 |
| 5 | **Frontend** | `npm run dev` | 5173 | React/Vite |

---

## 🔄 Thứ tự tắt

```
Frontend → Backend → Video Service → Avatar AI → Docker (postgres)
```

---

## ⚠️ Lưu ý quan trọng

- **Avatar AI Service** phải chạy TRƯỚC Video Service
- **SadTalker** cần ~30-60 giây/slide để render (GPU RTX 3050)
- Khi không cần AI avatar thật, đặt `AVATAR_ENGINE=fallback` để dùng robot SVG (nhanh hơn)
- Backend cần có `GEMINI_API_KEY` trong file `.env`

### Chế độ video hoạt hình 3D toàn màn hình (Veo)

Mặc định hệ thống vẫn render bố cục slide + giáo viên. Để mỗi cảnh trở
thành một clip hoạt hình 3D 16:9 giống video kể chuyện AI, cấu hình trong
file `.env` ở thư mục gốc:

```env
SCENE_VIDEO_PROVIDER=veo
GEMINI_API_KEY=your_gemini_api_key_here
VEO_MODEL=veo-3.1-fast-generate-preview
```

Sau đó khởi động lại các service. Video service sẽ sinh clip Veo 8 giây
cho từng cảnh, tải clip về local, ghép giọng đọc và phụ đề bằng Remotion.
Nếu Veo chưa được cấp quyền, hết quota hoặc một cảnh sinh lỗi, riêng cảnh
đó tự động dùng cảnh parallax 3D chuyển động chạy cục bộ; toàn bộ bài giảng
vẫn tiếp tục và không quay về bố cục slide tĩnh.

Lưu ý: Veo có thể phát sinh chi phí API và mỗi cảnh có thể cần vài phút để
sinh. Đặt `SCENE_VIDEO_PROVIDER=none` để tắt chế độ này.
