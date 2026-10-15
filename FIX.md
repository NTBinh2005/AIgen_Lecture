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
> #58 (các lỗi đã nêu đều trả tiếng Việt; lỗi còn sót từ nhánh cũ được chặn ở bộ xử lý lỗi chung, không lộ chuỗi tiếng Anh ra API),
> #75 (`GET /lectures` đã lọc kết hợp theo `title`, `teacherId`, `status`, `videoStatus`, `accessScope` và giữ đúng phạm vi của TEACHER),
> `GET /error` không còn trả 500.
>
> **Đã sửa từ các đợt trước:** #1–#40, #42–#57, #59–#68, #16b, #17b.
> Các mục còn lại giữ nguyên số để dễ tham chiếu.

## Cần nhóm thống nhất

**72. Bật bắt buộc mua gói theo mặc định**
- `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM` mặc định `true`: học sinh chưa có gói không xem được bài giảng và không làm được bài; giáo viên chưa có gói không dùng được AI và render video.
- Máy FE đã cấp gói 365 ngày cho `htduong2005@gmail.com`, `teacher@edumind.dev`, `student@edumind.dev`; các tài khoản khác (kể cả tài khoản mới đăng ký) vẫn bị chặn tới khi mua gói hoặc được admin cấp (FE: Admin → Gói đăng ký → Người đăng ký → Cấp gói).
- Khi demo có thể đặt `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM=false` trong `.env`.

## Lỗi backend còn lại

Không còn lỗi source nào trong các mục review còn lại của tài liệu này. Mục #72 là quyết định sản phẩm; các việc dưới đây cần môi trường hoặc dịch vụ ngoài repository.

## Việc triển khai / vận hành còn lại

**69. Thanh toán SePay** (đã chuyển từ VNPay / MoMo sang SePay)
- Đã kiểm trên bản sao DB (15/10): migration `V15` chạy được; tạo giao dịch trả ảnh VietQR đúng ngân hàng / số tài khoản / số tiền / nội dung `DH…`; webhook sai chữ ký hoặc quá 5 phút → 401, sai số tiền hoặc sai tài khoản nhận → 400; webhook đúng → giao dịch `SUCCESS`, kích hoạt gói, tạo hoá đơn; gửi lại cùng webhook không cộng gói lần hai.
- Chạy ở `localhost` thì SePay không gọi được webhook về backend, giao dịch nằm ở "Chờ thanh toán". Cần địa chỉ công khai (VD ngrok) cho `POST /api/payments/webhook/sepay` và khai báo trong trang quản trị SePay cùng `SEPAY_WEBHOOK_SECRET`.
- Cơ chế HMAC hiện tại đã khớp tài liệu chính thức của SePay: `X-SePay-Signature: sha256=HMAC_SHA256(secret, timestamp + "." + rawBody)` cùng `X-SePay-Timestamp`. Không cần đổi sang xác thực `Authorization: Apikey`.
- Giao dịch `PENDING` đã tự chuyển `CANCELLED` sau 15 phút (cấu hình bằng `SEPAY_PENDING_EXPIRATION_MINUTES`); lịch quét mặc định mỗi 60 giây.
- SePay QR/chuyển khoản không cung cấp luồng hoàn tiền tự động trong tích hợp hiện tại. Admin vẫn phải chuyển khoản hoàn tiền, đối soát rồi mới đánh dấu "Đã hoàn tiền"; đây là quy trình vận hành, không phải lỗi callback.

## Ghi chú

- **Đổi định dạng API (#74) — client khác cần cập nhật:** các endpoint sau giờ trả `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`) thay vì mảng: `/notifications`, `/audit-logs`, `/classes`, `/enrollments`, `/users`, `/invoices`, `/payments/admin`, `/refunds`, `/subscriptions`, `/exports`, `/lectures/{id}/comments`, `/students/me/quiz-history`, `/quiz-assignments/{id}/progress`. FE web đã cập nhật (15/10); **app mobile cần cập nhật** nếu đang gọi các endpoint này.
- **Lỗi thiếu gói:** client nên dựa vào `code: "SUBSCRIPTION_REQUIRED"` trong body lỗi 403 để nhắc người dùng mua gói.
- **Chưa kiểm được với AI thật:** các đợt gần đây Gemini trả 429 / 503, nên chưa xác nhận nội dung bài giảng khi chọn ngôn ngữ `en` (#36) và hành vi khi Gemini lỗi (#44).
- Video cũ lưu trên project Supabase `gbhhlacjwkztlbwzjsuo` không truy cập được (tên miền không phân giải), cần khôi phục hoặc upload lại nếu còn cần.
- Dữ liệu cũ của lỗi #2 (snapshot quiz thiếu đáp án, xuất bản ngày 03/10) chỉ có ở 3 quiz thử của tài khoản `fixcheck…`; đã xử lý trên máy FE ngày 15/10: lưu trữ cả 3 quiz, gỡ 2 bài giao chưa có ai làm, đóng 2 bài giao đã có bài làm (giữ lại 4 lượt làm cũ, điểm trắc nghiệm của chúng vẫn sai). Nếu DB trên máy chủ thật có quiz xuất bản trước bản sửa #2 thì cần xử lý tương tự.
