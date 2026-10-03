# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-03. Kiểm tra trên Docker (backend `8081`) bằng tài khoản teacher và student thật.
> Đã bỏ khỏi danh sách các mục đã sửa trong commit `905c41d`: retry Gemini, DEFAULT cho cột NOT NULL, API sửa slide, lỗi 400 thay vì 500, log job, API key trong header. Mục #11 (crash khi thiếu `GOOGLE_OAUTH_CLIENT_ID`) cũng đã sửa nên được xóa; các mục còn lại giữ nguyên số để dễ tham chiếu.

## Nghiêm trọng: chặn luồng chính của học sinh

**1. Học sinh không xem được bài giảng nào, kể cả bài đã được giao cho lớp**
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

**8. API comment, quiz và video-status không kiểm tra quyền xem bài giảng**
- Học sinh đọc và **đăng được bình luận** vào bài giảng mình không có quyền xem: bài 1 (`FAILED`, `PRIVATE`) và bài 2 (`DRAFT`) đều trả 201.
- `GET /lectures/{id}/quizzes` và `GET /lectures/{id}/video-status` của các bài này cũng trả 200.
- Cần áp dụng cùng điều kiện quyền như `GET /lectures/{id}` (sau khi sửa mục 1).

**9. Gọi khi chưa đăng nhập trả 500 thay vì 401**
- `SecurityConfig` để `permitAll` cho `GET /api/lectures/*/video-status` và `POST /api/lectures/generate-from-file`, nhưng controller lại gọi `requirePrincipal` và ném `AuthenticationCredentialsNotFoundException`. `GlobalExceptionHandler` không có handler cho lỗi này nên trả 500.
- Cách sửa: bỏ `permitAll` cho hai route này, hoặc thêm handler trả 401.

**10. Mã lỗi không nhất quán**
- `GET /lectures/9999/quizzes` (bài không tồn tại) trả 200 `[]` thay vì 404.
- `POST /live-sessions/{id}/join` khi buổi học còn `SCHEDULED` trả 403 "Bạn không có quyền...", dễ hiểu nhầm là không thuộc lớp. Nên trả 409/400 kèm thông báo "Phòng học chưa mở".

## Cấu hình và bảo mật

**12. Secret mặc định vẫn được dùng khi deploy bằng compose**
- `SecretsValidator` chỉ chặn khi `APP_ENFORCE_SECRETS=true`, mà cờ này mặc định `false` và `docker-compose.yml` không bật. Compose còn tự điền giá trị mặc định cho `JWT_SECRET` và `LIVE_WEBHOOK_SECRET`, nên app chỉ ghi cảnh báo `[BẢO MẬT]` rồi vẫn chạy.
- Cần đặt `APP_ENFORCE_SECRETS=true` và secret thật trong môi trường deploy.

**13. Cổng Postgres mặc định không khớp compose gốc**
- `application.properties` mặc định `DB_PORT=5435` (khớp `backend/docker-compose.yml`), nhưng `docker-compose.yml` ở thư mục gốc và `README.md` dùng `5436`. Chạy backend local với compose gốc thì phải tự đặt `DB_PORT=5436`. Cần thống nhất một cổng.

**14. Model Gemini mặc định khác nhau giữa local và Docker**
- `application.properties` dùng `gemini-2.0-flash`, còn `docker-compose.yml` dùng `gemini-3.5-flash`, nên kết quả AI có thể khác nhau giữa hai môi trường.

**15. App mobile vẫn gọi cổng 8080** (việc của FE mobile)
- Backend publish ở `8081:8080`, nhưng mobile ghi cố định `10.0.2.2:8080` ở `mobile/lib/services/auth_service.dart:7`, `mobile/lib/services/lecture_service.dart:7` và `mobile/lib/screens/student/lecture_video_screen.dart:11`.
