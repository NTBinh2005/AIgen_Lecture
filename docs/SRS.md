# Software Requirements Specification (SRS)
> **Dự án:** CP-2026-02 | **Nhóm:** G-06 | **GVHD:** Lâm Hữu Khánh Phương
> **Tên dự án:** Interactive AI-Generated Lecture System (Đã Pivot sang AI Video Lecture)

---

## 1. Tổng quan hệ thống

**Vấn đề đang giải quyết:**
- Giáo viên mất nhiều thời gian soạn bài giảng tương tác thủ công.
- Cần một giải pháp tự động hóa việc tạo nội dung học tập hấp dẫn (video) từ tài liệu thô.

**Giải pháp đề xuất (Sau Pivot):**
1. Giáo viên upload tài liệu (PDF/DOCX) → hệ thống trích xuất text.
2. Gọi LLM API (OpenAI / Gemini) → sinh ra kịch bản bài giảng (Script).
3. Backend gọi sang `video-service` (Node.js + Remotion) để render thành **Video bài giảng hoàn chỉnh** (có animation, TTS giọng đọc).
4. Video sau khi render được upload lên **Firebase Storage** và lưu URL.
5. Học sinh xem video bài giảng trên Web/Mobile.

**Các bên liên quan:** Teachers, Students, Admin.

---

## 2. Giả định & Ràng buộc

### Assumptions
- Tài liệu PDF/DOCX upload là văn bản chuẩn, parse được text.
- LLM API hoạt động ổn định.

### Constraints
- Thời gian phát triển giới hạn (hiện tại còn rất ít thời gian do Pivot).
- Phân quyền nghiêm ngặt: Admin / Teacher / Student hoàn toàn tách biệt.
- Ứng dụng kiến trúc Microservice lai: Backend chính (Java Spring Boot) và Video Service (Node.js + Remotion).
- Render video là tác vụ bất đồng bộ (Async) mất nhiều thời gian, hệ thống dùng cơ chế Polling để cập nhật trạng thái.

---

## 3. Business Rules

| Rule ID | Tên quy tắc | Mô tả |
|---------|-------------|-------|
| BR-01 | File Upload Constraint | File upload phải là .pdf hoặc .docx, ≤ 20MB. |
| BR-02 | Content Ownership | Teacher chỉ được xem/sửa/xóa lecture của chính mình. |
| BR-03 | AI Service Timeout | LLM API timeout ~15s, tuy nhiên Video Render dùng Async. |
| BR-04 | Prevent Data Deletion | Cấm hard-delete tài khoản/lecture đã có dữ liệu. Chỉ soft-delete. |
| BR-05 | AI Context Window Limit | Nếu văn bản trích xuất > 4,000 tokens thì tự động Text Chunking. |

---

## 4. User Stories

| Story ID | Actor | Muốn làm gì | Mục đích |
|----------|-------|-------------|----------|
| US-01 | Teacher | Upload tài liệu thô (PDF, DOCX) | Dùng làm context cho AI sinh video |
| US-02 | Teacher | Yêu cầu AI tạo Video bài giảng | Không phải soạn thủ công |
| US-03 | Student | Xem Video bài giảng | Học trực quan thông qua video |
| US-04 | System | Lưu trữ Video lên Firebase | Cung cấp link public cho Web/Mobile |
| US-05 | Teacher/Student | Đăng nhập bằng email + mật khẩu | Truy cập đúng tính năng theo vai trò |
| US-06 | Admin | Xem danh sách tài khoản, thay đổi vai trò | Quản lý quyền truy cập |

---

## 5. Functional Requirements

| Req ID | Mô tả | Priority |
|--------|-------|----------|
| FR-01 | Trích xuất văn bản từ PDF/Word bằng Apache PDFBox/POI. | High |
| FR-02 | Tích hợp LLM API tạo Kịch bản dạng JSON chuẩn xác. | High |
| FR-03 | Giao tiếp với `video-service` để render video (Async + Polling). | High |
| FR-04 | Tích hợp TTS (Google Cloud/ElevenLabs hoặc Mock) cho Video. | High |
| FR-05 | Upload file video MP4 lên Firebase Storage để lấy Public URL. | High |
| FR-06 | Hiển thị Video Player trên Frontend và Mobile thay cho Slide tĩnh. | High |
| FR-07 | Đăng nhập email/password + phân quyền JWT. | High |

---

## 6. Domain Entities & Data Model

| Entity | Attributes chính |
|--------|-----------------|
| **User** | UserID (PK), Role (Teacher/Student/Admin), Name, Email, PasswordHash |
| **Lecture** | LectureID (PK), TeacherID (FK), Title, OriginalSource, CreatedDate, `videoUrl` (String), `videoStatus` (Enum: PENDING, PROCESSING, DONE, FAILED), `videoJobId` (String) |
| **SourceDocument**| DocumentID (PK), LectureID (FK), FileName, FileType, StoragePath, UploadedAt |
| **API_Usage_Log** | UsageID (PK), TeacherID (FK), Endpoint, TokensUsed, Cost, CreatedAt |

*(Lưu ý: Interaction_Log, AI_Element, AI_Analysis tạm thời hạ độ ưu tiên trong giai đoạn Pivot để tập trung hoàn thiện Video Pipeline end-to-end).*

---

## 7. Tech Stack

- **Backend (Core):** Java Spring Boot (REST API, JWT, JPA, PostgreSQL, Firebase Admin).
- **Backend (Video Render):** Node.js + Remotion + Express (`video-service`).
- **Frontend:** React + TypeScript + Vite + Zustand + Tailwind CSS/Shadcn.
- **Mobile:** Flutter (Dart).
- **Infrastructure:** Docker, Docker Compose, Firebase Storage, LLM API (Gemini).
