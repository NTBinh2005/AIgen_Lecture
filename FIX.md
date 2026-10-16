# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-16 tại commit `7cab16b` (PR #32–#35). Đã kiểm trên bản sao DB đang dùng (migration V16) và build lại stack của máy FE.
> Các lỗi đã sửa được xoá khỏi danh sách; số thứ tự các mục còn lại giữ nguyên để dễ tham chiếu.

## Cần nhóm thống nhất

**72. Bật bắt buộc mua gói theo mặc định**
- `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM` mặc định `true`: học sinh chưa có gói không xem được bài giảng và không làm được bài; giáo viên chưa có gói không dùng được AI và render video.
- Máy FE đã cấp gói 365 ngày cho `htduong2005@gmail.com`, `teacher@edumind.dev`, `student@edumind.dev`; các tài khoản khác (kể cả tài khoản mới đăng ký) vẫn bị chặn tới khi mua gói hoặc được admin cấp (FE: Admin → Gói đăng ký → Người đăng ký → Cấp gói).
- Khi demo có thể đặt `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM=false` trong `.env`.

## Lỗi backend còn lại

Không còn lỗi gây hỏng chức năng. Hai điểm nhỏ nên chỉnh:
- Tạo trùng yêu cầu hoàn tiền trả **400** ("Giao dịch này đã có yêu cầu hoàn tiền"); nên là **409** cho thống nhất với các lỗi trùng khác. Tạo yêu cầu trả 200, nên là 201.
- Thông báo cho học viên khi duyệt và khi đã hoàn tiền cùng tiêu đề "Cập nhật yêu cầu hoàn tiền"; nên tách tiêu đề ("Yêu cầu hoàn tiền đã được duyệt" / "Đã hoàn tiền") để học viên phân biệt trong chuông thông báo.

## Việc triển khai / vận hành còn lại

**69. Thanh toán và hoàn tiền SePay**
- Khi deploy cần địa chỉ HTTPS công khai cố định cho `POST /api/payments/webhook/sepay` (ngrok miễn phí đổi URL mỗi lần chạy) và khai báo trong trang quản trị SePay cùng `SEPAY_WEBHOOK_SECRET`.
- Hoàn tiền tự hoàn tất nhờ webhook **tiền ra**: cần bật sự kiện Money out và tiền tố mã **HT** trong SePay, đặt `SEPAY_REFUND_SOURCE_ACCOUNT` nếu chuyển trả từ tài khoản khác. SePay chỉ hỗ trợ báo tiền ra với Sacombank, TPBank, VietinBank (tài khoản TKP) — tài khoản MB cá nhân hiện tại chỉ dùng để thử, tài khoản chính thức khi triển khai cần thuộc danh sách này.
- Email hoá đơn gửi bằng `MAIL_USERNAME` / `MAIL_PASSWORD` sau khi thanh toán thành công; chưa cấu hình thì bỏ qua, không ảnh hưởng thanh toán. Chưa thử gửi email thật.
- Dữ liệu cũ: có 1 yêu cầu hoàn tiền cũ (#1, chờ duyệt) chưa có tài khoản nhận → học viên bổ sung tài khoản (nút "Thêm tài khoản" ở trang Gói & thanh toán) hoặc admin từ chối để học viên gửi lại.

## Ghi chú

- **#41 (ghi hình / người tham gia buổi live) đã bỏ:** FE web đã gỡ phần "Bản ghi hình" và "Người tham gia phòng họp" (15/10); mobile không gọi các API này (theo báo cáo của nhóm). Backend vẫn còn các endpoint `…/recording…` và `ParticipantController` (`/live-sessions/{id}/participants`) — nên xoá hẳn cho gọn.

- **Phân trang chặt hơn (đợt này):** `size` > 100 hoặc `page` < 0 giờ trả 400 thay vì tự sửa. FE web đã đổi các chỗ gọi `size=200` / `size=500` (danh sách quiz, bài giảng) sang gộp nhiều trang 100 bản ghi; **app mobile cần kiểm tra** nếu đang gọi `size` lớn.
- **Đổi định dạng API (#74) — client khác cần cập nhật:** các endpoint sau giờ trả `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`) thay vì mảng: `/notifications`, `/audit-logs`, `/classes`, `/enrollments`, `/users`, `/invoices`, `/payments/admin`, `/refunds`, `/subscriptions`, `/exports`, `/lectures/{id}/comments`, `/students/me/quiz-history`, `/quiz-assignments/{id}/progress`. FE web đã cập nhật (15/10); **app mobile cần cập nhật** nếu đang gọi các endpoint này.
- **Lỗi thiếu gói:** client nên dựa vào `code: "SUBSCRIPTION_REQUIRED"` trong body lỗi 403 để nhắc người dùng mua gói.
- **Chưa kiểm được với AI thật:** các đợt gần đây Gemini trả 429 / 503, nên chưa xác nhận nội dung bài giảng khi chọn ngôn ngữ `en` (#36) và hành vi khi Gemini lỗi (#44).
- Video cũ lưu trên project Supabase `gbhhlacjwkztlbwzjsuo` không truy cập được (tên miền không phân giải), cần khôi phục hoặc upload lại nếu còn cần.
- Dữ liệu cũ của lỗi #2 (snapshot quiz thiếu đáp án, xuất bản ngày 03/10) chỉ có ở 3 quiz thử của tài khoản `fixcheck…`; đã xử lý trên máy FE ngày 15/10: lưu trữ cả 3 quiz, gỡ 2 bài giao chưa có ai làm, đóng 2 bài giao đã có bài làm (giữ lại 4 lượt làm cũ, điểm trắc nghiệm của chúng vẫn sai). Nếu DB trên máy chủ thật có quiz xuất bản trước bản sửa #2 thì cần xử lý tương tự.
