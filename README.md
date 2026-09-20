# Hướng dẫn cài đặt và chạy dự án (AIGen Lecture)

Tài liệu này dành cho các thành viên mới clone/pull code về và cần chạy dự án ở local. Dự án bao gồm nhiều thành phần (Backend, Frontend, Mobile, Video Service, Database), do đó hãy làm theo đúng thứ tự dưới đây để tránh lỗi.

---

## 1. Yêu cầu hệ thống (Prerequisites)
Hãy đảm bảo máy bạn đã cài đặt các công cụ sau:
- **Java 21** (Dành cho Backend Spring Boot)
- **Node.js** (Khuyến nghị bản LTS, >= v20 cho Frontend. Đối với Video-Service dùng Docker nên không cần Node.js local)
- **Docker & Docker Compose** (Bắt buộc để chạy Database và Video Service)
- **Git** (Để clone source code)

---

## 2. Các bước khởi chạy dự án

### Bước 1: Khởi động Infrastructure & Services (Docker)
Dự án sử dụng Docker Compose để gom các dịch vụ phụ thuộc bao gồm: **PostgreSQL** (Database) và **Video Service** (Dịch vụ render video Remotion).

1. Mở terminal ở thư mục gốc của dự án (`edu-ai-lecture-system`).
2. Chạy lệnh:
   ```bash
   docker-compose up -d
   ```
3. Đợi một chút để Docker tải image và khởi động các container.
4. Kiểm tra xem các container đã chạy chưa bằng lệnh `docker ps`. Đảm bảo thấy `aigen-lecture-postgres` và `aigen-lecture-video-service` đang `Up`.
   - *Lưu ý: Nếu Docker không bật tự động cùng máy tính, mỗi lần khởi động lại máy bạn cần chạy lệnh này để bật Database.*

### Bước 2: Khởi chạy Backend (Spring Boot)
1. Mở một tab terminal mới và đi vào thư mục `backend`:
   ```bash
   cd backend
   ```
2. Chạy Spring Boot bằng Maven Wrapper (đã tích hợp sẵn):
   - **Windows:**
     ```powershell
     .\mvnw spring-boot:run
     ```
   - **Mac/Linux:**
     ```bash
     ./mvnw spring-boot:run
     ```
3. Đợi log báo `Started DemoApplication` là backend đã chạy thành công ở cổng `http://localhost:8080`.
   - *Lưu ý: Lần đầu tiên chạy, hệ thống sẽ tự động tạo các bảng trong Database PostgreSQL.*

### Bước 3: Khởi chạy Frontend (React + Vite)
1. Mở một tab terminal mới và đi vào thư mục `frontend`:
   ```bash
   cd frontend
   ```
2. Cài đặt các thư viện (chỉ cần chạy lần đầu hoặc khi có người mới thêm thư viện):
   ```bash
   npm install
   ```
3. Chạy server phát triển:
   ```bash
   npm run dev
   ```
4. Mở trình duyệt và truy cập vào link báo trên terminal (thường là `http://localhost:5173`).

---

## 3. Cấu hình biến môi trường (Environment Variables) - RẤT QUAN TRỌNG

Để bảo mật, hệ thống KHÔNG lưu các API Key thật (Gemini, Supabase, ElevenLabs) trên mã nguồn Github. Khi anh em pull code về, cần tự cấu hình các key này ở máy local mới chạy được đầy đủ tính năng:

- **Backend (Gemini API Key):** 
  - Backend sử dụng AI Gemini. Key mặc định trong `application.properties` đã bị xóa để bảo mật.
  - **Cách chạy:** Bạn cần tạo biến môi trường `GEMINI_API_KEY` trước khi chạy Spring Boot. 
    - Trên Windows (PowerShell): `$env:GEMINI_API_KEY="your_gemini_key_here"; .\mvnw spring-boot:run`
    - Trên Mac/Linux: `GEMINI_API_KEY="your_gemini_key_here" ./mvnw spring-boot:run`
    - *(Hoặc bạn có thể thêm biến môi trường này vào IDE như IntelliJ/Eclipse trong phần Edit Configurations).*

- **Video Service & Storage (Docker Compose):** 
  - Các cấu hình nhạy cảm (ElevenLabs Key, Supabase URL) đã được gỡ khỏi `docker-compose.yml`. Mặc định chạy local sẽ dùng "mock" TTS và không upload lên Supabase.
  - **Để test full luồng upload:** Bạn cần tạo một file tên là `docker-compose.override.yml` ở thư mục gốc, copy cấu hình credentials thật từ team lead và dán vào. Docker sẽ tự động load file này. File này đã được đưa vào `.gitignore` nên anh em yên tâm lưu key ở đây không sợ push nhầm lên Github.

---

## 4. Các lỗi thường gặp (Troubleshooting)

1. **Lỗi `Connection refused` khi chạy Backend:**
   - Nguyên nhân: Database chưa chạy.
   - Xử lý: Kiểm tra lại Docker, chạy `docker-compose up -d postgres`.

2. **Lỗi `Port 8080 was already in use`:**
   - Nguyên nhân: Có một tiến trình khác (hoặc chính backend đang chạy ngầm) chiếm mất cổng 8080.
   - Xử lý: Tắt tiến trình đó (ấn Ctrl+C ở terminal cũ) hoặc Kill process chiếm cổng 8080.

3. **Backend báo lỗi `Illegal unquoted character` khi Generate Bài giảng:**
   - Nguyên nhân: LLM trả về chuỗi JSON bị lỗi xuống dòng.
   - Xử lý: Đã được fix ở phiên bản mới nhất, hãy đảm bảo bạn đã `git pull` code mới nhất về.

4. **Tạo video bị Failed báo `WebSocket not supported`:**
   - Nguyên nhân: Base image của video-service cũ không tương thích với Supabase v2.
   - Xử lý: Chạy lại lệnh `docker-compose up -d --build video-service` để cập nhật Node 22.

---
*Mọi thắc mắc về luồng code, hãy tham khảo thêm file `docs/SRS.md` và `docs/API_CONTRACT.md`.*
