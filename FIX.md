# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-14 (đêm) tại commit `61f69d5` (PR #22–#24, kèm [báo cáo sửa backend](docs/BACKEND_FIX_REPORT_2026-10-14.md)).
> Cách kiểm tra: build image backend, chạy trên **bản sao DB đang dùng** của máy FE (đang ở V7) và trên một **DB trống**;
> gọi lại toàn bộ bộ kiểm tra (~110 trường hợp phân quyền / dữ liệu đầu vào / lỗi 500, quét mọi endpoint GET với 6 danh tính),
> giả lập IPN VNPay bằng khoá thử riêng của môi trường kiểm tra, và mở giao diện web bằng Chrome.
>
> **Kết quả:** trên DB đang dùng, 7 migration V8–V14 chạy thành công, backend khởi động bình thường, không còn endpoint GET nào trả 500.
> Trên DB trống thì backend **không khởi động được** (#70).
>
> **Đã sửa và xác nhận trong đợt này, nên đã xoá khỏi danh sách:**
> #25 (PATCH lớp phân biệt bỏ qua và `null`), #40 (admin xem hoá đơn, `check-user` chỉ cho admin, thông báo tiếng Việt),
> #41 (tài nguyên buổi live dùng UUID thật, kiểm tra tồn tại), #43 (file Excel / PDF có tên học sinh, điểm, câu trả lời; PDF hiển thị tiếng Việt),
> #45 (tìm giáo viên, cộng tác viên có tên / email, khôi phục phiên bản bài giảng, đăng xuất thu hồi access token),
> #46 (thêm sửa / gỡ bài đã giao; quy ước UTC đã ghi vào tài liệu API), #47 (gói bắt buộc cho tính năng, nâng cấp gói giữ ngày còn lại,
> thu hồi quyền gói cũ, hoàn tiền trừ thời hạn, lưu giá lúc mua, admin cấp / gia hạn / huỷ gói), #52 (logout token rác trả 400),
> #54 (signal bắt buộc `{"type":"TAB_SWITCH"}`, `GET /users` phân trang, tìm kiếm tối thiểu 3 ký tự), #57 (buổi `SCHEDULED` quá giờ cũng tự kết thúc),
> #60 (thông báo học tập + `targetType` / `targetId` + SSE `/notifications/stream`), #62 (compose có `VIDEO_PUBLIC_BASE_URL`, `APP_SWAGGER_ENABLED`),
> #63 (khoá đăng nhập theo email + địa chỉ, lưu DB), #64 (tạo được buổi "bắt đầu ngay"), #67 (email trùng không còn gây 500),
> #68 (mặc định `gemini-3.8-flash`), #16b (thống kê đọc từ DB, uptime thật, chi phí AI trả `null` khi chưa có số liệu).
>
> **Đã sửa từ các đợt trước:** #1–#10, #12–#24, #26–#34, #37, #39, #42, #48–#51, #53, #55, #56, #59, #61, #65, #66.
> Các mục còn lại giữ nguyên số để dễ tham chiếu.

## Lỗi mới (kiểm tra 14/10, commit `61f69d5`)

**70. Backend không khởi động được trên DB trống** (NGHIÊM TRỌNG — chặn cài máy mới / server mới)
- Chạy backend với một Postgres trống: Flyway chạy V1–V14 xong, sau đó `data.sql` chèn tài khoản mẫu thì lỗi `null value in column "email_ambiguous" of relation "users" violates not-null constraint`, ứng dụng dừng (`Application run failed`).
- Nguyên nhân: cột `email_ambiguous` (và có thể `token_version`) được Hibernate tạo `NOT NULL` không có giá trị mặc định trước khi migration chạy; lệnh `INSERT` trong `data.sql` không ghi cột này. Cùng kiểu lỗi với #65 trước đây.
- Không ảnh hưởng DB đã có dữ liệu (đã thử trên bản sao DB của máy FE: chạy bình thường).
- Cách sửa: ghi rõ `email_ambiguous = false`, `token_version = 0` trong `data.sql`, hoặc khai báo giá trị mặc định ở entity (`columnDefinition` có `DEFAULT`) để Hibernate tạo cột có default.

**71. Bị chặn vì chưa mua gói nhưng nhận thông báo "không có quyền" chung chung**
- `PremiumAccessService` ném `AccessDeniedException("Bạn cần gói đăng ký còn hiệu lực để sử dụng tính năng này")`, nhưng bộ xử lý lỗi chung ghi đè mọi `AccessDeniedException` thành "Bạn không có quyền thực hiện hành động này". Client không phân biệt được "chưa mua gói" với "sai quyền".
- Đã thử: học sinh chưa có gói gọi `GET /lectures/student`, `POST /quiz-assignments/{id}/start`; giáo viên chưa có gói gọi `POST /quizzes/ai-generate`, `POST /lectures/{id}/video-render` → đều 403 với thông báo chung.
- Cần: trả mã riêng (VD 402 hoặc 403 kèm `code: "SUBSCRIPTION_REQUIRED"`) và giữ nguyên thông báo gốc. FE web tạm tự kiểm tra `GET /subscriptions/me` để hiện dải nhắc mua gói.

**72. Bật bắt buộc mua gói theo mặc định: mọi tài khoản chưa có gói mất quyền ngay khi nâng cấp** (cần nhóm thống nhất)
- `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM` mặc định `true` trong `application.properties` và `docker-compose.yml`. Sau khi build lại, học sinh chưa có gói không xem được danh sách / nội dung bài giảng và không làm được bài; giáo viên chưa có gói không dùng được AI và render video. Lịch học, lớp học, thông báo vẫn dùng được.
- DB của máy FE hiện không có gói nào đang hoạt động, nên mọi tài khoản học sinh / giáo viên (kể cả tài khoản mẫu) đều bị chặn sau khi nâng cấp.
- Chưa có khoá cổng thanh toán hợp lệ (xem #69) thì cách duy nhất để có gói là admin cấp thủ công (FE: Admin → Gói đăng ký → Người đăng ký → Cấp gói), hoặc đặt `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM=false` trong `.env` khi demo.

**73. Thông báo realtime (SSE): tải lại trang vài lần là bị chặn, và lỗi trả 500**
- `NotificationStreamService` giới hạn 5 kết nối mỗi tài khoản nhưng không giải phóng kết nối khi trình duyệt đóng / tải lại trang (chỉ hết khi timeout 120 giây). Mở và tải lại trang khoảng 5 lần trong 2 phút là không kết nối được nữa.
- Khi vượt giới hạn, `BadRequestException("Đã mở quá nhiều kết nối thông báo")` bị trả thành **500** (ném trong handler `text/event-stream` nên không đi qua bộ xử lý lỗi JSON).
- Cần: gỡ emitter trong `onCompletion` / `onError` / khi ghi heartbeat thất bại; khi vượt giới hạn thì đóng kết nối cũ nhất thay vì từ chối kết nối mới, hoặc trả 429 đúng chuẩn.

**74. Phần lớn API danh sách chưa có phân trang** (hiệu năng, cần làm trước khi có nhiều người dùng)
- Trong 47 endpoint GET trả về danh sách, chỉ 5 endpoint có phân trang: `GET /lectures`, `GET /lectures/student`, `GET /quizzes`, `GET /presentations` (trả `Page`) và `GET /users` (nhận `page` / `size` tối đa 100 nhưng vẫn trả mảng, không có tổng số phần tử / số trang). Các endpoint còn lại trả toàn bộ dữ liệu trong một lần.
- Cần thêm phân trang (`page`, `size`, trả `Page` có `totalElements` / `totalPages`, mặc định sắp xếp mới nhất trước) cho các endpoint sẽ phình to theo thời gian:

  | Endpoint | Màn hình dùng | Tăng theo |
  |---|---|---|
  | `GET /audit-logs` | Admin → Nhật ký | Mỗi lần đăng nhập / thao tác. Khi không lọc chỉ trả 100 bản ghi mới nhất (`findTop100ByOrderByCreatedAtDesc`), không xem được nhật ký cũ hơn |
  | `GET /notifications` | Chuông thông báo | Mỗi bài giảng / quiz / điểm / bình luận × số học sinh |
  | `GET /payments/admin`, `GET /refunds`, `GET /invoices` (admin), `GET /subscriptions` | Admin → Thanh toán, Gói đăng ký | Mỗi giao dịch / lần mua |
  | `GET /enrollments`, `GET /classes` | Admin → Lớp học, Thống kê | Số lớp × số học sinh |
  | `GET /quiz-assignments/{id}/progress` | Giáo viên → Bài nộp & chấm điểm | Học sinh × số lượt, kèm toàn bộ câu trả lời |
  | `GET /students/me/quiz-history` | Học sinh → Kết quả học tập | Mỗi lần nộp bài, kèm toàn bộ câu trả lời |
  | `GET /lectures/{id}/comments` | Bình luận bài giảng | Mỗi bình luận |
  | `GET /exports` | Lịch sử file xuất | Mỗi lần xuất |

- `GET /users` nên trả `Page` như các endpoint trên để FE biết tổng số và hiện phân trang; hiện FE admin chỉ tải trang đầu (100 người).
- Riêng `progress` và `quiz-history` nên có thêm bản tóm tắt (không kèm `answers`), chỉ tải câu trả lời khi mở chi tiết một lượt làm.
- Các danh sách nhỏ gắn với một đối tượng (gói, phiên bản, cộng tác viên, lịch / buổi live / bài giao của một lớp, tài nguyên buổi live, gói / giao dịch của chính mình) chưa cần phân trang. `GET /users/students` và `GET /users/teachers` đã giới hạn 20 kết quả.
- Sau khi backend đổi, FE cần cập nhật các trang tương ứng (hiện đang đọc các endpoint này dưới dạng mảng).

## Lỗi còn lại từ các đợt trước

**41. Ghi hình và người tham gia buổi live** (phần tài nguyên đã sửa)
- `POST /live-sessions/{id}/recording/request` tạo bản ghi `REQUESTED` rồi đứng yên; `GET /live-sessions/{id}/participants` luôn rỗng. Cả hai cần tích hợp nhà cung cấp họp trực tuyến (API + webhook).
- `V11` chuyển tài nguyên dùng id số cũ sang bảng `legacy_session_resource_links`; cần thêm lại bằng UUID thật nếu còn dùng.

**58. Thông báo lỗi còn lẫn tiếng Anh / không dấu**
- Vẫn gặp trong đợt này: "Cannot enroll an inactive student", "Question points must be greater than zero", "The correct answer must be one of the options", "closeAt must be in the future", "HTTP method is not supported for this endpoint", "AI lecture generation failed: HTTP 429", "Email da duoc su dung: …" (không dấu). FE hiển thị nguyên văn cho người dùng.
- `GET /error` vẫn trả 500.

**69. Cổng thanh toán** (cấu hình)
- Máy FE đã có khoá VNPay và MoMo trong `.env`; tạo giao dịch thành công ở cả hai cổng. Nhưng link VNPay mở ra trang lỗi `code=71` (theo bảng mã của VNPay: mã website không tồn tại hoặc chưa kích hoạt trên sandbox) → cần kiểm tra lại `VNPAY_TMN_CODE` với tài khoản sandbox.
- Chạy ở `localhost` thì VNPay / MoMo không gọi được IPN về backend, giao dịch nằm ở "Chờ thanh toán". Muốn thử trọn luồng cần địa chỉ công khai (VD ngrok) cho `VNPAY_RETURN_URL`, `MOMO_NOTIFY_URL` và khai báo với cổng thanh toán.
- Hoàn tiền vẫn không gọi cổng thanh toán; admin tự chuyển tiền rồi mới đánh dấu "Đã hoàn tiền".

## Ghi chú

- **Nâng cấp DB đang có dữ liệu:** Flyway chạy V8–V14. Lúc khởi động, log có vài cảnh báo `Error executing DDL ... contains null values` do Hibernate thử thêm cột `NOT NULL` trước Flyway; các cột sau đó được migration tạo đúng (đã kiểm tra: `token_version` mặc định 0, `email_ambiguous` mặc định false, `session_resources.resource_id` kiểu `uuid`), không cần xử lý.
- **Chưa kiểm được với AI thật:** lúc kiểm tra Gemini trả 429 (hết hạn mức), nên chưa xác nhận nội dung bài giảng khi chọn ngôn ngữ `en` (#36) và hành vi khi Gemini lỗi (#44). Tham số `language` đã nhận đúng (`auto` / `vi` / `en`, giá trị khác trả 400).
- Video cũ lưu trên project Supabase `gbhhlacjwkztlbwzjsuo` không truy cập được (tên miền không phân giải), cần khôi phục hoặc upload lại nếu còn cần.
- Quiz đã xuất bản **trước** bản sửa #2 vẫn có snapshot thiếu đáp án nên vẫn chấm sai; cần tạo / xuất bản lại quiz đó.
- `GET /lectures` của ADMIN mới lọc theo `title`, chưa lọc theo giáo viên / trạng thái.
