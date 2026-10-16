# BÁO CÁO TIẾN ĐỘ DỰ ÁN (PROJECT PROGRESS PRESENTATION)
## Đề tài: Interactive AI-Generated Lecture System (AIGen Lecture / EduMind)
- **Mã đề tài:** CP-2026-02 | **Nhóm:** G-06
- **Giảng viên hướng dẫn:** ThS. Lâm Hữu Khánh Phương
- **Kỳ học:** Semester 8 — Đại học FPT
- **Thời gian báo cáo:** Tháng 10/2026

---

## 📌 SLIDE 1: TRANG TIÊU ĐỀ (COVER SLIDE)

### INTERACTIVE AI-GENERATED LECTURE SYSTEM
**Nền tảng Tự Động Hóa Biên Soạn Video Bài Giảng Tương Tác Bằng AI**

* **Đề tài Capstone:** CP-2026-02
* **Nhóm thực hiện:** G-06
* **Giảng viên hướng dẫn:** Lâm Hữu Khánh Phương
* **Đơn vị:** Đại học FPT (FPT University)
* **Kỳ học:** Fall 2026 (Semester 8)

---

## 📌 SLIDE 2: BỐI CẢNH & QUYẾT ĐỊNH PIVOT (PROBLEM & PIVOT)

### 1. Bối cảnh & Thách thức
* **Khó khăn của giáo viên:** Soạn giáo án, vẽ slide, thu âm và làm video bài giảng thủ công tốn từ 4–8 giờ cho 1 bài giảng ngắn.
* **Hạn chế của bài học tĩnh:** Học sinh thường mất tập trung khi đọc tài liệu PDF/slide văn bản thuần túy.
* **Rào cản công cụ:** Giáo viên không chuyên gặp khó khi sử dụng các phần mềm biên tập dựng phim chuyên nghiệp.

### 2. Quyết định Pivot chiến lược (AI Video First)
* Chuyển hướng từ slide tĩnh sang **Hệ thống tạo Video Bài Giảng Hoàn Chỉnh bằng AI**.
* **Luồng xử lý 1 chạm:** Tải lên tài liệu thô (`.pdf`, `.docx`, `.pptx`) ➔ AI trích xuất nội dung ➔ Tự sinh kịch bản sư phạm phân cảnh (scenes) ➔ Dựng video MP4 kèm giọng đọc AI (TTS) và hình ảnh minh họa sống động.

---

## 📌 SLIDE 3: KIẾN TRÚC HỆ THỐNG TỔNG THỂ (SYSTEM ARCHITECTURE)

Hệ thống được thiết kế theo mô hình **Microservices lai**, phân tách độc lập các module chức năng:

```
[ Khách hàng ] ──► [ Web App (React 18 + Vite) ] & [ Mobile App (Flutter) ]
                          │
                          ▼
[ Core API ]   ──► [ Spring Boot 3 + Java 21 ] ──► [ PostgreSQL 17 (Flyway V16) ]
                          │
           ┌──────────────┴──────────────┐
           ▼                             ▼
[ Video Render Service ]       [ AI Services ]
 (Node.js 22 + Remotion)        - Google Gemini AI (Scripting & Quiz)
  - Text-To-Speech Engine       - Avatar AI Service (SadTalker, LivePortrait)
  - Firebase Cloud Storage      - SePay VietQR Payment Gateway
```

* **Core Backend:** Java 21, Spring Boot 3, Spring Security + JWT (Refresh Token Rotation), JPA/Hibernate.
* **Video Rendering Service:** Node.js 22, Remotion Engine render video MP4 song song.
* **Avatar AI Service:** Python Fast/Flask API, SadTalker, LivePortrait, Wav2Lip đồng bộ khẩu hình.
* **Client Ecosystem:** Web App (React, TypeScript, Tailwind CSS) & Mobile App (Flutter Dart cho iOS/Android).
* **Infrastructure:** Docker Compose, PostgreSQL 17, Firebase Storage, Twilio SMS OTP, SePay Gateway.

---

## 📌 SLIDE 4: TỔNG THỂ TIẾN ĐỘ THỰC HIỆN (MILESTONES & PROGRESS)

| Phân hệ / Hạng mục | Tỷ lệ hoàn thành | Trạng thái | Đánh giá chi tiết |
| :--- | :---: | :---: | :--- |
| **1. Backend Core (Spring Boot)** | **95%** | 🟢 Đạt chuẩn | REST APIs, bảo mật JWT, Quiz Engine, Live Session, Điểm danh QR, Thanh toán SePay, Audit Log. |
| **2. Frontend Web App (React)** | **90%** | 🟢 Đạt chuẩn | Đầy đủ 3 phân hệ role (Admin, Teacher, Student), Dashboard, Trình xem video, Tạo bài giảng AI. |
| **3. Video Service (Remotion)** | **90%** | 🟢 Đạt chuẩn | Render video tự động từ JSON script, ghép TTS, upload Firebase Storage, webhook trạng thái. |
| **4. Mobile App (Flutter)** | **80%** | 🟡 Đang hoàn thiện | Đã có Auth, Dashboard, Danh sách & Xem Video bài giảng, Giao diện Giáo viên tạo bài giảng. |
| **5. AI Avatar Service** | **75%** | 🟡 Đang hoàn thiện | Pipeline SadTalker, LivePortrait sinh video người dẫn chuyện AI từ file audio và ảnh chân dung. |
| **6. DevOps & Quality Assurance** | **95%** | 🟢 Đạt chuẩn | Docker compose chạy trọn gói, **118 test cases đạt 100%**, rà soát concurrency & race condition. |

> **Tổng thể tiến độ dự án ước đạt:** **~88% – 90%** khối lượng công việc theo cam kết SRS.

---

## 📌 SLIDE 5: CHI TIẾT TÍNH NĂNG ĐÃ HOÀN THÀNH (KEY FEATURES)

### 1. Phân hệ Quản trị & Xác thực (Auth & RBAC)
* Phân quyền 3 vai trò độc lập: **Admin**, **Teacher**, **Student**.
* Đăng nhập mật khẩu, xác thực Google OAuth2, SMS OTP đăng nhập qua số điện thoại.
* Cơ chế bảo mật phiên: JWT + Refresh Token Rotation, thu hồi token khi đổi mật khẩu/đổi quyền.

### 2. Pipeline Tạo Video Bài Giảng AI (Core Feature)
* Hỗ trợ upload tài liệu: PDF, Word, PowerPoint.
* Trích xuất văn bản thông minh (Apache PDFBox/POI) + Text Chunking cho tài liệu dài.
* LLM Gemini API sinh kịch bản JSON theo từng cảnh bài học.
* Render video tự động qua `video-service` và xuất link trực tiếp từ Firebase Storage.

### 3. Tương tác E-Learning & Quiz
* Sinh câu hỏi trắc nghiệm tự động từ nội dung bài giảng.
* Chấm điểm tự động và AI gợi ý nhận xét chi tiết cho từng học sinh.
* Lưu bài làm tự động (Autosave an toàn) chống mất dữ liệu khi mất kết nối.
* Thảo luận, bình luận đa cấp dưới từng mốc thời gian video.

### 4. Quản lý Lớp Học, Điểm Danh & Thanh Toán
* Quản lý lớp học, phân công bài giảng, giới hạn sĩ số.
* Buổi học trực tuyến (Live Session) + Tạo mã QR điểm danh động chống gian lận.
* Cổng thanh toán VietQR SePay tự động kích hoạt gói thành viên khi quét mã.
* Quy trình duyệt hoàn tiền tự động (Refund) qua webhook biến động số dư.

---

## 📌 SLIDE 6: KẾT QUẢ KIỂM THỬ & CHẤT LƯỢNG MÃ NGUỒN (QA & BUG AUDIT)

*Báo cáo rà soát mã nguồn ngày 15/10/2026 ghi nhận các cải tiến lớn:*

1. **Khắc phục lỗi chạy đua đồng thời (Race Condition):**
   * Sử dụng Pessimistic Locking trong Spring Data JPA khi làm bài, autosave, đăng ký lớp học và điểm danh QR.
   * Xử lý xung đột `answerVersion` tránh ghi đè đáp án khi mở nhiều tab làm bài.
2. **Chuẩn hóa API & Bảo mật phiên:**
   * Tự động thu hồi Refresh Token và đóng kết nối SSE ngay khi đổi/reset mật khẩu.
   * Ngăn chặn thao tác sửa/xóa bài giảng không thuộc quyền sở hữu (`Content Ownership Rule`).
3. **Kết quả kiểm thử tự động:**
   * **118 Test Suites (0 Failure, 0 Error, 0 Skipped)** bao gồm Unit Test, Integration Test và Audit Test chạy trên cơ sở dữ liệu PostgreSQL 17 thật.

---

## 📌 SLIDE 7: THÁCH THỨC KỸ THUẬT & PHƯƠNG ÁN XỬ LÝ (CHALLENGES & MITIGATION)

| Vấn đề / Thách thức | Ảnh hưởng | Phương án xử lý đã triển khai |
| :--- | :--- | :--- |
| **1. Quá tải / Rate Limit Gemini AI** | API miễn phí đôi khi trả lỗi 429 hoặc 503 khi gửi tài liệu lớn. | Tích hợp thuật toán Exponential Backoff, bổ sung kịch bản bài giảng mẫu dự phòng khi AI bận. |
| **2. Tải CPU khi Render Video** | Render nhiều video đồng thời tốn bộ nhớ RAM/CPU trên container. | Triển khai hàng đợi xử lý bất đồng bộ (Async Queue) giới hạn tối đa 2 render task đồng thời. |
| **3. Webhook SePay trên Localhost** | SePay không gọi trực tiếp được vào máy cá nhân khi chưa deploy. | Cấu hình kênh trung chuyển tunnel (Ngrok) phục vụ demo và tích hợp chế độ test giả lập giao dịch. |
| **4. Đồng bộ Mobile App** | Backend đã chuyển các API sang định dạng Pageable chuẩn hóa. | Đồng bộ hóa DTO và Model trên Flutter theo chuẩn API Contract mới nhất. |

---

## 📌 SLIDE 8: KẾ HOẠCH BÀN GIAO & VỀ ĐÍCH (ROADMAP TO DEFENSE)

### Tuần 1: Hoàn thiện chi tiết & Trải nghiệm người dùng (UX Polish)
* Đồng bộ giao diện duyệt hoàn tiền SePay trên trang Web Admin.
* Tối ưu hiển thị Quiz và Video Player trên Mobile App (Flutter).
* Bổ sung hiệu ứng Skeleton Loading và thông báo lỗi tiếng Việt thân thiện.

### Tuần 2: Đóng băng mã nguồn & Kiểm thử toàn diện (Code Freeze & E2E Testing)
* Đóng băng các tính năng mới (Code Freeze).
* Thực hiện kiểm thử toàn trình End-to-End từ khâu upload PDF ➔ sinh video ➔ học sinh làm quiz ➔ giáo viên chấm điểm.
* Chuẩn bị bộ dữ liệu mẫu (Dummy Data) phục vụ demo trực quan trước hội đồng.

### Tuần 3: Diễn tập & Chuẩn bị bảo vệ (Rehearsal & Final Defense)
* Quay video demo dự phòng (Backup Video Demo) phòng sự cố mạng trong buổi bảo vệ.
* Diễn tập thuyết trình thử các kịch bản hỏi đáp của hội đồng chấm đồ án.
* Hoàn thiện tài liệu luận văn tốt nghiệp và bàn giao mã nguồn.

---

## 📌 SLIDE 9: LỜI CẢM ƠN & HỎI ĐÁP (Q&A)

### CẢM ƠN THẦY/CÔ VÀ HỘI ĐỒNG ĐÃ LẮNG NGHE!
* **Dự án:** Interactive AI-Generated Lecture System (CP-2026-02)
* **Nhóm thực hiện:** G-06
* *Rất mong nhận được những góp ý quý báu từ Thầy/Cô để nhóm hoàn thiện đồ án đạt kết quả cao nhất!*
