# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-14 (tối). Rà soát lại toàn bộ backend tại commit `2674ae6` trên DB trống: không có lỗi hồi quy, các mục bên dưới vẫn đúng. PR #20 kiểm tra tại commit `2674ae6`; subscription tại `f263e6a` (PR #19); các mục khác tại commit `1c4ce20` (PR #18, commit `75036aa` "[FIX]fix-all-error"). Đã kiểm tra lại bằng API thật:
> build image backend từ source, chạy trên một Postgres trống với tài khoản seed teacher/student/admin cộng thêm
> một giáo viên và một học sinh thứ hai. Backend biên dịch và khởi động được, 5 migration Flyway chạy thành công.
>
> **Đã sửa và xác nhận trong đợt 14/10, nên đã xoá khỏi danh sách:** #27 (bài nháp trả `hasUnpublishedChanges: false`),
> #33 (video chỉ render khi giáo viên yêu cầu; trạng thái mặc định mới là `NOT_REQUESTED`), #34 (quyền admin trên
> bài giảng thống nhất, admin xoá được), #37 (đã bỏ webhook thanh toán không xác thực), #39 (hoá đơn là PDF thật),
> #42 (trả `aiSuggestedScore`), #48 (không tự đăng ký được ADMIN, kể cả qua Google/SMS), #49 (chấm/chốt/mở lại/xem trước
> có kiểm tra quyền, chặn điểm ngoài khoảng, mở lại có đặt lại hạn), #50 (học sinh ngoài lớp bị 403), #51 (không dùng
> được bài giảng/quiz/lớp của giáo viên khác), #53 (email không phân biệt hoa thường, giới hạn đăng nhập sai, token của
> tài khoản bị khoá hết hiệu lực, mật khẩu admin tạo tối thiểu 8 ký tự), #55 (`meetingUrl` chỉ nhận http/https),
> #56 (tác giả, giáo viên chủ bài và admin xoá được bình luận), #57 (buổi `OPEN`/`LIVE` tự chuyển `ENDED` khi quá giờ),
> #61 (đã có `.gitmodules`, hướng dẫn tải model, `start_all.ps1` dùng `AVATAR_ENGINE=auto`), #17b (giáo viên phụ và admin
> xem được bài làm), #30 (admin không tự khoá được mình), #13 và #15 (cổng Postgres và cổng API mobile).
>
> **Đã sửa từ các đợt trước:** #1–#10, #12, #14, #16–#24, #26, #28, #29, #31, #32.
> Mục #59 (việc của FE web) đã làm xong toàn bộ ngày 14/10 nên cũng đã xoá.
> Các mục còn lại giữ nguyên số để dễ tham chiếu; mục chỉ sửa được một phần đã rút gọn còn phần chưa xong.

## Kiểm tra PR #20 (commit `f406d95` "[FIX]error", 14/10)

> Đã build backend ở commit `2674ae6` và chạy trên **bản sao DB đang dùng** (đang ở V5): migration V6 và V7 chạy thành công, backend khởi động bình thường.
> **Đã sửa và xoá khỏi danh sách:** #65 (V6 ghi đủ giá trị cho các cột bắt buộc, có 3 gói mẫu), #66 (bỏ khoá VNPay/MoMo mặc định; IPN ký bằng khoá cũ bị từ chối "Chữ ký không hợp lệ"; IPN còn đối chiếu số tiền), #38 (phần MoMo chuyển thành việc cấu hình, xem #69).
> V7 sửa thêm một lỗi chưa ghi: DB nâng cấp từ bản cũ còn ràng buộc `lectures_video_status_check` không có `NOT_REQUESTED`, nên **backend đang chạy trên máy FE (bản V5) không tạo được bài giảng mới** (409 "Database constraint violation", đã thử 14/10). Build lại với bản này là hết.

**68. Model Gemini mặc định mới `gemini-2.5-flash` trả 404 — tạo bài giảng bằng AI hỏng sau khi build lại** (lỗi mới)
- PR #20 đổi mặc định `GEMINI_API_URL` trong `application.properties` và `docker-compose.yml` từ `gemini-3.8-flash` sang `gemini-2.5-flash`. Gọi `POST /lectures/generate-from-file` với model mới: 409 "AI lecture generation failed: HTTP 404" (model không tồn tại). Cùng file, cùng khoá API với `gemini-3.8-flash`: lần đầu 503 (quá tải), lần sau 200 và sinh đủ slide + 5 câu quiz.
- `.env` của máy FE không đặt `GEMINI_API_URL` nên sẽ nhận mặc định mới này khi build lại.
- Cần: trả mặc định về model đang dùng được (`gemini-3.8-flash`), hoặc ghi `GEMINI_API_URL` vào `.env.example` để mọi máy cấu hình giống nhau.

**69. Thanh toán bị tắt khi chưa cấu hình khoá cổng thanh toán** (cấu hình, cần nhóm thống nhất)
- Sau #66, VNPay và MoMo không còn khoá mặc định. `.env` hiện chưa có `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `MOMO_*`, nên `POST /payments` trả 400 "Cổng VNPay chưa được cấu hình (thiếu VNPAY_TMN_CODE / VNPAY_HASH_SECRET)" (MoMo tương tự). Học sinh và giáo viên **không mua được gói nào** cho tới khi có khoá.
- Cần: lấy khoá sandbox VNPay (và MoMo nếu dùng) rồi đặt vào `.env` của từng máy / máy chủ, không commit vào git. MoMo trước đây dùng khoá test và trả `resultCode 13 "The merchant configuration is incorrect"`, nên cũng cần khoá merchant hợp lệ.

**67. Email trùng khác hoa thường có sẵn trong DB làm hỏng đăng nhập** (chưa sửa phần dữ liệu cũ)
- Đã chặn tạo mới: admin tạo user trùng khác hoa thường trả 400 "Email already exists".
- Còn lại: chưa có migration chuẩn hoá email cũ và unique index trên `lower(email)`. DB nào đã có hai tài khoản chỉ khác hoa thường thì đăng nhập email đó trả 500 (`IncorrectResultSizeDataAccessException`). Máy FE đã xử lý tạm bằng cách đổi email tài khoản trùng.

## Lỗi mới phát sinh từ bản sửa 14/10

**62. Cấu hình mới chưa được đưa vào `docker-compose.yml`**
- Bản sửa thêm hai biến `VIDEO_PUBLIC_BASE_URL` (video-service) và `APP_SWAGGER_ENABLED` (backend) vào `.env.example`, nhưng `docker-compose.yml` khai báo từng biến môi trường một và không có hai biến này. Chạy bằng Docker thì đặt trong `.env` cũng không có tác dụng.
- Hệ quả: link video lưu tại máy vẫn là `http://localhost:3001/...` (#35 chưa hết với Docker), và `swagger-ui`, `/v3/api-docs` vẫn mở công khai (đã gọi thử: 200).
- Lưu ý khi thêm `VIDEO_PUBLIC_BASE_URL`: `GoogleTtsProvider.ts` cũng dùng biến này để tạo link audio mà Remotion tải **từ bên trong container**. Nếu đặt thành địa chỉ công khai mà container không tự truy cập được thì bước render sẽ không lấy được audio. Nên tách địa chỉ nội bộ (cho audio) và địa chỉ công khai (cho `videoUrl`). Phần này đọc từ code, chưa chạy thử.

**63. Khoá đăng nhập theo email cho phép người lạ khoá tài khoản của người khác**
- `AuthController` đếm số lần sai theo email trong bộ nhớ; sau 5 lần sai thì từ chối mọi lần đăng nhập của email đó trong 5 phút, **kể cả khi nhập đúng mật khẩu**. Đã thử: gọi sai 5 lần với `student@edumind.dev`, sau đó đăng nhập đúng mật khẩu vẫn nhận 401 "Qua nhieu lan dang nhap sai".
- Ai biết email của giáo viên/học sinh đều có thể chặn họ đăng nhập liên tục. Bộ đếm cũng mất khi backend khởi động lại và không dùng chung khi chạy nhiều instance.
- Đề xuất: giới hạn theo cặp email + IP (hoặc theo IP), làm chậm dần thay vì khoá hẳn.

**64. Buổi live không tạo được nếu giờ bắt đầu không ở tương lai**
- `LiveSessionServiceImpl` giờ từ chối `startsAt <= now` (400 "startsAt must be in the future"). Giáo viên không tạo được buổi "bắt đầu ngay" hoặc nhập bù một buổi vừa bắt đầu vài phút trước. Nên cho phép một khoảng trễ (VD 15 phút) hoặc chỉ yêu cầu `endsAt` ở tương lai.
- Buổi `SCHEDULED` mà giáo viên không bao giờ mở sẽ nằm mãi ở `SCHEDULED` sau khi quá giờ: scheduler mới chỉ xử lý `OPEN` và `LIVE` (đọc từ code).

## Lỗi chức năng

**25. `PATCH /classes/{id}`: không xoá được giá trị bằng `null`** (đã sửa phần mất mô tả)
- Đã sửa: request không gửi `description` thì mô tả được giữ nguyên; gửi `description: null` thì xoá.
- Còn lại: `semester`, `endsAt`, `maxStudents` gửi `null` vẫn bị coi là "không đổi" (đã thử: `HK2 / 20` giữ nguyên), nên không bỏ được học kỳ, ngày kết thúc hay giới hạn sĩ số.

**35. Link video khi lưu tại máy; project Supabase không còn truy cập được** (sửa một phần)
- Đã sửa trong code: `renderJob.ts` dùng `VIDEO_PUBLIC_BASE_URL` thay cho `localhost` viết cứng, và khi upload Supabase lỗi thì giữ file đã render và trả link nội bộ thay vì đánh dấu `FAILED`.
- Còn lại: biến này chưa có trong `docker-compose.yml` (xem #62) nên bản Docker vẫn trả `http://localhost:3001/...`.
- Tên miền Supabase `gbhhlacjwkztlbwzjsuo.supabase.co` trong `.env` vẫn không phân giải được; video cũ lưu trên đó không phát được.

**36. Không chọn được ngôn ngữ bài giảng** (sửa một phần)
- Đã sửa: giọng đọc Google không còn viết cứng tiếng Việt mà tự đoán theo ký tự có dấu trong lời giảng; `.env.example` không còn khai báo `TTS_PROVIDER` hai lần.
- Còn lại: vẫn không có tham số `language` cho `/lectures/from-file` và `/lectures/generate-from-file`; prompt vẫn "Preserve the source language", nên tài liệu tiếng Anh luôn ra bài giảng tiếng Anh. Cách đoán theo dấu sẽ đọc lời giảng tiếng Việt không dấu bằng giọng tiếng Anh.

**40. Quản lý thanh toán của Admin** (sửa phần lớn)
- Đã sửa: admin xem được mọi giao dịch và yêu cầu hoàn tiền, duyệt theo từng bước; hoàn tiền `COMPLETED` giờ chuyển giao dịch sang `REFUNDED`, huỷ gói đăng ký và thu hồi quyền học (đã thử: `access/check` trả `false` sau khi hoàn tiền).
- Còn lại:
  - Vẫn không gọi cổng thanh toán để hoàn tiền thật; admin phải tự chuyển tiền.
  - Admin chưa xem được hoá đơn: `GET /invoices` trả mảng rỗng, `GET /invoices/{id}` của người khác trả 400 "Access denied to this invoice".
  - `GET /access/check-user?userId=` cho mọi tài khoản đã đăng nhập tra quyền học của người khác.
  - Thông báo đổi trạng thái hoàn tiền viết bằng tiếng Anh ("Refund status updated").
- FE đã có trang "Thanh toán" của admin (danh sách giao dịch, duyệt / từ chối / hoàn tất hoàn tiền).

**41. Tài nguyên, ghi hình và người tham gia của buổi live chưa dùng được thật** (chưa sửa)
- `SessionResourceRequest.resourceId` vẫn là `Long`, trong khi bản trình chiếu và asset dùng `UUID`, nên không trỏ được tới tài nguyên thật.
- `POST /live-sessions/{id}/recording/request` tạo bản ghi `REQUESTED` rồi đứng yên; `GET /live-sessions/{id}/participants` luôn rỗng vì dữ liệu chỉ đến từ webhook của nhà cung cấp họp trực tuyến.

**43. Xuất kết quả quiz vẫn chưa có dữ liệu thật** (sửa một phần)
- Đã sửa: `GET /exports` trả đúng danh sách job của người gọi; `STUDENT_RESULT_PDF` trả `application/pdf` thật; kiểm tra quyền và id khi tạo/tải.
- Còn lại: nội dung file chỉ là thông tin của chính job. File XLSX có đúng hai ô "Export type" và "Scope" (VD `{"type":"GRADEBOOK_XLSX","assignmentId":2,"classId":3}`); PDF ghi "STUDENT RESULT / Export job / Scope". Chưa có tên học sinh, điểm hay câu trả lời nào.

**44. Tạo slide bằng AI khi Gemini lỗi** (sửa một phần, có thay đổi ngược)
- Đã sửa: khi Gemini lỗi 404/429/500/503 hoặc không kết nối được, job chuyển `FAILED` kèm mã lỗi rõ ràng (`AI_MODEL_NOT_FOUND`, `AI_TEMPORARILY_UNAVAILABLE`, `AI_AUTH_FAILED`…), FE chỉ tự thử lại với lỗi tạm thời; `parameters` / `generationParameters` trả JSON thường.
- Còn lại: PR #20 cố ý cho **khoá Gemini sai (401/403) hoặc chưa cấu hình** quay về sinh slide mẫu từ văn bản tài liệu, job vẫn `DONE` (`LlmServiceImpl`, có test `fallsBackToLocalGenerationWhenGeminiRejectsApiKey`). Giáo viên nhận slide khuôn mẫu mà không biết AI không chạy. Nên ít nhất trả kèm cờ / cảnh báo "nội dung tạo tự động không dùng AI" để FE hiển thị.

**45. Thiếu dữ liệu để FE hiển thị** (sửa một phần)
- Đã sửa: `GET /students/me/quiz-history` có `quizTitle`, `classId`, `className`.
- Còn lại:
  - Cộng tác viên (`GET /lectures/{id}/collaborators`, `GET /presentations/{id}/collaborators`) vẫn chỉ trả mảng `userId` (`[7]`), và TEACHER không có API tìm giáo viên → FE phải bắt nhập ID.
  - Bài giảng không có API khôi phục phiên bản cũ (bản trình chiếu thì có).
  - Đăng xuất chỉ thu hồi refresh token; access token cũ vẫn dùng được (đã thử: 200 sau khi logout).

**46. Thời gian lưu không kèm múi giờ; chưa sửa/xoá được bài đã giao** (chưa sửa)
- Backend ép `TimeZone.setDefault(UTC)` và dùng `LocalDateTime` (không có múi giờ) cho `openAt`, `closeAt`, `startsAt`, `endsAt`, `deadlineAt`… rồi so với `LocalDateTime.now()`. API không quy ước các trường này là UTC hay giờ địa phương.
- FE web đã tự quy ước mọi `LocalDateTime` là UTC (đổi giờ khi gửi và khi hiển thị). Mobile và client khác cần làm giống vậy, nếu không bài kiểm tra giao theo giờ Việt Nam sẽ bị ẩn 7 tiếng. Các kiểm tra mới "phải ở tương lai" (`closeAt`, `startsAt` của buổi live) cũng so theo UTC.
- Vẫn không có `PATCH /quiz-assignments/{id}` và `DELETE /quiz-assignments/{id}` (404), nên giáo viên không chỉnh được giờ mở/hạn nộp hay gỡ bài giao nhầm.
- Cách sửa gốc: dùng `Instant`/`OffsetDateTime` (JSON có `Z`), hoặc ghi rõ quy ước UTC trong tài liệu API.

**47. Gói đăng ký (subscription)** (đã có API, còn thiếu vài phần)
- Đã có và đã chạy thử (14/10, giả lập VNPay bằng khoá thử riêng của môi trường kiểm tra):
  - `GET /plans` (công khai, lọc `?role=`), 3 gói mẫu. Admin quản lý gói: `GET /admin/plans`, `POST /plans`, `PUT /plans/{id}`, `DELETE /plans/{id}` (ngừng bán). Trùng mã 409, mã có ký tự lạ 400, gói cho ADMIN bị chặn, học sinh gọi bị 403.
  - Thanh toán: giá lấy từ gói; IPN sai số tiền bị từ chối; IPN báo thất bại chuyển giao dịch `FAILED`; IPN gửi lại lần hai không kích hoạt trùng.
  - Kích hoạt gói 30 / 365 ngày, phát hành hoá đơn, cấp quyền; mua lại cùng gói khi còn hạn thì **cộng dồn** thời hạn (30 → 60 ngày).
  - `GET /subscriptions/me`, huỷ cuối chu kỳ, tiếp tục; người khác không huỷ được (403). Hoàn tiền xong thì giao dịch `REFUNDED`, gói bị huỷ, quyền bị thu hồi.
- Còn lại:
  - **Gói chưa mở khoá tính năng nào.** Không chỗ nào trong code kiểm tra gói hay quyền học khi xem video, làm bài, tạo bài giảng / quiz bằng AI, render video. Người không mua gói vẫn dùng được mọi thứ như người đã mua.
  - **Nâng cấp gói làm mất phần đã trả:** đang có gói tháng (còn ~60 ngày) mà mua gói năm thì gói tháng bị chuyển `CANCELLED` ngay, không trừ tiền hay cộng ngày còn lại sang gói mới.
  - **Quyền không khớp với trạng thái gói:** sau khi gói tháng bị huỷ do nâng cấp, `GET /access/check?productId=<gói tháng>` vẫn trả `hasAccess: true` vì bản ghi `learning_access` của lần mua đó không bị thu hồi.
  - Hoàn tiền một lần mua đã được cộng dồn (không phải lần mua cuối) chỉ thu hồi quyền của giao dịch đó; thời hạn gói vẫn giữ phần đã cộng thêm.
  - `GET /subscriptions/me` trả thông tin gói **hiện tại** (giá, tên) chứ không phải giá lúc mua: admin đổi giá 150.000 → 99.000 thì gói học sinh đã mua cũng hiện 99.000.
  - Admin không xem được danh sách người đăng ký (`GET /subscriptions` trả 404), không gán / gia hạn / huỷ gói thay người dùng.
  - Không có tự động gia hạn / thu tiền định kỳ: `MONTHLY` và `YEARLY` thực chất là mua một lần có thời hạn; "huỷ cuối chu kỳ" vì vậy không khác gì để gói tự hết hạn.
  - `POST /api/internal/events/payment-succeeded` (ghi danh vào lớp sau khi trả tiền) vẫn không được luồng thanh toán gọi tới.
- Phía FE (đã làm 14/10): trang "Gói & thanh toán" dùng chung cho học sinh và giáo viên (thẻ chọn gói theo vai trò, gói của tôi, huỷ / tiếp tục gia hạn), trang "Gói đăng ký" của admin (tạo, sửa, ngừng / mở bán).

**52. Còn hai trường hợp trả 500** (đã sửa phần lớn)
- Đã sửa: đường dẫn không tồn tại trả 404, sai method trả 405, các id không tồn tại ở chấm bài / tiến độ / quiz / export / migration trả 404.
- Còn lại: `POST /auth/logout` với refresh token không hợp lệ vẫn trả 500 (`/auth/refresh` cùng dữ liệu trả 400).

**54. Thiếu kiểm tra dữ liệu đầu vào** (đã sửa phần lớn)
- Đã sửa: mã lớp duy nhất toàn hệ thống; quiz chặn điểm âm, thiếu lựa chọn, đáp án ngoài lựa chọn; `closeAt` phải ở tương lai; tiêu đề buổi live tối đa 255 ký tự; `timezone` phải là mã IANA và `recurrence` phải dạng `FREQ=...`; `GET /enrollments?status=` sai trả 400; không ghi danh được tài khoản đã khoá.
- Còn lại:
  - `POST /attempts/{id}/signals` vẫn nhận body rỗng hoặc chuỗi 5.000 ký tự (204), mỗi lần gọi tăng bộ đếm rời tab.
  - `GET /users` không phân trang (bỏ qua `page`, `size`: gửi `size=2` vẫn trả toàn bộ).
  - `GET /users/students?email=s` cho mọi giáo viên liệt kê toàn bộ học sinh chỉ với 1 ký tự.

**58. Định dạng lỗi không thống nhất** (sửa một phần)
- Đã sửa: 403 và 401 từ tầng lọc bảo mật đã có trường `message`; `POST /auth/verify` với token sai trả 401.
- Còn lại:
  - Thông báo lỗi lẫn lộn ba kiểu, và các thông báo mới thêm lại bằng tiếng Anh: `"Khong the tu dang ky tai khoan ADMIN"`, `"You do not have permission to perform this action"`, `"Score must be between 0 and 4.0 for question 6"`, `"Bạn không có quyền…"`. FE đang hiển thị nguyên văn cho người dùng.
  - `GET /quiz-assignments/{id}/my-result` của bài không thuộc lớp mình trả 404 thay vì 403.
  - `swagger-ui` và `/v3/api-docs` vẫn công khai ở bản Docker (xem #62).

**60. Thông báo chỉ có cho thanh toán, chưa có thông báo học tập** (thiếu chức năng, chưa làm)
- API thông báo đã có và FE đã dùng (chuông ở header): `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all`.
- `NotificationType` vẫn chỉ có 4 loại về thanh toán. Đã thử lại: sau khi học sinh được thêm vào lớp, được giao bài giảng, được giao bài kiểm tra, nộp bài và được chấm, danh sách thông báo của cả học sinh lẫn giáo viên đều trống.
- Cần backend bổ sung:
  - Học sinh: được thêm vào lớp; lớp có bài giảng mới; có bài kiểm tra mới; bài kiểm tra sắp hết hạn (cần scheduler, chỉ gửi cho người chưa nộp); có điểm; lượt làm được mở lại; buổi live được tạo / sắp bắt đầu / bị huỷ; có người trả lời bình luận.
  - Giáo viên: học sinh tham gia lớp bằng mã; có bài tự luận chờ chấm (`REVIEW_REQUIRED`); render video xong hoặc lỗi; tạo slide / quiz bằng AI xong hoặc lỗi; có bình luận mới ở bài giảng.
- `NotificationResponse` nên có thêm `targetType` (`LECTURE`, `ASSIGNMENT`, `ATTEMPT`, `LIVE_SESSION`, `CLASS`) và `targetId` để FE mở đúng trang khi bấm vào. Hiện chỉ có `title`, `message`, `type` và một `referenceId` dạng chuỗi không kèm loại đối tượng.
- Không có kênh đẩy (WebSocket / SSE / FCM): FE phải gọi lại `unread-count` định kỳ, mobile không nhận được thông báo khi đóng app.

**16b. Thống kê Admin còn vài chỉ số giữ chỗ** (chưa sửa)
- `GET /statistics/overview` vẫn trả `llmCostUsd: 0.0`, `serverUptime: "100%"`, `totalAiGeneratedLectures: 0`, `totalInteractions: 0`.
- `GET /statistics/charts` vẫn trả mảng rỗng cho `userGrowth`, `lectureGrowth`, `weeklyInteractions`. FE Admin tiếp tục tự tính các biểu đồ này từ `/users`, `/classes`, `/enrollments`, `/quizzes`, `/audit-logs`.

## Ghi chú

- Khi build lại backend trên DB đang có dữ liệu: Flyway chạy các migration còn thiếu (V5 chuyển bài `PENDING` chưa có job về `NOT_REQUESTED`; V6 thêm bảng gói; V7 sửa ràng buộc `video_status`). Video đã render (`DONE`) không bị ảnh hưởng. Máy FE đã ở V7 từ 14/10.
- Quiz đã xuất bản **trước** bản sửa #2 vẫn có snapshot thiếu đáp án nên vẫn chấm sai; cần tạo/xuất bản lại quiz đó.
- Đã chạy lại 14/10: tạo bài giảng bằng AI từ file PDF (`gemini-3.8-flash`, có lúc 503 do Gemini quá tải); luồng thanh toán / gói / hoàn tiền bằng IPN VNPay giả lập với khoá thử riêng của môi trường kiểm tra. Chưa chạy: thanh toán thật qua VNPay sandbox (chưa có khoá, xem #69), tạo quiz bằng AI, render video thật với backend mới.
- `GET /lectures` của ADMIN mới lọc theo `title`, chưa lọc theo giáo viên/trạng thái.

