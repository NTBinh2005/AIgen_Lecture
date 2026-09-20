# Lộ trình hoàn thiện dự án (Project Roadmap)

Tất cả các tính năng dưới đây đều BẮT BUỘC phải hoàn thành để dự án đạt chuẩn cao nhất. Anh em làm lần lượt theo đúng thứ tự (Phase 1 -> Phase 5) để tránh đụng code nhau. Cứ làm xong cái nào thì check `[x]` vào cái đó.

---

## 🚀 Phase 1: Hoàn thiện nền tảng Web & Quản trị (Dễ làm trước)
*Tập trung dọn dẹp các tính năng cơ bản trên Web để hệ thống trơn tru.*

- `[ ]` **Teacher - Quản lý Bài giảng cơ bản**
  - `[ ]` Thêm tính năng "Xóa" (Soft-delete) bài giảng trên web.
  - `[ ]` Thêm tính năng "Sửa tên" bài giảng trên web.
- `[ ]` **Admin Dashboard**
  - `[ ]` Dựng trang quản lý User cho Admin.
  - `[ ]` Tính năng phân quyền (đổi Role) hoặc Khoá tài khoản.
- `[ ]` **Quản lý Tài khoản cá nhân (User Profile)**
  - `[ ]` Xem thông tin cá nhân (Web).
  - `[ ]` Tính năng Đổi mật khẩu.

---

## 📱 Phase 2: Chuyển giao hệ sinh thái sang Mobile
*Phải có Mobile App vì đây là yêu cầu cứng của giảng viên.*

- `[ ]` **Mobile App (Flutter) cho Student**
  - `[ ]` Dựng màn hình Login.
  - `[ ]` Dựng màn hình Danh sách Bài giảng (fetch từ API backend).
  - `[ ]` Dựng màn hình Video Player để xem bài giảng (MP4 từ Firebase).

---

## 💬 Phase 3: Nâng cấp Tương tác (Interactive E-Learning)
*Biến Web/Mobile từ trang xem video một chiều thành nền tảng giáo dục thực thụ.*

- `[ ]` **Q&A / Bình luận dưới Video**
  - `[ ]` Backend: API CRUD cho Comment.
  - `[ ]` Frontend/Mobile: Khung chat/bình luận dưới màn hình xem Video.
  - `[ ]` Teacher có thể "Reply" bình luận của Student.
- `[ ]` **Mini Quiz (Trắc nghiệm ngắn)**
  - `[ ]` Cho phép AI sinh ra thêm 3-5 câu hỏi trắc nghiệm kèm theo video.
  - `[ ]` Hiển thị popup Quiz khi học sinh xem xong Video.
- `[ ]` **Video Bookmark**
  - `[ ]` Nút "Lưu mốc thời gian" để học sinh note lại đoạn video cần nhớ.

---

## 🛡️ Phase 4: Chất lượng, Hiệu năng & Bảo mật (NFRs)
*Tinh chỉnh kỹ thuật để đối phó với giám khảo chuyên môn sâu.*

- `[ ]` **Performance & UX (Trải nghiệm người dùng)**
  - `[ ]` **Skeleton Loading:** Hiện khung xương mờ mờ chờ gọi API thay vì "Loading...".
  - `[ ]` **Lazy Loading Video:** Tối ưu load thẻ `<video>` để tiết kiệm băng thông.
  - `[ ]` **Debounce/Throttle API:** Hạn chế bấm nút "Generate" liên tục.
- `[ ]` **Security & Khử lỗi**
  - `[ ]` **Rate Limiting:** Chặn spam gọi API AI (tránh tốn tiền API).
  - `[ ]` **Hide Secrets:** Gom API Key (Gemini, Supabase) vào file `.env`, không lộ lên Github.
  - `[ ]` **Input Validation:** Chặn mã độc (XSS) khi người dùng nhập dữ liệu.
  - `[ ]` **Swagger API Docs:** Viết document API backend để code FE/Mobile dễ đọc.
  - `[ ]` **Error Logging:** Bắt lỗi `500` thành thông báo thân thiện cho user.

---

## 🌟 Phase 5: Tính năng cao cấp (Wow Factor)
*Làm cuối cùng để chốt hạ điểm tuyệt đối (A+).*

- `[ ]` **Teacher Analytics & AI Feedback**
  - `[ ]` Backend: Lưu log thời gian xem video, tỷ lệ hoàn thành của học sinh.
  - `[ ]` Frontend: Vẽ biểu đồ thống kê (Chart.js / Recharts) cho Teacher.
  - `[ ]` AI Feedback: Prompt Gemini nhận xét lớp học dựa trên số liệu.
- `[ ]` **Email & Thông báo (Dùng Mailhog lúc dev)**
  - `[ ]` Luồng **Quên mật khẩu (Forgot Password)** qua Email.
  - `[ ]` Tự động bắn email cho học sinh khi Teacher render xong Video mới.
- `[ ]` **Token & Cost Tracking (Quản lý chi phí AI)**
  - `[ ]` Ghi log số Token OpenAI/Gemini đã dùng vào bảng `API_Usage_Log`.
  - `[ ]` Giao diện xem tháng này tốn bao nhiêu USD tiền gọi AI.
