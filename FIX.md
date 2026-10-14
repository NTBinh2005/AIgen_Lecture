# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-13 tại commit `1947e7b` (PR #14). Đã kiểm tra lại bằng API thật: build image backend từ source,
> chạy trên một Postgres trống với tài khoản seed teacher/student/admin. Backend biên dịch và khởi động được,
> 4 migration Flyway chạy thành công trên DB mới. PR #15 trên `origin/dev` chỉ đổi `avatar-ai-service`,
> `video-service` và `start_all.ps1`, không đụng backend Java.
>
> **Đã sửa và xác nhận (đợt 12/10, chạy lại 13/10 vẫn đúng), nên đã xoá khỏi danh sách:** #1 `GET /lectures/student` không có `title` (200),
> #4 `answerVersion` trả đúng sau khi tăng và sai version trả 409, #10 join phòng `SCHEDULED` trả 400 "Phòng học chưa mở",
> #19 quiz AI trả `FAILED` kèm `error` và chạy sau commit, #24 thông báo "Lớp chưa được kích hoạt" (cả ghi danh lẫn
> tự ghi danh), #28 model mặc định `gemini-3.8-flash` (đã gọi thử, hoạt động), #29 mật khẩu được băm BCrypt và
> tài khoản admin tạo đăng nhập được, #31 admin liệt kê được mọi bài giảng, #32 admin xem được bài kiểm tra và tiến độ
> của lớp; phần biên dịch của #27; phần `points` số thực và `GET /quizzes` có câu hỏi của #25; phần số giả của #16b;
> phần chặn giáo viên ngoài lớp của #17b; phần tự đổi `role` và tự `DELETE` của #30.
>
> **Đã sửa từ các đợt trước:** #2, #3, #5, #6, #7, #8, #9, #12, #14, #16, #17, #18, #20, #21, #22, #23, #26.
> Các mục còn lại giữ nguyên số để dễ tham chiếu.

## Lỗi chức năng

**25. `PATCH /classes/{id}` xoá mất mô tả khi request không gửi `description`** (lỗi mới do bản sửa)
- `ClassUpdateRequest.description` đổi thành `Optional<String>`. Khi request **không có** trường `description`, Jackson vẫn tạo `Optional.empty()` chứ không phải `null`, nên `ClassServiceImpl.update` coi như "xoá mô tả".
- Tái hiện: tạo lớp có `description: "mo ta"`, gọi `PATCH /classes/1` với `{"className":"Lop A2"}` → response trả `description: null`.
- Trang sửa lớp của FE luôn gửi kèm `description` nên chưa bị ảnh hưởng; các client khác (mobile, Swagger) sẽ mất dữ liệu.
- Cách sửa: phân biệt "không gửi" với "gửi `null`" bằng `JsonNullable`, hoặc đọc body dạng `JsonNode`/`Map` để kiểm tra trường có mặt hay không.
- Phần cũ vẫn còn: `semester`, `endsAt`, `maxStudents` gửi `null` vẫn bị coi là "không đổi" (đã test, giá trị cũ giữ nguyên).

**27. Bài giảng chưa từng xuất bản vẫn trả `hasUnpublishedChanges: true`**
- Khai báo trùng đã xoá, nhưng `LectureResponse.from()` giữ lại điều kiện thiếu kiểm tra `publishedVersionId`. Bài nháp vừa tạo (`POST /lectures`) trả `hasUnpublishedChanges: true`.
- Cách sửa: thêm `lecture.getPublishedVersionId() != null` vào điều kiện ở `LectureResponse.java:57`.

**33. Scheduler render video bỏ qua cờ `app.video.enabled` và tự render mọi bài `PENDING`** (lỗi mới)
- `LectureVideoRenderCoordinator.dispatchPendingLectures` chạy mỗi 3 giây, lấy mọi bài có `videoStatus = PENDING` và chưa có `videoJobId` rồi gửi sang video-service. Class này không kiểm tra `app.video.enabled` (chỉ `LectureController` và `VideoRenderService` đọc cờ đó), trong khi `Lecture.videoStatus` mặc định là `PENDING`.
- Khi **tắt** video (`APP_VIDEO_ENABLED=false`) mà video-service **vẫn chạy** (mặc định của `docker-compose.yml`, backend còn `depends_on` nó): bài vẫn được render thật. Đã xảy ra ngày 13/10 khi build lại trên DB có sẵn dữ liệu: 7 bài `PENDING` cũ chuyển `PROCESSING` ngay khi backend khởi động, video-service chạy ~590% CPU; phải dừng container video-service để huỷ. Cờ `false` chỉ làm `GET /video-status` và `POST /video-render` trả `NOT_AVAILABLE`, nên FE không thấy tiến độ dù job đang chạy.
- Khi tắt video và video-service **không truy cập được**: bài bị ghi `videoStatus = FAILED` kèm `videoErrorMessage: "Could not submit render job: I/O error on POST ... /generate-video"`. `GET /lectures` trả `FAILED` trong khi `GET /lectures/{id}/video-status` trả `NOT_AVAILABLE`.
- Khi **bật** video: mọi bài giảng (kể cả bản nháp vừa tạo) tự render ngay, không cần giáo viên bấm "Tạo video"; và sau khi nâng cấp, toàn bộ bài `PENDING` cũ trong DB sẽ bị render hàng loạt (10 bài mỗi 3 giây) — tốn tài nguyên với luồng avatar/SadTalker.
- Cách sửa: cho scheduler dừng khi `app.video.enabled=false`; và chỉ dispatch bài đã được yêu cầu render (thêm trạng thái riêng như `QUEUED`, hoặc cờ `videoRequestedAt`) thay vì dùng `PENDING` mặc định. `POST /lectures/{id}/video-render` đặt trạng thái đó.

**34. Quyền của Admin trên bài giảng không nhất quán**
- `GET /lectures` (danh sách) trả `canEdit: false`, `canPublish: false` cho ADMIN ở mọi bài, nhưng `GET /lectures/{id}` của cùng bài trả `true`; thực tế ADMIN `PATCH` và `publish` được (200). Danh sách đang map với `admin = false`.
- Response trả `canArchive: true` cho ADMIN nhưng `DELETE /lectures/{id}` trả 403 (`SecurityConfig` chỉ cho `TEACHER`). ADMIN vì vậy không lưu trữ/xoá được bài giảng nào.
- FE Admin hiện không dựa vào `canEdit`/`canPublish` của danh sách và không hiện nút xoá.

**35. Link video khi lưu tại máy bị viết cứng `localhost`; project Supabase không còn truy cập được** (lỗi mới)
- Với `UPLOAD_TO_SUPABASE=false`, `video-service/src/renderJob.ts:240` trả `videoUrl = http://localhost:${port}/videos/{jobId}.mp4`. Link này chỉ mở được trên chính máy chạy Docker; máy khác trong mạng, bản deploy và emulator mobile (`10.0.2.2`) đều không phát được. Cần biến cấu hình cho địa chỉ công khai (VD `VIDEO_PUBLIC_BASE_URL`).
- Với `UPLOAD_TO_SUPABASE=true`: tên miền `gbhhlacjwkztlbwzjsuo.supabase.co` trong `.env` không còn phân giải được (`ENOTFOUND`, kiểm tra 13/10), nên mọi lần render đều thất bại ở bước upload ("[Supabase] Upload thất bại: fetch failed") sau khi đã render xong. Video cũ lưu trên Supabase (bài 10, 11 ở DB local) cũng không phát được nữa.
- Khi upload thất bại, file MP4 đã render vẫn nằm trong `/app/out` nhưng bài bị đánh dấu `FAILED`; nên fallback sang link nội bộ thay vì bỏ kết quả.

**36. Không chọn được ngôn ngữ bài giảng; giọng đọc luôn là tiếng Việt** (thiếu chức năng)
- Prompt tạo bài giảng trong `LlmServiceImpl.java:76` yêu cầu "Preserve the source language", và `POST /lectures/from-file` chỉ nhận `file`, `title`, `accessScope`. Tài liệu tiếng Anh vì vậy luôn ra slide và lời giảng tiếng Anh (VD bài 12 "Unlocking Human Language"); giáo viên không yêu cầu được bản tiếng Việt.
- `video-service/src/tts/GoogleTtsProvider.ts` viết cứng `lang: 'vi'`, nên lời giảng tiếng Anh bị đọc bằng giọng tiếng Việt.
- Đề xuất: thêm tham số `language` (VD `vi` / `en` / `source`) cho `/lectures/from-file` và `/lectures/generate-from-file`, đưa vào prompt; lưu ngôn ngữ vào lecture và truyền sang video-service để chọn `lang` cho TTS. FE sẽ thêm ô chọn ngôn ngữ ở trang tạo bài giảng.
- `.env.example` khai báo `TTS_PROVIDER` hai lần (`mock` ở dòng 13, `google` ở dòng 24). Ai copy file này mà giữ giá trị `mock` sẽ có video không tiếng — đã gặp ở máy FE (13/10).

## Rà soát toàn bộ API chưa dùng (13/10)

> Đã gọi thử 75 endpoint mà FE chưa dùng, bằng tài khoản teacher/student/admin trên stack Docker mới.

**37. Webhook thanh toán không xác thực — ai cũng tự đánh dấu "đã thanh toán" được** (bảo mật, nghiêm trọng)
- `POST /api/payments/webhook` là `permitAll`, không có chữ ký/token; body chỉ gồm `transactionId`, `status`, `amount`. Cả ba giá trị này người trả tiền đều có sẵn trong response của `POST /payments`.
- Tái hiện: học sinh tạo giao dịch 150.000đ (PENDING), rồi gọi **không cần đăng nhập** `POST /payments/webhook {"transactionId":"…","status":"SUCCESS","amount":150000}` → 200. Giao dịch chuyển `SUCCESS`, hệ thống cấp quyền `PREMIUM`, phát hành hoá đơn và gửi thông báo.
- Cách sửa: bỏ endpoint chung này (đã có IPN riêng cho VNPay/MoMo có kiểm tra chữ ký), hoặc bắt buộc HMAC với secret phía server.

**38. Thanh toán chưa gắn với sản phẩm thật**
- Không có bảng/API sản phẩm hay khoá học: `productId` là số bất kỳ và `amount` do client tự gửi, backend không đối chiếu giá. Học sinh có thể "mua" sản phẩm #1 với giá 1.000đ.
- `LearningAccess` được cấp sau thanh toán không ảnh hưởng tới quyền xem lớp/bài giảng (quyền xem vẫn chỉ dựa vào ghi danh).
- MoMo: `POST /payments` với `paymentMethod: MOMO` trả 400 `resultCode 13 "The merchant configuration is incorrect"` — cấu hình merchant trong backend không hợp lệ. VNPay sandbox tạo link bình thường.

**39. "PDF hoá đơn" là file văn bản**
- `GET /invoices/{id}/pdf` trả `Content-Type: text/plain`, 80 byte: "Invoice PDF Content / Invoice ID / Invoice Number / Amount". Chưa sinh PDF thật.

**40. Admin không quản lý được thanh toán; không có luồng duyệt hoàn tiền**
- Chỉ có API "của tôi" (`/payments/my-payments`, `/invoices`, `/refunds/my-refunds`). ADMIN gọi các API này nhận mảng rỗng, và `GET /payments/{id}`, `GET /refunds/{id}` của người khác trả 400 "Access denied".
- Yêu cầu hoàn tiền tạo xong nằm mãi ở `REQUESTED`: không có endpoint duyệt/từ chối, các trạng thái `APPROVED`, `REJECTED`, `COMPLETED` không được dùng ở đâu trong code.
- Lỗi không đúng quyền trả 400 thay vì 403.
- `GET /api/access/check-user?userId=&productId=` là `permitAll`: không cần đăng nhập vẫn tra được quyền học của bất kỳ user nào.

**41. Tài nguyên, ghi hình và người tham gia của buổi live chưa dùng được thật**
- `POST /live-sessions/{id}/resources` nhận `resourceId` kiểu `Long`, trong khi bản trình chiếu và asset dùng `UUID`, nên không thể trỏ tới tài nguyên thật. Backend cũng không kiểm tra tồn tại: `PRESENTATION #1` và `ASSET #1` (không có) đều trả 201. Học sinh thấy tên tài nguyên nhưng không có link để mở/tải.
- `POST /live-sessions/{id}/recording/request` tạo bản ghi `REQUESTED` rồi đứng yên (chưa nối nhà cung cấp họp trực tuyến); `GET /live-sessions/{id}/participants` luôn rỗng vì dữ liệu chỉ đến từ webhook của nhà cung cấp.

**42. Điểm AI gợi ý cho câu tự luận không trả về**
- `POST /attempts/{id}/answers/{answerId}/ai-suggest` trả 202 và ghi cột `ai_suggested_score`, nhưng `AttemptAnswerResponse` không có trường này nên giáo viên không xem được gợi ý. Lần test ghi giá trị `0` cho một bài luận có nội dung.

**43. Xuất kết quả quiz**
- `GET /exports` là stub, luôn trả `[]` (comment trong code: "Simplified: return an empty list") dù đã có job `COMPLETED`.
- Cả 3 loại `GRADEBOOK_XLSX`, `STUDENT_RESULT_XLSX`, `STUDENT_RESULT_PDF` sinh cùng một file XLSX 3.298 byte; loại `STUDENT_RESULT_PDF` cũng trả `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Comment trong `ExportRequest` ghi `QUIZ_TEMPLATE, QUIZ_RESULTS, CLASS_SCORES`, không khớp enum.

**44. Tạo slide bằng AI âm thầm dùng nội dung mẫu khi Gemini lỗi**
- Khi Gemini trả 503 hết 3 lần thử, `LlmServiceImpl` không báo lỗi mà sinh bộ slide khuôn mẫu ("Lesson opener: …", "Welcome. Before we begin…", "Pause and think"), job vẫn `DONE`. Giáo viên nhận slide chung chung mà không biết AI đã lỗi. Đã thấy khi tạo bản trình chiếu từ bài giảng (job mất khoảng 2,5 phút).
- Bản mẫu chọn ngôn ngữ bằng cách dò ký tự có dấu, nên nội dung tiếng Việt không dấu bị coi là tiếng Anh.
- `generationParameters` (presentation) và `parameters` (generation job) trả ra thuộc tính nội bộ của `JsonNode` (`{"array":false,"bigDecimal":false,…}`) thay vì dữ liệu JSON.

**45. Thiếu dữ liệu để FE hiển thị**
- Cộng tác viên (`GET /lectures/{id}/collaborators`, `GET /presentations/{id}/collaborators`) chỉ trả mảng `userId`, và TEACHER không có API tìm giáo viên → FE phải bắt nhập ID và chỉ hiện "Giáo viên #114".
- `GET /students/me/quiz-history` không có tên quiz/lớp (chỉ `assignmentId`); FE phải ghép từ danh sách bài kiểm tra của các lớp.
- Bài giảng không có API khôi phục phiên bản cũ (bản trình chiếu thì có).
- Đăng xuất chỉ thu hồi refresh token; access token cũ vẫn dùng được tới khi hết hạn (24 giờ).

**46. Thời gian lưu không kèm múi giờ — bài kiểm tra giao theo giờ Việt Nam bị ẩn 7 tiếng** (lỗi mới, ảnh hưởng người dùng thật)
- Backend ép `TimeZone.setDefault(UTC)` (`DemoApplication.java:10`) và dùng `LocalDateTime` (không có múi giờ) cho `openAt`, `closeAt`, `startsAt`, `endsAt`, `deadlineAt`, `createdAt`… rồi so với `LocalDateTime.now()` (giờ UTC). API không quy ước các trường này là UTC hay giờ địa phương.
- Đã xảy ra ngày 13/10: giáo viên giao bài cho "Lớp demo" với giờ mở `20:33` (giờ VN). `QuizAssignmentServiceImpl.getStudentAssignments` bỏ qua bài có `openAt` sau `now()` = 13:37 UTC, nên học sinh không thấy bài cho tới 20:33 UTC (03:33 sáng hôm sau giờ VN). Hạn nộp, đồng hồ đếm ngược của lượt làm bài và giờ buổi live lệch theo cùng cách.
- FE đã đổi sang quy ước: mọi `LocalDateTime` của backend là UTC — đổi giờ địa phương sang UTC khi gửi và đổi ngược lại khi hiển thị (`frontend/src/lib/format.ts`). Mobile và các client khác cần làm giống vậy.
- Dữ liệu nhập **trước** khi FE sửa (lớp, lịch, buổi live, bài kiểm tra đã giao) đang lưu giờ VN như thể là UTC, nên giờ hiển thị sẽ lệch +7 tiếng; bài #6 của Lớp demo vẫn bị ẩn tới 03:33.
- Không có API sửa bài đã giao (`PATCH /quiz-assignments/{id}`), nên giáo viên không tự chỉnh được giờ mở/hạn nộp — chỉ có thể giao bài mới.
- Cách sửa gốc: dùng `Instant`/`OffsetDateTime` (JSON có `Z`) cho các trường thời gian, hoặc ghi rõ quy ước UTC trong API; thêm API sửa/xoá bài đã giao.

**47. Chưa có chức năng gói đăng ký (subscription)** (thiếu chức năng)
- Backend không có entity, bảng hay API nào về gói/bảng giá/sản phẩm/khoá học: thư mục `entity` chỉ có `Payment`, `Invoice`, `Refund`, `LearningAccess`; không controller nào trả danh sách gói. FE vì vậy không dựng được trang chọn gói và đăng ký.
- `POST /payments` nhận `productId` là số bất kỳ và `amount` do client gửi (xem #38), nên không có gì ràng buộc "gói nào – giá bao nhiêu – thời hạn bao lâu".
- Thanh toán thành công chỉ tạo `LearningAccess` với `accessType = "PREMIUM"` viết cứng (`LearningAccessEventListener.java:55`) và `expiresAt = null`. Không có gia hạn, hết hạn, huỷ gói hay nâng/hạ cấp.
- Quyền `PREMIUM` không được kiểm tra ở đâu: xem bài giảng, làm bài kiểm tra, tạo bài giảng bằng AI… đều không phụ thuộc vào gói. `GET /access/check` trả kết quả nhưng không tính năng nào dùng tới.
- `POST /api/internal/events/payment-succeeded` (nhận `classId`, `studentId`, ghi danh học sinh vào lớp sau khi trả tiền) có sẵn nhưng luồng thanh toán không gọi tới, nên trả tiền xong học sinh cũng không được vào lớp.
- Cần backend bổ sung:
  - Bảng + API gói: `GET /plans` (tên, giá, chu kỳ, quyền lợi, dành cho STUDENT/TEACHER), và API quản trị gói cho ADMIN.
  - `POST /payments` nhận `planId`, tự lấy giá từ gói thay vì tin `amount` của client.
  - Bảng subscription theo người dùng: gói hiện tại, ngày bắt đầu/hết hạn, trạng thái; API `GET /subscriptions/me`, huỷ/gia hạn.
  - Áp quyền của gói vào tính năng thật (VD giới hạn số bài giảng AI, số lớp, quyền xem video).
- Hiện FE chỉ có trang "Thanh toán" của học sinh (nhập tay mã sản phẩm và số tiền) để gọi được các API thanh toán sẵn có.

## Rà soát toàn hệ thống (13/10, đợt 2)

Cách kiểm tra: gọi ~90 endpoint GET và ~180 thao tác ghi bằng 6 danh tính (chưa đăng nhập, học sinh trong lớp, học sinh ngoài lớp, giáo viên chủ lớp, giáo viên khác, admin); mở toàn bộ trang web của 3 vai trò bằng Chrome (desktop 1366px và mobile 390px). Các mục dưới đây đều đã tái hiện được trên bản backend đang chạy.

**48. Ai cũng tự đăng ký được tài khoản ADMIN** (NGHIÊM TRỌNG)
- `POST /api/auth/register` (không cần đăng nhập) nhận `role` từ client và lưu thẳng (`AuthController.java:59`). Gửi `"role": "ADMIN"` → 200, token trả về gọi được `GET /users`, `GET /statistics/overview`.
- `POST /auth/google` và `POST /auth/sms/verify` cũng lấy `role` từ request khi tạo tài khoản mới (`AuthServiceImpl.resolveRole`, dòng 319) nên bị lỗi tương tự.
- Cần: chỉ cho tự đăng ký `STUDENT` (và `TEACHER` nếu sản phẩm cho phép); tài khoản `ADMIN` chỉ tạo qua `POST /users` của admin.

**49. Giáo viên bất kỳ chấm, chốt điểm và mở lại bài làm của lớp khác** (NGHIÊM TRỌNG)
- `POST /attempts/{id}/grade`, `/finalize`, `/reopen` chỉ kiểm tra vai trò TEACHER/ADMIN, không kiểm tra giáo viên có phụ trách lớp hay không. Trong `GradingServiceImpl.confirmGrade` (dòng 129) còn nguyên ghi chú `// Verify teacher ownership...` nhưng chưa có code. Giáo viên `gvphu@test.com` (không liên quan lớp) gọi cả ba đều nhận 204.
- `POST /quiz-assignments/{id}/preview` cũng không kiểm tra: giáo viên khác xem được toàn bộ câu hỏi kèm lời giải.
- `grade` nhận `questionScores` với mã câu hỏi không tồn tại và điểm vượt điểm tối đa của câu (gửi `{"99999": 1000}` → 204), không chặn điểm âm.
- `reopen` không đặt lại `deadlineAt`: bài đã quá hạn từ 03/10 sau khi mở lại vẫn nộp được ngày 13/10.
- Ngược lại, giáo viên phụ và admin lại bị 403 ở các API xem bài (xem #17b) — quyền đọc chặt hơn quyền ghi.

**50. Học sinh không thuộc lớp vẫn bắt đầu làm được bài kiểm tra** (NGHIÊM TRỌNG)
- `POST /quiz-assignments/{id}/start`: `AttemptServiceImpl.startAttempt` (dòng 47) không kiểm tra học sinh có ghi danh ACTIVE trong lớp của bài hay không. `student@edumind.dev` (không ở lớp nào) gọi với bài của lớp 1 → 200, nhận đủ đề bài và một lượt làm được tạo.
- Chỉ cần đoán `assignmentId` (số tăng dần) là lấy được đề của mọi lớp.
- `GET /quiz-assignments/student/class/{id}` thì đã chặn đúng (403).

**51. Giáo viên dùng được bài giảng và quiz của giáo viên khác**
- `POST /classes/{id}/lectures`: gán được bài giảng đã xuất bản của người khác vào lớp mình (bài 3 của `teacher@edumind.dev` gán vào lớp của `fixcheck…` → 201). Học sinh lớp đó xem được nội dung.
- `POST /quiz-assignments`: giao được `quizVersionId` của giáo viên khác cho lớp mình (201). Chỉ kiểm tra quyền trên lớp, không kiểm tra quyền trên quiz.
- `POST /quizzes/ai-generate`: sinh quiz từ `sourceLectureId` của giáo viên khác (202) — lấy được nội dung bài giảng qua câu hỏi sinh ra và tốn quota Gemini.
- `POST /exports`: tạo và tải được file xuất cho `assignmentId`/`classId` của lớp khác (202 → `COMPLETED` → tải 200). Hiện file chỉ là dữ liệu mẫu (xem #43) nên chưa lộ điểm thật, nhưng khi #43 được sửa thì sẽ lộ. Request thiếu cả `assignmentId` lẫn `classId`, hoặc id không tồn tại, vẫn được nhận và "hoàn thành".

**52. Nhiều trường hợp trả 500 thay vì 4xx**
- Đường dẫn không tồn tại (`GET /api/nonexistent`) và sai method (`GET /api/live-sessions`, `GET /api/refunds`) → 500. `GlobalExceptionHandler` chưa xử lý `NoResourceFoundException` và `HttpRequestMethodNotSupportedException` nên rơi vào nhánh `Exception`. Cần trả 404/405.
- Dùng `orElseThrow()` không tham số (ném `NoSuchElementException` → 500) khi id không tồn tại:
  - `GET /quiz-assignments/99999/progress` (`QuizAssignmentServiceImpl.java:157`)
  - `POST /attempts/99999/grade`, `/finalize` (`GradingServiceImpl.java:129`)
  - `POST /attempts/{id}/answers/99999/ai-suggest` (`GradingServiceImpl.java:99`)
  - `POST /admin/quiz-migrations/import-ai-elements/99999` (`MigrationServiceImpl.java:27`)
  - Các chỗ tương tự chưa gọi thử: `QuizServiceImpl.java:112, 234, 243, 252`, `ExportServiceImpl.java:55, 132`.
- `POST /auth/logout` với refresh token không hợp lệ → 500 (`/auth/refresh` cùng dữ liệu trả 400 đúng).
- `POST /classes/self-enroll` → 500 khi có hai lớp trùng mã (xem #54).

**53. Tài khoản và phiên đăng nhập**
- Email phân biệt hoa thường: đăng ký `STUDENT@edumind.dev` thành công dù đã có `student@edumind.dev` → hai tài khoản cùng một địa chỉ. Cần chuẩn hoá về chữ thường khi lưu và khi tìm.
- Không giới hạn số lần đăng nhập sai: 20 lần sai liên tiếp đều trả 401 ngay, không khoá tạm hay làm chậm.
- Tài khoản bị khoá/xoá (`INACTIVE`) không đăng nhập mới được, nhưng access token đã cấp vẫn dùng tiếp tới khi hết hạn: `JwtAuthenticationFilter.java:45` không kiểm tra `status` và `UserPrincipal.isEnabled()` luôn trả `true`.
- Ghi danh được học sinh đã `INACTIVE` vào lớp (`POST /classes/{id}/students` → 201).
- `POST /users` (admin) nhận mật khẩu 1 ký tự, trong khi `/auth/register` yêu cầu tối thiểu 8. Tên trường là `passwordHash` nhưng thực chất nhận mật khẩu thô.
- Đăng ký với tên dài hơn 100 ký tự → 409 "Database constraint violation" thay vì 400 có tên trường (thiếu `@Size` ở `RegisterRequest.name`).

**54. Thiếu kiểm tra dữ liệu đầu vào**
- Trùng mã lớp: chỉ kiểm tra theo cặp (mã, học kỳ) nên tạo được lớp thứ hai cùng `classCode` nếu khác học kỳ (201). Trong khi `self-enroll` tìm lớp bằng `findByClassCode` trả `Optional` → hai kết quả → 500, **học sinh không vào được cả hai lớp**. Mã lớp cần là duy nhất toàn hệ thống.
- Quiz: tạo được câu hỏi điểm âm (`points: -5`), câu trắc nghiệm không có lựa chọn nào, đáp án đúng không nằm trong các lựa chọn. (FE đã tự chặn, nhưng API thì không.)
- Giao bài: `closeAt` ở quá khứ vẫn được nhận (201) → bài sinh ra đã đóng ngay.
- Buổi live: tạo được buổi ở quá khứ (năm 2020); `title` dài hơn giới hạn cột → 409 "Database constraint violation" thay vì 400.
- Lịch học: `timezone` và `recurrence` nhận chuỗi bất kỳ (`"Sao/Hoa"`).
- `POST /attempts/{id}/signals`: nhận body rỗng hoặc chuỗi 5.000 ký tự đều 204, mỗi lần gọi tăng bộ đếm rời tab.
- `GET /enrollments?status=KHONGCO`: bỏ qua bộ lọc sai và trả toàn bộ, thay vì 400.
- `GET /users` không phân trang (bỏ qua `page`, `size`); `GET /users/students?email=a` cho mọi giáo viên liệt kê gần hết học sinh chỉ với 1 ký tự.

**55. `meetingUrl` của buổi live không được kiểm tra**
- `POST /live-sessions` lưu nguyên `meetingUrl: "javascript:alert(1)"`. Client mở link này sẽ chạy mã trong trình duyệt của học sinh.
- FE web đã chặn: chỉ mở link bắt đầu bằng `http://` hoặc `https://` (`LiveSessionCard.tsx`). Backend cần kiểm tra để mobile và client khác không dính.

**56. Học sinh và Admin không xoá được bình luận**
- `SecurityConfig.java:121` đặt `DELETE /api/lectures/**` chỉ cho `TEACHER`. Vì vậy `DELETE /lectures/{id}/comments/{commentId}` trả 403 cho chính học sinh viết bình luận và cho admin, dù mô tả API ghi "chủ sở hữu hoặc ADMIN". Nút xoá bình luận trên trang xem bài giảng của học sinh luôn thất bại.
- Giáo viên chủ bài giảng cũng không xoá được bình luận của học sinh trong bài mình (400 "Bạn không có quyền xóa bình luận này") — không ai kiểm duyệt được bình luận.
- Cùng quy tắc này khiến admin không xoá được bài giảng và cộng tác viên (liên quan #34).

**57. Buổi live không tự kết thúc**
- Không có scheduler nào cho buổi live. Buổi #1 (lịch 06/10) vẫn ở trạng thái `LIVE` đến 13/10; trang học sinh vẫn hiện "Đang diễn ra" và nút "Vào lớp".
- Cần tự chuyển `ENDED` khi quá `endsAt` (hoặc sau một khoảng chờ).

**58. Định dạng lỗi không thống nhất**
- 403 từ tầng lọc bảo mật không có trường `message` (`{"status":403,"error":"Forbidden","path":…}`), còn 403 từ service thì có → FE phải tự đặt câu thông báo.
- Thông báo lỗi lẫn lộn tiếng Việt có dấu, tiếng Việt không dấu và tiếng Anh (`"Email hoac password khong dung"`, `"Lecture not found: 99999"`, `"Bạn không có quyền…"`), và FE đang hiển thị nguyên văn cho người dùng.
- Sai quyền trả lẫn lộn 400/403/404 tuỳ API (`/payments/{id}` của người khác → 400; `/quiz-assignments/{id}/my-result` → 404).
- `POST /auth/verify` với token sai trả 200.
- `swagger-ui` và `/v3/api-docs` mở công khai — nên tắt hoặc khoá ở môi trường production.



**59. Việc còn lại phía FE web** (việc của FE, ghi để theo dõi)
- `pages/student/WatchLecturePage.tsx:571`: `timeSpent` gửi lên `POST /interactions` luôn bằng 0 vì thời điểm bắt đầu được lấy ngay trước lúc gửi.
- ESLint còn 11 lỗi ở 3 file cũ: `LoginModal.tsx` (2 `any`, 1 setState trong effect), `LectureStatusBadge.tsx`, `WatchLecturePage.tsx` (8). `tsc` và `vite build` đều sạch.
- `pages/admin/AiSettingsPage.tsx` không còn được dùng (route đã chuyển hướng) — có thể xoá.
- `store/authStore.ts:52`: giá trị mặc định của `API_BASE_URL` vẫn là cổng `8080`; chạy dev thiếu `.env.local` sẽ gọi sai cổng.
- Trang làm bài `/student/quizzes/{id}` với id không tồn tại hoặc của lớp khác vẫn hiện màn hình "Bắt đầu" (không có API lấy thông tin một bài giao cho học sinh; phụ thuộc #50).
- Đã sửa trong đợt này: lỗi 4xx không còn bị thử lại 3 lần (trước đây trang "không tìm thấy" mất ~7 giây mới hiện); chặn `meetingUrl` không phải http(s).
- Kết quả mở trang: 48 trang × 2 kích thước màn hình đều tải được, không có lỗi console, không tràn ngang, không hiện `undefined`/`NaN`.


**60. Thông báo chỉ có cho thanh toán, chưa có thông báo học tập** (thiếu chức năng)
- API thông báo đã có và FE đã dùng (chuông ở header): `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all`.
- Nhưng `NotificationType` chỉ có 4 loại, đều về thanh toán: `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `REFUND_STATUS`, `LEARNING_ACCESS_GRANTED`. `createNotification` chỉ được gọi ở `NotificationEventListener`, `PaymentServiceImpl` và `RefundServiceImpl`. Trong DB hiện chỉ có 3 thông báo, đều sinh ra từ luồng thanh toán thử.
- Không sự kiện học tập nào tạo thông báo. Cần backend bổ sung:
  - Học sinh: được thêm vào lớp; lớp có bài giảng mới (`POST /classes/{id}/lectures`); có bài kiểm tra mới (`POST /quiz-assignments`); bài kiểm tra sắp hết hạn (cần scheduler, VD trước `closeAt` 24 giờ và 1 giờ, chỉ gửi cho người chưa nộp); có điểm (chấm tự động xong, giáo viên `finalize`); lượt làm được mở lại; buổi live được tạo / sắp bắt đầu / bị huỷ; có người trả lời bình luận.
  - Giáo viên: học sinh tham gia lớp bằng mã; có bài tự luận chờ chấm (`REVIEW_REQUIRED`); render video xong hoặc lỗi; tạo slide / quiz bằng AI xong hoặc lỗi; có bình luận mới ở bài giảng.
- `NotificationResponse` nên có thêm trường để FE mở đúng trang khi bấm vào thông báo, VD `targetType` (`LECTURE`, `ASSIGNMENT`, `ATTEMPT`, `LIVE_SESSION`, `CLASS`) và `targetId`. Hiện chỉ có `title`, `message`, `type` và một `referenceId` dạng chuỗi không kèm loại đối tượng.
- Không có kênh đẩy (WebSocket / SSE / FCM): FE phải gọi lại `unread-count` định kỳ, mobile không nhận được thông báo khi đóng app.

## Phân quyền

**17b. Giáo viên phụ và Admin bị 403 khi xem bài làm**
- `AttemptServiceImpl.fetchAttempt` giờ chỉ cho giáo viên chính của lớp hoặc chủ quiz. Giáo viên phụ (co-teacher) bị 403 ở `GET /attempts/{id}`.
- `GET /quiz-assignments/{id}/progress` gọi lại `fetchAttempt` cho từng lượt làm, nên giáo viên phụ xem được danh sách bài kiểm tra của lớp nhưng nhận 403 ở tiến độ ngay khi có học sinh làm bài.
- `GET /attempts/{id}` có `@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")` nên ADMIN bị 403, dù `/progress` trả cùng dữ liệu đó cho ADMIN.
- Cách sửa: trong `fetchAttempt` dùng `classAccessService.canManage(...)` (đã gồm giáo viên phụ và ADMIN) thay cho so sánh với giáo viên chính; thêm `ADMIN` vào `@PreAuthorize` và truyền `isTeacher = true` cho ADMIN.

**30. Admin vẫn tự khoá được chính mình qua `PATCH`**
- Đã chặn tự đổi `role` và `DELETE /users/{id}` của chính mình (trả 400), và giữ admin cuối cùng.
- Còn lỗi: `PATCH /users/{id}` với `id` của chính admin và `{"status":"INACTIVE"}` trả 200. Sau đó admin không đăng nhập lại được ("Tai khoan da bi vo hieu hoa"); nếu đó là admin duy nhất thì phải sửa trực tiếp trong DB.
- Cách sửa: trong `UserServiceImpl.update`, áp dụng cùng kiểm tra (không phải chính mình, không phải admin hoạt động cuối cùng) khi `status` đổi khỏi `ACTIVE`.

**16b. Thống kê Admin còn vài chỉ số giữ chỗ**
- Số giả đã bỏ; `totalInteractions` và `roleDistribution` lấy từ DB.
- `GET /statistics/overview` vẫn trả `llmCostUsd: 0.0`, `serverUptime: "100%"`, `totalAiGeneratedLectures: 0` viết cứng.
- `GET /statistics/charts` trả mảng rỗng cho `userGrowth`, `lectureGrowth`, `weeklyInteractions`. FE Admin tiếp tục tự tính các biểu đồ này từ `/users`, `/classes`, `/enrollments`, `/quizzes`, `/audit-logs`.

## Cấu hình

**13. Cổng Postgres còn lệch ở compose của backend**
- `application.properties` đã mặc định `5436`, khớp `docker-compose.yml` gốc và `README.md`.
- `backend/docker-compose.yml` và `DOCKER_FILES.md` vẫn publish `5435`. Chạy backend local với compose này phải tự đặt `DB_PORT=5435`.

**15. Một file mobile vẫn gọi cổng 8080** (việc của FE mobile)
- `auth_service.dart` và `lecture_video_screen.dart` đã đổi sang `8081`. `mobile/lib/services/lecture_service.dart:7` vẫn ghi cố định `10.0.2.2:8080`.

**61. Wav2Lip được commit thiếu `.gitmodules` và thiếu hướng dẫn tải model** (PR #15/#16, chỉ ảnh hưởng chạy local)
- `avatar-ai-service/wav2lip_src` (và `sadtalker_src` từ trước) được commit dưới dạng submodule nhưng repo không có file `.gitmodules`: clone/pull về thư mục trống, `git submodule status` báo `no submodule mapping found`.
- `start_all.ps1` ép `AVATAR_ENGINE=wav2lip`, nên trên máy không có sẵn mã nguồn Wav2Lip service avatar báo "Wav2Lip was requested but is not ready" và script không chạy được. Hiện chỉ máy của người commit chạy được.
- Hai file model `wav2lip_gan.pth` và `s3fd.pth` là bắt buộc nhưng `download_checkpoints.ps1`, `README.md` và `requirements*.txt` chưa nhắc tới Wav2Lip.
- Cần: thêm `.gitmodules` (hoặc bỏ submodule và ghi lệnh clone trong README), bổ sung bước tải model, và để `start_all.ps1` rơi về engine dự phòng khi thiếu Wav2Lip.
- **Bản Docker không bị ảnh hưởng** (đã kiểm tra 13/10 trên commit `08f7b99`): build hai image `video-service` và `avatar-ai-service` từ code mới thành công; với cấu hình mặc định của compose (`AVATAR_ENGINE=fallback`, `AVATAR_PROVIDER=2d-viseme`) render thử một video 2 slide ra file MP4 16 giây có hình, phụ đề và giọng đọc.

## Ghi chú

- Quiz đã xuất bản **trước** bản sửa #2 vẫn có snapshot thiếu đáp án nên vẫn chấm sai; cần tạo/xuất bản lại quiz đó.
- #19/#28: đã chạy lại luồng **tạo quiz bằng AI** từ đầu đến cuối với `gemini-3.8-flash` (13/10): job chuyển `PROCESSING` → `DONE` sau ~30 giây, sinh 5 câu hỏi. Luồng **tạo bài giảng từ file** (`POST /lectures/from-file`) và render video thật chưa được chạy lại trong đợt này.
- #31: `GET /lectures` của ADMIN mới lọc theo `title`, chưa lọc theo giáo viên/trạng thái.
- Khi build lại backend trên DB đang có dữ liệu: Flyway sẽ chạy 4 migration (baseline 0), và nếu bật video thì xem #33 trước.
