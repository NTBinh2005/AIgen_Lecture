# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-03. Kiểm tra trên Docker (backend `8081`) bằng tài khoản teacher và student thật.
> Đã bỏ khỏi danh sách các mục đã sửa trong commit `905c41d`: retry Gemini, DEFAULT cho cột NOT NULL, API sửa slide, lỗi 400 thay vì 500, log job, API key trong header. Mục #11 (crash khi thiếu `GOOGLE_OAUTH_CLIENT_ID`) cũng đã sửa nên được xóa; các mục còn lại giữ nguyên số để dễ tham chiếu.
>
> **Cập nhật:** nhóm lỗi Auth / User / Authorization đã được sửa trong đợt này — các mục **#1, #8, #9, #12, #16, #22, #23, #26** được đánh dấu `✅ ĐÃ SỬA`. Mục #17 do đồng đội sửa trước đó.

## Nghiêm trọng: chặn luồng chính của học sinh

**1. ✅ ĐÃ SỬA — Học sinh không xem được bài giảng nào, kể cả bài đã được giao cho lớp**
- Vị trí: `DenyAllLectureAccessGrantVerifier.canReadPublishedLecture` luôn trả `false`. `LectureServiceImpl.assertStudentGrant` dùng nó cho `GET /lectures/{id}` và `GET /lectures/{id}/versions/{versionId}`.
- Tái hiện: teacher publish bài 6 (`CLASS`), giao vào lớp 1 (`POST /classes/1/lectures`), học sinh tự đăng ký vào lớp 1 (enrollment `ACTIVE`). Học sinh gọi `GET /lectures/6` thì vẫn nhận 403.
- Phần lớp học / ghi danh / giao bài (Backend 2) đã chạy được, chỉ còn thiếu bộ kiểm tra quyền thật: học sinh có enrollment `ACTIVE` trong một lớp đã được giao bài giảng đó.
- Cùng nguyên nhân: `getAllLecturesForStudent` (`LectureServiceImpl:111`) vẫn trả `Page.empty()`, nên `GET /lectures/student` luôn rỗng. FE tạm lấy danh sách bài giảng từ `GET /classes/{id}/lectures`.

**2. Chấm quiz sai, và nộp bài lỗi 500 khi có câu tự luận ngắn**
- Vị trí: `QuizServiceImpl.mapQuestionToDto` không copy `correctAnswer`. Khi publish, `questionsSnapshot` chỉ chứa `"correctAnswer": null`. `GradingServiceImpl` lại chấm theo snapshot này.
- Hậu quả:
    - `MCQ_SINGLE` / `TRUE_FALSE`: luôn bị chấm sai (so sánh với `null`).
    - `SHORT_ANSWER`: `q.getCorrectAnswer().trim()` gây `NullPointerException`, nên `POST /attempts/{id}/submit` trả 500.
- Tái hiện: tạo quiz có câu `SHORT_ANSWER` (đáp án `Hà Nội`), publish, giao cho lớp, học sinh trả lời đúng rồi nộp → 500. Quiz chỉ có trắc nghiệm thì nộp được nhưng 0 điểm.
- `GET /quizzes/{id}` của teacher cũng trả `correctAnswer: null` (cùng hàm map).

**3. Lưu đáp án báo 400 nếu body không có `questionId`**
- Vị trí: `AttemptController.submitAnswer` (`PUT /attempts/{id}/answers/{questionId}`). `AttemptAnswerSubmitRequest.questionId` có `@NotNull`, mà `@Valid` chạy trước khi controller gán `questionId` lấy từ path.
- Hiện tượng: gửi `{"response":"B","answerVersion":0}` thì nhận 400 `questionId: QuestionId is required`, đáp án không được lưu. Nếu học sinh nộp luôn thì bị chấm 0 điểm.
- FE hiện đang gửi `questionId` cả trong body để tránh lỗi. Cách sửa: bỏ `@NotNull` ở DTO, hoặc không validate trường này.

## Lỗi chức năng

**4. `answerVersion` không bao giờ tăng**
- Sau mỗi lần lưu, `GET /attempts/{id}` vẫn trả `answerVersion: 0`, và gửi lại `answerVersion: 0` vẫn được chấp nhận. Optimistic lock trong `AttemptServiceImpl.submitAnswer` vì vậy không có tác dụng.

**5. Lịch sử làm bài của học sinh luôn rỗng**
- `GET /students/me/quiz-history` trả `[]`, dù học sinh đã có lượt làm `GRADED` (`GET /quiz-assignments/1/my-result` vẫn trả đúng lượt đó).

**6. Không có API lưu `videoUrl` hay quiz gắn với bài giảng**
- Bảng `ai_elements` không được ghi ở đâu cả, nên `GET /api/lectures/{id}/quizzes` (`InteractionServiceImpl`) luôn trả rỗng, và `POST /api/interactions` không có câu hỏi nào để trả lời.
- `LectureCreateRequest.quizzes` vẫn còn trong DTO nhưng bị bỏ qua mà không báo lỗi.

**7. Luồng render video chưa được nối lại**
- `/video-status` đã trả `NOT_AVAILABLE` khi `app.video.enabled=false`, nhưng backend vẫn không gọi `video-service` (`POST /generate-video`) ở đâu, chưa có job polling. Cần quyết định video render ở bước nào (ví dụ khi publish).

## Phân quyền và xử lý lỗi

**8. ✅ ĐÃ SỬA — API comment, quiz và video-status không kiểm tra quyền xem bài giảng**
- Học sinh đọc và **đăng được bình luận** vào bài giảng mình không có quyền xem: bài 1 (`FAILED`, `PRIVATE`) và bài 2 (`DRAFT`) đều trả 201.
- `GET /lectures/{id}/quizzes` và `GET /lectures/{id}/video-status` của các bài này cũng trả 200.
- Cần áp dụng cùng điều kiện quyền như `GET /lectures/{id}` (sau khi sửa mục 1).

**9. ✅ ĐÃ SỬA — Gọi khi chưa đăng nhập trả 500 thay vì 401**
- `SecurityConfig` để `permitAll` cho `GET /api/lectures/*/video-status` và `POST /api/lectures/generate-from-file`, nhưng controller lại gọi `requirePrincipal` và ném `AuthenticationCredentialsNotFoundException`. `GlobalExceptionHandler` không có handler cho lỗi này nên trả 500.
- Cách sửa: bỏ `permitAll` cho hai route này, hoặc thêm handler trả 401.

**10. Mã lỗi không nhất quán**
- `GET /lectures/9999/quizzes` (bài không tồn tại) trả 200 `[]` thay vì 404.
- `POST /live-sessions/{id}/join` khi buổi học còn `SCHEDULED` trả 403 "Bạn không có quyền...", dễ hiểu nhầm là không thuộc lớp. Nên trả 409/400 kèm thông báo "Phòng học chưa mở".

## Cấu hình và bảo mật

**12. ✅ ĐÃ SỬA (một phần) — Secret mặc định vẫn được dùng khi deploy bằng compose**
- `SecretsValidator` chỉ chặn khi `APP_ENFORCE_SECRETS=true`, mà cờ này mặc định `false` và `docker-compose.yml` không bật. Compose còn tự điền giá trị mặc định cho `JWT_SECRET` và `LIVE_WEBHOOK_SECRET`, nên app chỉ ghi cảnh báo `[BẢO MẬT]` rồi vẫn chạy.
- Cần đặt `APP_ENFORCE_SECRETS=true` và secret thật trong môi trường deploy.
- **Đã làm (giữ chạy out-of-the-box):** `docker-compose.yml` gốc nhận biến `APP_ENFORCE_SECRETS` (mặc định `false`), và `.env.example` đã có hướng dẫn. **Việc cần làm khi deploy thật:** đặt `APP_ENFORCE_SECRETS=true` + cấp `JWT_SECRET`, `LIVE_WEBHOOK_SECRET` thật trong `.env` — khi đó app sẽ từ chối khởi động nếu secret còn để mặc định.

**13. Cổng Postgres mặc định không khớp compose gốc**
- `application.properties` mặc định `DB_PORT=5435` (khớp `backend/docker-compose.yml`), nhưng `docker-compose.yml` ở thư mục gốc và `README.md` dùng `5436`. Chạy backend local với compose gốc thì phải tự đặt `DB_PORT=5436`. Cần thống nhất một cổng.

**14. Model Gemini mặc định khác nhau giữa local và Docker**
- `application.properties` dùng `gemini-2.0-flash`, còn `docker-compose.yml` dùng `gemini-3.5-flash`, nên kết quả AI có thể khác nhau giữa hai môi trường.

**15. App mobile vẫn gọi cổng 8080** (việc của FE mobile)
- Backend publish ở `8081:8080`, nhưng mobile ghi cố định `10.0.2.2:8080` ở `mobile/lib/services/auth_service.dart:7`, `mobile/lib/services/lecture_service.dart:7` và `mobile/lib/screens/student/lecture_video_screen.dart:11`.

## Luồng Teacher (kiểm tra 2026-10-03)

**16. ✅ ĐÃ SỬA — `@PreAuthorize` không có tác dụng — bất kỳ ai đăng nhập cũng xem được thống kê của Admin** (bảo mật, nghiêm trọng)
- Không có `@EnableMethodSecurity` ở đâu trong code, nên mọi `@PreAuthorize("hasRole(...)")` trong 9 controller đều bị bỏ qua. Phân quyền hiện chỉ dựa vào `requestMatchers` trong `SecurityConfig`.
- Hậu quả thấy được: `/api/statistics/**` không có luật URL riêng, nên **học sinh và giáo viên** gọi `GET /statistics/overview` và `GET /statistics/charts` đều nhận 200.
- Cách sửa: thêm `@EnableMethodSecurity` vào `SecurityConfig`, sau đó test lại toàn bộ API (một số luồng có thể đang chạy được nhờ annotation bị bỏ qua).
- Ngoài ra `StatisticsOverviewResponse` trả số cố định (`totalInteractions: 24680`, `llmCostUsd: 142.5`, `serverUptime: "99.9%"`).

**17. Giáo viên không xem được bài làm của học sinh nên không chấm được bài tự luận**
- `GET /quiz-assignments/{id}/progress` trả danh sách `AttemptResponse` với `answers: null` và **không có `studentId` / tên học sinh**, nên không biết lượt làm nào của ai.
- `GET /attempts/{id}`: `AttemptController.fetchAttempt` luôn truyền `isTeacher=false` vào `attemptService.fetchAttempt(...)`, nên giáo viên gọi thì nhận 403.
- Hệ quả: `POST /attempts/{id}/grade` chạy được (đã test, điểm cuối = tổng `questionScores`), nhưng giáo viên phải chấm mà không đọc được câu trả lời, và cũng không lấy được `answerId` để gọi `ai-suggest`.
- Cần: thêm `studentId`, `studentName` vào `AttemptResponse` của progress; cho giáo viên của lớp gọi `GET /attempts/{id}` (kèm `answers`).

**18. Sửa quiz đã publish trả 500**
- `PATCH /quizzes/{id}` khi quiz đã `PUBLISHED` ném `IllegalStateException("Can only update quiz in DRAFT or REVIEWED status")`, rơi vào handler chung nên trả 500. Nên trả 409 kèm message.

**19. Tạo quiz bằng AI chỉ là code giả**
- `POST /quizzes/ai-generate` trả `jobId` ngẫu nhiên và chỉ tạo một quiz DRAFT không có câu hỏi; `GET /quizzes/ai-jobs/{jobId}` luôn trả `PROCESSING`, `questionCount: 0` với bất kỳ `jobId` nào (kể cả `abc`). FE không thể hoàn thành luồng QUIZ-02.

**20. Danh sách bài kiểm tra của giáo viên thiếu tên quiz**
- `GET /quiz-assignments/teacher/class/{classId}` chỉ trả `quizVersionId`, không có `quizId` / `quizTitle` (khác với API của học sinh có `quizTitle`). FE phải tải toàn bộ quiz rồi tự map theo version.

**21. Giao bài kiểm tra không kiểm tra thời gian**
- `POST /quiz-assignments` chấp nhận `openAt = 2026-12-01`, `closeAt = 2026-11-01` (đóng trước khi mở) và trả 201.

**22. ✅ ĐÃ SỬA — Cộng tác viên bài giảng chấp nhận cả học sinh**
- `POST /lectures/{id}/collaborators` với `userId` của một STUDENT vẫn trả 204. Học sinh này sau đó sẽ có quyền sửa bài giảng. Nên chỉ cho phép TEACHER.

**23. ✅ ĐÃ SỬA — Giáo viên không tìm được học sinh để ghi danh**
- `POST /classes/{id}/students` và `/students/bulk` cần `studentId`, nhưng `/api/users/**` chỉ dành cho ADMIN và không có API tìm học sinh theo email. Giáo viên chỉ có thể dùng mã lớp (self-enroll) hoặc phải biết ID.
- Đề xuất: API tìm học sinh theo email (`GET /users/students?email=`) cho TEACHER, hoặc cho `EnrollmentRequest` nhận `email`.

**24. Thông báo lỗi sai khi ghi danh vào lớp DRAFT; `GET /presentations` lỗi 500**
- Ghi danh vào lớp đang `DRAFT` báo "Lớp đã đóng, không thể ghi danh học viên mới (CLASS-BR-04)". Nên báo "Lớp chưa được kích hoạt".
- `GET /presentations` trả 500: `function lower(bytea) does not exist` (query lọc theo title, tham số `null` bị bind thành `bytea`).

**25. Không xoá được trường tuỳ chọn của lớp; điểm câu hỏi bị cắt về số nguyên**
- `PATCH /classes/{id}` coi `null` là "không đổi": gửi `{"description": null}` thì mô tả cũ vẫn giữ nguyên. Giáo viên không xoá được `description`, `semester`, `endsAt`, `maxStudents` đã đặt.
- `QuestionDto.points` là `Integer`: tạo câu hỏi `points: 0.5` vẫn trả 202 nhưng lưu thành `0` mà không báo lỗi. Trong khi điểm chấm (`questionScores`, `finalScore`) lại là `Double`. Nên đổi `points` sang số thực hoặc trả 400.
- `GET /quizzes` (danh sách) luôn trả `questions: []` nên FE không hiện được số câu của từng quiz.

**26. ✅ ĐÃ SỬA — Giao được bài giảng `PRIVATE` cho lớp, và trạng thái không phản ánh bản sửa chưa xuất bản**
- `POST /classes/{id}/lectures` chỉ kiểm tra `PUBLISHED`, không kiểm tra `accessScope`. Bài `PRIVATE` vẫn giao được (201), nhưng `assertStudentGrant` yêu cầu `CLASS`, nên học sinh sẽ không bao giờ xem được bài đó. Nên trả 400 "Bài giảng phải ở phạm vi Lớp học", hoặc tự đổi sang `CLASS` khi giao.
- Sửa một bài đã xuất bản (`PATCH`) tạo version nháp mới, nhưng `status` của lecture vẫn là `PUBLISHED` và response không có cờ nào cho biết có bản nháp. FE phải tự so `currentVersionId !== publishedVersionId`. Đề xuất thêm `hasUnpublishedChanges` vào `LectureResponse`.
