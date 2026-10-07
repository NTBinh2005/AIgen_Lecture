# Các lỗi backend trên nhánh `dev`

> Cập nhật 2026-10-06 sau PR #10, #11, #12 (commit `03ec3f7`). Đã kiểm tra bằng API thật trên một bản build tạm
> (bỏ dòng khai báo trùng ở mục #27 để biên dịch được), tài khoản teacher/student thật.
>
> **Đã sửa và xác nhận, nên đã xoá khỏi danh sách:** #2 chấm quiz, #3 `questionId` trong body, #5 lịch sử làm bài,
> #6 quiz gắn bài giảng, #7 render video (đã ra `DONE` kèm link Supabase), #8 quyền comment/quiz/video-status,
> #9 trả 401 khi chưa đăng nhập, #12 truyền `APP_ENFORCE_SECRETS` qua compose, #14 (thay bằng #28),
> #16 `@EnableMethodSecurity`, #17 tên học sinh + câu trả lời khi chấm, #18 trả 409, #20 `quizTitle`,
> #21 kiểm tra `closeAt > openAt`, #22 cộng tác viên chỉ TEACHER, #23 tìm học sinh theo email,
> #26 chặn giao bài `PRIVATE` + cờ `hasUnpublishedChanges`, phần `GET /presentations` của #24.
> Các mục còn lại giữ nguyên số để dễ tham chiếu.

## Nghiêm trọng

**27. Backend trên `dev` không biên dịch được** (chặn build/deploy)
- `dto/response/LectureResponse.java` khai báo `private boolean hasUnpublishedChanges;` **hai lần** (dòng 32 và 40) — PR #11 và PR #12 cùng thêm trường này rồi merge chồng lên nhau.
- `./mvnw compile` báo `variable hasUnpublishedChanges is already defined`, kéo theo hàng loạt lỗi Lombok phía sau; `docker compose up --build` cũng build thất bại.
- Hàm `from()` còn gọi `setHasUnpublishedChanges(...)` hai lần với hai điều kiện khác nhau; lần sau ghi đè lần trước nên bài **chưa từng xuất bản** cũng trả `hasUnpublishedChanges: true` (đã thấy ở bài nháp mới tạo).
- Cách sửa: xoá một dòng khai báo và giữ điều kiện có `getPublishedVersionId() != null`.

**28. Model Gemini mặc định đã bị Google ngừng — mọi tính năng AI đều lỗi**
- PR #12 đổi mặc định cả `application.properties` lẫn `docker-compose.yml` sang `gemini-2.0-flash`. Gemini trả `404 "This model models/gemini-2.0-flash is no longer available"`.
- Ảnh hưởng: tạo bài giảng từ file (job FAILED) và tạo quiz bằng AI (#19) đều không chạy.
- Cách sửa: đổi `GEMINI_API_URL` mặc định sang model còn hoạt động (Google gợi ý `gemini-3.8-flash`), hoặc đặt `GEMINI_API_URL` trong `.env`.

**1. Danh sách bài giảng của học sinh lỗi 500 khi không lọc theo tên**
- Phần quyền xem đã sửa: học sinh xem được bài đã giao (`GET /lectures/9` → 200), bài không được giao vẫn 403.
- Còn lỗi: `GET /lectures/student` **không có** `title` trả 500 `function lower(bytea) does not exist` — cùng lỗi đã sửa ở `/presentations`. Có `title` thì chạy bình thường.
- Vị trí: `LectureRepository.findPublishedForStudent`, điều kiện `(:title IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :title, '%')))`. Cách sửa giống `PresentationRepository` (tách query hoặc truyền chuỗi rỗng thay vì `null`).
- FE đang lấy bài giảng qua `GET /classes/{id}/lectures` nên chưa bị ảnh hưởng.

## Lỗi chức năng

**4. `answerVersion` trả về sai và lỗi khoá lạc quan trả 500**
- Version giờ đã tăng sau mỗi lần sửa, nhưng response của `PUT /attempts/{id}/answers/{questionId}` trả version **trước khi tăng** (DB đã là 2, response vẫn báo 1). FE dùng số này cho lần lưu sau sẽ bị từ chối.
- Gửi sai version → `ObjectOptimisticLockingFailureException` không có handler nên trả **500** thay vì 409.
- Cách sửa: `saveAndFlush` rồi mới đọc version để trả về; thêm handler 409 cho `ObjectOptimisticLockingFailureException`.
- FE hiện tự tính version (+1 sau mỗi lần sửa) và thử lại một lần khi gặp 409/500.

**19. Tạo quiz bằng AI không bao giờ báo lỗi**
- Đã gọi Gemini thật và dùng `quizId` làm `jobId`. Nhưng khi AI lỗi, `QuizServiceImpl` chỉ ghi log; `GET /quizzes/ai-jobs/{id}` trả `PROCESSING` mãi (đã thấy khi Gemini trả 404 ở #28). Cần lưu trạng thái `FAILED` (kèm lý do) để FE dừng chờ.
- `CompletableFuture.runAsync` chạy ngay trong transaction chưa commit của `createQuizDraft`, nên luồng nền có thể không thấy quiz vừa tạo. Nên kích hoạt sau commit (`@TransactionalEventListener(AFTER_COMMIT)`) như luồng video.
- FE tự dừng chờ sau 2 phút và cho mở quiz nháp.

**10. Vào phòng live khi buổi học chưa mở báo sai lý do**
- (Phần quiz bài không tồn tại đã trả 404.) `POST /live-sessions/{id}/join` khi buổi còn `SCHEDULED` vẫn trả 403 "Bạn không có quyền thực hiện hành động này", dễ hiểu nhầm là không thuộc lớp. Nên trả 409/400 "Phòng học chưa mở".

**24. Thông báo sai khi ghi danh vào lớp DRAFT**
- Ghi danh vào lớp `DRAFT` vẫn báo "Lớp đã đóng, không thể ghi danh học viên mới (CLASS-BR-04)" (`EnrollmentServiceImpl:119`). Nên báo "Lớp chưa được kích hoạt".

**25. Không xoá được trường tuỳ chọn của lớp; điểm câu hỏi bị cắt về số nguyên; danh sách quiz không có câu hỏi**
- `PATCH /classes/{id}` coi `null` là "không đổi": gửi `{"description": null}` thì mô tả cũ vẫn giữ nguyên.
- `QuestionDto.points` vẫn là `Integer`: `points: 0.5` trả 202 nhưng lưu thành `0` mà không báo lỗi (điểm chấm lại là `Double`).
- `GET /quizzes` (danh sách) vẫn trả `questions: []` cho mọi quiz.

## Phân quyền

**17b. Giáo viên bất kỳ xem được bài làm của mọi học sinh**
- `GET /attempts/{id}` cho mọi tài khoản TEACHER (`isTeacher = true`) mà không kiểm tra giáo viên có phụ trách lớp của bài kiểm tra đó không.

**16b. Thống kê Admin trả số cố định**
- Quyền đã sửa (chỉ ADMIN). Nhưng `StatisticsOverviewResponse` vẫn trả `totalInteractions: 24680`, `llmCostUsd: 142.5`, `serverUptime: "99.9%"` là số viết cứng.
- `GET /statistics/charts` trả **toàn bộ** số giả (VD 1160 học sinh, 98 giáo viên trong khi DB có 4 và 2). FE Admin hiện tự tính biểu đồ từ `/users`, `/classes`, `/enrollments`, `/quizzes`, `/audit-logs`.

## Luồng Admin (kiểm tra 2026-10-06)

**29. Admin tạo người dùng / đặt lại mật khẩu thì mật khẩu được lưu nguyên văn** (bảo mật, nghiêm trọng)
- `POST /users` và `PATCH /users/{id}` nhận trường `passwordHash` và `UserServiceImpl` lưu thẳng vào DB, **không băm BCrypt**. Đã test: tạo user mật khẩu `Password123!` → cột `password_hash` chứa đúng `Password123!`, và user đó đăng nhập bị 401.
- Hậu quả: mật khẩu lộ dạng chữ thường trong DB, và tài khoản do admin tạo không dùng được.
- Cách sửa: đổi tên trường thành `password`, gọi `passwordEncoder.encode(...)` ở cả create lẫn update.

**30. Admin có thể tự hạ quyền / tự khoá chính mình**
- `PATCH /users/{id}` với `id` của chính admin và `{"role":"STUDENT"}` trả 200. Ngay sau đó token mất quyền admin nên không tự đổi lại được — đã xảy ra khi test, phải sửa trực tiếp trong DB. Tương tự với `DELETE /users/{id}` (khoá).
- Cách sửa: chặn sửa `role`/`status` và khoá với `userId` của chính người gọi, và không cho hạ/khoá admin cuối cùng. (FE đã khoá các ô này cho tài khoản của chính mình.)

**31. Admin không liệt kê được toàn bộ bài giảng**
- `GET /lectures` với ADMIN chỉ trả bài giảng do chính admin sở hữu (`findOwnedOrShared`) → trả rỗng. Không có API nào để admin xem/duyệt mọi bài giảng (vẫn mở được từng bài qua `GET /lectures/{id}`).
- Đề xuất: khi `admin = true` thì trả toàn bộ (có lọc theo giáo viên/trạng thái).

**32. Admin bị 403 khi xem bài kiểm tra của lớp**
- `GET /quiz-assignments/teacher/class/{classId}` và `GET /quiz-assignments/{id}/progress` có `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")` nhưng `QuizAssignmentServiceImpl` vẫn yêu cầu người gọi là giáo viên sở hữu quiz ("Only the owner teacher can view assignment progress") → ADMIN nhận 403. Tab "Bài kiểm tra" trong trang quản lý lớp của Admin vì vậy báo lỗi.

## Cấu hình

**13. Cổng Postgres mặc định không khớp compose gốc**
- `application.properties` mặc định `DB_PORT=5435` (khớp `backend/docker-compose.yml`, `DOCKER_FILES.md`), nhưng `docker-compose.yml` gốc và `README.md` dùng `5436`. Cần thống nhất một cổng.

**15. App mobile vẫn gọi cổng 8080** (việc của FE mobile)
- Backend publish ở `8081:8080`, nhưng mobile ghi cố định `10.0.2.2:8080` ở `mobile/lib/services/auth_service.dart:7`, `mobile/lib/services/lecture_service.dart:7`, `mobile/lib/screens/student/lecture_video_screen.dart:11`.

## Ghi chú

- Quiz đã xuất bản **trước** bản sửa #2 vẫn có snapshot thiếu đáp án nên vẫn chấm sai; cần tạo/xuất bản lại quiz đó.
