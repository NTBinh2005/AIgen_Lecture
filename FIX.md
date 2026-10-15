# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-15 tại commit `cd3a9b6` (PR #25–#26).
> Cách kiểm tra: build image backend, chạy trên một **DB trống** và trên **bản sao DB đang dùng** của máy FE (đang ở V14, đợt này không có migration mới);
> gọi lại bộ kiểm tra phân quyền / dữ liệu đầu vào / mã lỗi, kiểm tra 15 API danh sách mới phân trang, thông báo realtime (SSE),
> giả lập IPN VNPay bằng khoá thử riêng của môi trường kiểm tra, và mở các trang web của 3 vai trò bằng Chrome với dữ liệu thật.
>
> **Kết quả:** backend khởi động được trên cả DB trống lẫn DB đang dùng; không thấy lỗi hồi quy về phân quyền.
>
> **Đã sửa và xác nhận trong đợt này, nên đã xoá khỏi danh sách:**
> #70 (backend khởi động được trên DB trống, đăng nhập được tài khoản mẫu),
> #71 (chưa có gói trả 403 kèm `code: "SUBSCRIPTION_REQUIRED"` và thông báo riêng; sai quyền vẫn là thông báo chung),
> #73 (SSE: kết nối thứ 6 trở đi đóng kết nối cũ nhất thay vì trả 500 — đã thử 7 kết nối: cả 7 đều 200, 2 kết nối đầu bị đóng),
> #74 (15 API danh sách trả `Page` có `totalElements` / `totalPages`, mặc định 20, tối đa 100; kết quả học tập và tiến độ chấm bài chỉ trả tóm tắt, câu trả lời lấy qua `GET /attempts/{id}`),
> `GET /error` không còn trả 500.
>
> **Đã sửa từ các đợt trước:** #1–#40 (trừ #41), #42–#57, #59–#68, #16b, #17b.
> Các mục còn lại giữ nguyên số để dễ tham chiếu.

## Cần nhóm thống nhất

**72. Bật bắt buộc mua gói theo mặc định**
- `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM` mặc định `true`: học sinh chưa có gói không xem được bài giảng và không làm được bài; giáo viên chưa có gói không dùng được AI và render video.
- Máy FE đã cấp gói 365 ngày cho `htduong2005@gmail.com`, `teacher@edumind.dev`, `student@edumind.dev`; các tài khoản khác (kể cả tài khoản mới đăng ký) vẫn bị chặn tới khi mua gói hoặc được admin cấp (FE: Admin → Gói đăng ký → Người đăng ký → Cấp gói).
- Khi demo có thể đặt `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM=false` trong `.env`.

## Lỗi còn lại

**41. Ghi hình và người tham gia buổi live** (phần tài nguyên đã sửa)
- `POST /live-sessions/{id}/recording/request` tạo bản ghi `REQUESTED` rồi đứng yên; `GET /live-sessions/{id}/participants` luôn rỗng. Cả hai cần tích hợp nhà cung cấp họp trực tuyến (API + webhook).
- `V11` chuyển tài nguyên dùng id số cũ sang bảng `legacy_session_resource_links`; cần thêm lại bằng UUID thật nếu còn dùng.

**58. Thông báo lỗi** (đã dịch phần lớn, còn sót vài chỗ)
- Đã sửa trong đợt này: "Email đã được sử dụng" có dấu, lỗi 404 / 405, điểm câu hỏi, hạn nộp đã chuyển sang tiếng Việt.
- Còn gặp: "Student not found: 99999" (thêm học sinh không tồn tại vào lớp), "meetingUrl must use http or https" (tạo buổi live), "AI lecture generation failed: HTTP 429" (khi Gemini hết hạn mức).
- Giáo viên tạo hai lớp **trùng tên** thì nhận 409 "Dữ liệu vi phạm ràng buộc hoặc bị trùng" (ràng buộc `uk_classes_teacher_class_name`), không nói rõ là trùng tên lớp. Nên kiểm tra trước và báo "Bạn đã có lớp tên …".
- Phân trang chưa thống nhất cách xử lý tham số sai: `size=500` trả 400 "page phải >= 0, size từ 1 đến 100", nhưng `page=-1` lại được chấp nhận và trả trang đầu.

**69. Thanh toán SePay** (đã chuyển từ VNPay / MoMo sang SePay; phần còn lại là triển khai)
- Đã kiểm trên bản sao DB (15/10): migration `V15` chạy được; tạo giao dịch trả ảnh VietQR đúng ngân hàng / số tài khoản / số tiền / nội dung `DH…`; webhook sai chữ ký hoặc quá 5 phút → 401, sai số tiền hoặc sai tài khoản nhận → 400; webhook đúng → giao dịch `SUCCESS`, kích hoạt gói, tạo hoá đơn; gửi lại cùng webhook không cộng gói lần hai.
- Chạy ở `localhost` thì SePay không gọi được webhook về backend, giao dịch nằm ở "Chờ thanh toán". Cần địa chỉ công khai (VD ngrok) cho `POST /api/payments/webhook/sepay` và khai báo trong trang quản trị SePay cùng `SEPAY_WEBHOOK_SECRET`.
- Cần xác nhận định dạng chữ ký: backend đòi header `X-SePay-Signature: sha256=HMAC_SHA256(secret, timestamp + "." + body)` và `X-SePay-Timestamp`. Nếu webhook SePay thật chỉ gửi `Authorization: Apikey …` thì mọi webhook thật sẽ bị 401 → cần đối chiếu tài liệu SePay khi nối thử lần đầu.
- Giao dịch `PENDING` không tự hết hạn; hoàn tiền không gọi cổng, admin tự chuyển khoản rồi đánh dấu "Đã hoàn tiền".

**75. Danh sách bài giảng của Admin chưa lọc được theo giáo viên / trạng thái** (thiếu chức năng)
- `GET /lectures` hiện chỉ nhận `title` (tìm theo tên) cùng `page` / `size` / `sort` ([LectureController.java:124](backend/src/main/java/com/example/demo/controller/LectureController.java#L124)). Tham số khác bị bỏ qua.
- Trang Admin → Bài giảng cần lọc thêm để quản lý khi có nhiều giáo viên. FE không tự lọc được vì chỉ có một trang dữ liệu mỗi lần tải.
- Cần thêm tham số tuỳ chọn, kết hợp được với `title` và phân trang:
  - `teacherId` (Integer): bài giảng của một giáo viên;
  - `status` (`DRAFT` / `PROCESSING` / `READY` / `PUBLISHED` / `FAILED` / `ARCHIVED`): lọc theo trạng thái; giá trị sai trả 400;
  - (nên có) `videoStatus` và `accessScope` (`PRIVATE` / `CLASS`).
- Chỉ ADMIN dùng được `teacherId` của người khác; với TEACHER thì giữ nguyên phạm vi hiện tại (bài của mình / được cộng tác).
- Sau khi backend thêm, FE sẽ bổ sung ô chọn giáo viên và trạng thái ở trang Admin → Bài giảng.

## Ghi chú

- **Đổi định dạng API (#74) — client khác cần cập nhật:** các endpoint sau giờ trả `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`) thay vì mảng: `/notifications`, `/audit-logs`, `/classes`, `/enrollments`, `/users`, `/invoices`, `/payments/admin`, `/refunds`, `/subscriptions`, `/exports`, `/lectures/{id}/comments`, `/students/me/quiz-history`, `/quiz-assignments/{id}/progress`. FE web đã cập nhật (15/10); **app mobile cần cập nhật** nếu đang gọi các endpoint này.
- **Lỗi thiếu gói:** client nên dựa vào `code: "SUBSCRIPTION_REQUIRED"` trong body lỗi 403 để nhắc người dùng mua gói.
- **Chưa kiểm được với AI thật:** các đợt gần đây Gemini trả 429 / 503, nên chưa xác nhận nội dung bài giảng khi chọn ngôn ngữ `en` (#36) và hành vi khi Gemini lỗi (#44).
- Video cũ lưu trên project Supabase `gbhhlacjwkztlbwzjsuo` không truy cập được (tên miền không phân giải), cần khôi phục hoặc upload lại nếu còn cần.
- Dữ liệu cũ của lỗi #2 (snapshot quiz thiếu đáp án, xuất bản ngày 03/10) chỉ có ở 3 quiz thử của tài khoản `fixcheck…`; đã xử lý trên máy FE ngày 15/10: lưu trữ cả 3 quiz, gỡ 2 bài giao chưa có ai làm, đóng 2 bài giao đã có bài làm (giữ lại 4 lượt làm cũ, điểm trắc nghiệm của chúng vẫn sai). Nếu DB trên máy chủ thật có quiz xuất bản trước bản sửa #2 thì cần xử lý tương tự.
