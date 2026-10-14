# Quiz API (Backend 4)
**Version:** 1.1.0
**Date:** 2026-09-20
**Owner:** Backend 4 (Quiz Module)

## Global Conventions

- **Auth:** Mọi request (trừ Auth) phải gửi kèm header:
```
Authorization: Bearer <token>
```

- **Error response shape:** FE sẽ hiển thị trường `message` cho người dùng.
```json
{
  "timestamp": "2023-11-20T08:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Bạn không có quyền truy cập",
  "path": "/api/..."
}
```

**Quiz-specific Error Codes:**

| Error Code | HTTP Status | Ý nghĩa |
|---|---|---|
| `QUIZ_NOT_FOUND` | 404 | Quiz hoặc Assignment không tồn tại |
| `QUIZ_PUBLISH_VALIDATION` | 400 | Quiz chưa có câu hỏi hoặc trạng thái không hợp lệ để publish |
| `ANSWER_VERSION_CONFLICT` | 409 | Client gửi answerVersion cũ hơn server (optimistic lock) |
| `ATTEMPT_ALREADY_SUBMITTED` | 400 | Attempt đã nộp, không thể thao tác |
| `MAX_ATTEMPTS_REACHED` | 400 | Học sinh đã hết lượt làm bài |
| `ASSIGNMENT_NOT_OPEN` | 400 | Bài tập chưa mở hoặc đã đóng |
| `TEMPLATE_INVALID` | 400 | File template XLSX không hợp lệ (export validate) |

- **Pagination:** Sử dụng query params `?page=0&size=20&sort=createdAt,desc`. Response trả về kèm `pagination` meta:
```json
{
  "pagination": {
    "pageNum": 0,
    "pageSize": 20,
    "totalItems": 100,
    "totalPages": 5
  }
}
```

- **Time format:** `yyyy-MM-dd'T'HH:mm:ss`, múi giờ của server.

---

## 1. QuizController (Quản lý ngân hàng đề và phiên bản)
Controller này là trung tâm điều khiển việc tạo, sửa, xóa và xuất bản Quiz (ngân hàng đề) của giáo viên. Mọi endpoint yêu cầu quyền TEACHER hoặc ADMIN.

---

**GET /api/quizzes**: Lấy danh sách Quiz của giáo viên. Có hỗ trợ phân trang và lọc (`title`, `status`, `sourceType`).

input:
(query parameters: `?page=0&size=20&title=Toan&status=DRAFT`)

output:
```json
{
  "success": true,
  "message": "Thành công",
  "data": [
    {
      "quizId": 1,
      "title": "Bài kiểm tra Toán",
      "sourceType": "MANUAL", // MANUAL, AI
      "status": "DRAFT", // DRAFT, REVIEWED, PUBLISHED, CLOSED, ARCHIVED
      "createdAt": "2023-11-20T08:00:00"
    }
  ],
  "pagination": {
    "pageNum": 0,
    "pageSize": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

---

**POST /api/quizzes**: Tạo một Quiz Draft mới (thủ công). Yêu cầu quyền Giáo viên hoặc Admin.

input:
```json
{
  "title": "Quiz Mới",
  "sourceType": "MANUAL", // Nếu là AI thì dùng /ai-generate thay vì endpoint này
  "questions": [
    {
      "questionType": "MCQ_SINGLE", // MCQ_SINGLE, TRUE_FALSE, SHORT_ANSWER, ESSAY
      "questionText": "1 + 1 = ?",
      "options": ["1", "2", "3"],
      "correctAnswer": "2",
      "points": 1,
      "explanation": "Toán cơ bản"
    }
  ]
}
```

output (201 CREATED):
```json
{
  "quizId": 2,
  "title": "Quiz Mới",
  "sourceType": "MANUAL",
  "status": "DRAFT",
  "questions": [ /* ... */ ]
}
```

---

**PATCH /api/quizzes/{id}**: Cập nhật Quiz đang ở trạng thái DRAFT hoặc REVIEWED. Yêu cầu quyền Giáo viên hoặc Admin.

input:
```json
{
  "title": "Quiz Cập Nhật",
  "questions": [ /* ... mảng câu hỏi mới đè lên câu hỏi cũ */ ]
}
```

output: Trả về chi tiết Quiz (200 OK).

---

**GET /api/quizzes/{id}**: Lấy chi tiết Quiz (kèm danh sách câu hỏi). Teacher chỉ xem được quiz của mình; Admin xem tất cả.

output: Trả về chi tiết Quiz (200 OK).

---

**POST /api/quizzes/ai-generate**: Bắt đầu tiến trình tạo Quiz bằng AI từ bài giảng (Async). Yêu cầu quyền Giáo viên hoặc Admin.

input:
```json
{
  "title": "Quiz AI",
  "sourceType": "AI",
  "sourceLectureId": 10
}
```

output (202 ACCEPTED):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED" // QUEUED, PROCESSING, DONE, FAILED
}
```

⚠️ FE phải poll endpoint bên dưới để biết khi nào hoàn thành.

---

**GET /api/quizzes/ai-jobs/{jobId}**: Poll trạng thái tiến trình tạo Quiz AI.

output (ví dụ đang xử lý):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "questionCount": 3 // Số câu hỏi đã generate được (cập nhật liên tục)
}
```

output (ví dụ hoàn thành):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DONE",
  "questionCount": 10,
  "quizId": 15 // ID của Quiz Draft đã được tạo, FE redirect đến trang edit quiz này
}
```

output (ví dụ lỗi):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "FAILED",
  "errorMessage": "LLM timeout after 5 minutes"
}
```

⚠️ FE sử dụng response này để hiển thị thanh tiến trình. Poll mỗi 3-5 giây.

---

**POST /api/quizzes/{id}/publish**: Xuất bản một phiên bản Quiz mới từ Draft hiện tại. Phải có ít nhất 1 câu hỏi, status phải là DRAFT hoặc REVIEWED.

output (200 OK):
```json
{
  "versionId": 1,
  "quizId": 1,
  "versionNo": 1,
  "publishedAt": "2023-11-20T08:05:00",
  "publishedBy": 10, // teacherId
  "questionsSnapshot": "[...]" // Chuỗi JSON chứa snapshot câu hỏi (immutable)
}
```

---

**GET /api/quizzes/{id}/versions**: Lấy lịch sử các phiên bản đã xuất bản của một Quiz.

output (200 OK):
```json
[
  {
    "versionId": 1,
    "versionNo": 1,
    "publishedAt": "2023-11-20T08:05:00"
  },
  {
    "versionId": 2,
    "versionNo": 2,
    "publishedAt": "2023-11-25T10:00:00"
  }
]
```

---

**PATCH /api/quizzes/{id}/close**: Đóng Quiz (không nhận thêm attempt nào mới). 200 OK.

**PATCH /api/quizzes/{id}/archive**: Lưu trữ Quiz. 200 OK.

---

## 2. QuizAssignmentController (Quản lý giao bài)
Controller quản lý việc gán một Quiz Version vào một Class cụ thể, thiết lập thời gian, số lần làm bài, và quy tắc hiển thị kết quả.

---

**POST /api/quiz-assignments**: Giao bài (chỉ dành cho Teacher/Admin).

input:
```json
{
  "quizVersionId": 1,
  "classId": 5,
  "openAt": "2023-11-20T08:00:00", // Có thể null (mở ngay)
  "closeAt": "2023-11-21T08:00:00", // Có thể null (không giới hạn)
  "durationMinutes": 45, // Thời gian làm bài tính bằng phút
  "maxAttempts": 1, // 0 = unlimited
  "resultPolicy": "AFTER_SUBMISSION", // AFTER_SUBMISSION, AFTER_GRADING, AFTER_CLOSE_AT, NEVER
  "shuffleSeedBase": 12345 // Seed cho việc trộn thứ tự câu hỏi
}
```

output (201 CREATED):
```json
{
  "assignmentId": 1,
  "quizVersionId": 1,
  "classId": 5,
  "openAt": "2023-11-20T08:00:00",
  "closeAt": "2023-11-21T08:00:00",
  "durationMinutes": 45,
  "maxAttempts": 1,
  "resultPolicy": "AFTER_SUBMISSION",
  "status": "OPEN" // DRAFT, OPEN, CLOSED
}
```

---

**GET /api/quiz-assignments/teacher/class/{classId}**: Teacher xem danh sách bài tập đã giao cho lớp.

output (200 OK): Mảng `QuizAssignmentResponse`.

---

**GET /api/quiz-assignments/student/class/{classId}**: Student xem danh sách bài tập của mình trong lớp.

input: query param `?status=NOT_STARTED` (tuỳ chọn).
⚠️ Hỗ trợ filter trạng thái: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `OVERDUE`. Nếu không truyền `status`, trả về tất cả.

output (200 OK):
```json
[
  {
    "assignmentId": 1,
    "quizTitle": "Toán giữa kỳ",
    "classId": 5,
    "openAt": "2023-11-20T08:00:00",
    "closeAt": "2023-11-21T08:00:00",
    "durationMinutes": 45,
    "maxAttempts": 1,
    "usedAttempts": 0,
    "status": "OPEN",
    "studentStatus": "TODO" // TODO, IN_PROGRESS, COMPLETED, OVERDUE
  }
]
```

---

**GET /api/quiz-assignments/{id}/progress**: Teacher xem tiến độ làm bài của học sinh (ai đã nộp, điểm bao nhiêu).

output (200 OK):
```json
[
  {
    "attemptId": 1,
    "assignmentId": 1,
    "studentId": 20,
    "studentName": "Nguyễn Văn A",
    "attemptNo": 1,
    "status": "SUBMITTED",
    "finalScore": 8.0,
    "submittedAt": "2023-11-20T09:00:00"
  }
]
```

---

**POST /api/quiz-assignments/{id}/preview**: Teacher xem trước bài làm (làm thử, không lưu vào lịch sử).
⚠️ Preview trả về attempt với answers included (có correctAnswer), tạo mô phỏng y như học sinh nhưng không tạo record trong DB.

output: Trả về `AttemptStartResponse` (200 OK).

---

**POST /api/quiz-assignments/{id}/start**: Học sinh bắt đầu làm bài.

output (200 OK):
```json
{
  "attemptId": 1,
  "assignmentId": 1,
  "startedAt": "2023-11-20T08:30:00",
  "deadlineAt": "2023-11-20T09:15:00", // = startedAt + durationMinutes
  "questions": [
    {
      "questionId": 10,
      "questionType": "MCQ_SINGLE",
      "questionText": "1 + 1 = ?",
      "options": ["1", "2", "3"],
      "points": 1
      // correctAnswer và explanation KHÔNG trả về cho student khi đang làm bài
    }
  ],
  "savedAnswers": {
    // Map questionId → response (dùng khi student refresh lại trang)
  }
}
```

⚠️ FE lưu ý:
- `deadlineAt` do server tính (`startedAt` + `durationMinutes`). **FE chỉ đếm ngược từ mốc này, không tự tính lại.**
- Mảng `questions` đã được shuffle cho attempt này. **FE hiển thị đúng thứ tự server trả về, không sort lại.**
- `savedAnswers` map là để phục hồi khi refresh — FE dùng nó populate lại UI.

---

**GET /api/quiz-assignments/{id}/my-result**: Học sinh xem kết quả bài tập sau khi hoàn thành. Dữ liệu trả về phụ thuộc vào `resultPolicy` (xem Phụ lục).

---

## 3. AttemptController (Làm bài và nộp bài)
Controller phục vụ học sinh trong quá trình làm bài: lưu đáp án từng câu, nộp toàn bài, ghi nhận tín hiệu gian lận, và xem lại lịch sử.

---

**GET /api/attempts/{id}**: Lấy trạng thái làm bài hiện tại (dùng khi bị rớt mạng và vào lại). Trả về danh sách câu hỏi + savedAnswers.

output (200 OK): Trả về `AttemptResponse`.

---

**PUT /api/attempts/{id}/answers/{questionId}**: Lưu/thay thế đáp án cho 1 câu hỏi (idempotent autosave). Đây là PUT chứ không phải POST — mỗi lần gọi thay thế hoàn toàn đáp án trước đó cho questionId đó.

input:
```json
{
  "response": "2", // Đáp án của học sinh
  "answerVersion": 0 // Phiên bản hiện tại (optimistic locking)
}
```

output: 204 No Content (thành công).

⚠️ Chú ý quan trọng cho FE:
- `answerVersion` bắt đầu từ 0.
- Nếu server trả về **409 `ANSWER_VERSION_CONFLICT`**, nghĩa là một request trước đó đã cập nhật answer này rồi (ví dụ do network delay gửi 2 lần). FE phải **GET lại attempt** (`GET /api/attempts/{id}`) để lấy version mới nhất, rồi retry.
- Khi lưu thành công, server tự tăng `answerVersion` lên 1.

---

**POST /api/attempts/{id}/signals**: Ghi nhận tín hiệu gian lận (chuyển tab, mất focus). Rate-limited (1 signal/giây). Trả về 204 No Content.

---

**POST /api/attempts/{id}/submit**: Nộp toàn bài. **Idempotent** — an toàn khi bấm nhiều lần (double-tap safe).

output (200 OK):
```json
{
  "attemptId": 1,
  "status": "SUBMITTED", // SUBMITTED hoặc AUTO_SUBMITTED (nếu server tự nộp)
  "objectiveScore": 8.0, // Điểm các câu trắc nghiệm (tự chấm)
  "finalScore": 8.0 // null nếu có tự luận chưa chấm
}
```

⚠️ FE lưu ý:
- Sau `deadlineAt`, server sẽ tự nộp (trạng thái `AUTO_SUBMITTED`). FE chỉ cần lock UI khi countdown đến 0.
- Nếu gọi submit khi đã submitted → trả về cùng response (không lỗi).

---

**POST /api/attempts/{id}/reopen**: (Teacher/Admin) Mở lại bài làm cho học sinh. Hành động này được ghi lại trong Audit Log.

output: 204 No Content.

---

**GET /api/students/me/quiz-history**: Học sinh xem lịch sử tất cả bài tập đã làm.

output (200 OK):
```json
[
  {
    "attemptId": 1,
    "assignmentId": 1,
    "attemptNo": 1,
    "status": "GRADED",
    "finalScore": 8.0,
    "submittedAt": "2023-11-20T09:00:00"
  }
]
```

---

## 4. GradingController (Chấm điểm)
Dành cho giáo viên chấm điểm thủ công (các câu tự luận) và trigger AI hỗ trợ chấm. Mọi endpoint yêu cầu quyền TEACHER hoặc ADMIN.

---

**POST /api/attempts/{id}/grade**: Teacher chốt điểm cho từng câu hỏi trong 1 bài làm.

input:
```json
{
  "questionScores": {
    "10": 2.0, // questionId: score
    "11": 1.5
  }
}
```

output: 204 No Content.

---

**POST /api/attempts/{id}/answers/{answerId}/ai-suggest**: Trigger AI gợi ý điểm cho câu tự luận. Async (202 Accepted).

output: 202 Accepted (không có body).

⚠️ Tính năng này chỉ lưu `aiSuggestedScore` ngầm vào `AttemptAnswer`. **Điểm thực tế của học sinh chỉ thay đổi khi giáo viên xác nhận qua `/grade` rồi `/finalize`.**

---

**POST /api/attempts/{id}/finalize**: Chốt điểm toàn bài (trạng thái chuyển thành `GRADED`). Chỉ gọi sau khi tất cả câu tự luận đã được chấm.

output: 204 No Content.

---

## 5. ExportController (Xuất file và Validate)
Hỗ trợ xuất báo cáo điểm (GRADEBOOK_XLSX) và upload template quiz. Mọi endpoint yêu cầu quyền TEACHER hoặc ADMIN.

---

**POST /api/exports**: Yêu cầu tạo báo cáo (Async).

input:
```json
{
  "exportType": "GRADEBOOK_XLSX", // GRADEBOOK_XLSX (bắt buộc theo QUIZ-08)
  "entityId": 5 // classId hoặc assignmentId tùy exportType
}
```

output (202 ACCEPTED):
```json
{
  "jobId": 10,
  "status": "QUEUED" // QUEUED, PROCESSING, DONE, FAILED
}
```

---

**GET /api/exports**: Lấy danh sách lịch sử các file export của giáo viên.

output (200 OK):
```json
[
  {
    "jobId": 10,
    "exportType": "GRADEBOOK_XLSX",
    "status": "DONE",
    "createdAt": "2023-11-20T10:00:00",
    "downloadUrl": "/api/exports/10/download",
    "expiresAt": "2023-11-27T10:00:00"
  }
]
```

---

**GET /api/exports/{id}**: Poll trạng thái export.

output (ví dụ đang xử lý):
```json
{
  "jobId": 10,
  "status": "PROCESSING",
  "progress": 45 // phần trăm (0-100)
}
```

output (ví dụ hoàn thành):
```json
{
  "jobId": 10,
  "status": "DONE",
  "downloadUrl": "/api/exports/10/download",
  "expiresAt": "2023-11-27T10:00:00"
}
```

output (ví dụ lỗi template):
```json
{
  "jobId": 10,
  "status": "FAILED",
  "errorReport": "TEMPLATE_INVALID: Cột B thiếu header 'Họ tên'"
}
```

⚠️ FE poll endpoint này mỗi 3-5 giây cho đến khi status = DONE hoặc FAILED.

---

**GET /api/exports/{id}/download**: Tải file về (trả về binary XLSX). Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

---

**POST /api/exports/validate-template**: Upload file XLSX để validate tính hợp lệ của template. Dùng `multipart/form-data` với key là `file`.

output: 204 No Content (nếu hợp lệ). 400 TEMPLATE_INVALID (nếu không hợp lệ, body chứa `errorReport`).

---

## 6. MigrationController (Admin)
Hỗ trợ Admin import dữ liệu mini-quiz cũ (từ AI Elements của bài giảng) sang Quiz module mới.

---

**POST /api/admin/quiz-migrations/import-ai-elements/{lectureId}**: Chạy import. **Idempotent**: nếu đã import bài giảng này rồi, sẽ không tạo bản sao.

output (200 OK):
```json
{
  "quizId": 15,
  "message": "Imported 5 questions from lecture 10"
}
```

---

## Phụ lục A: Mini-quiz legacy endpoints (vẫn hoạt động)

Các endpoint cũ sau đây vẫn hoạt động bình thường, **không bị ảnh hưởng bởi module Quiz mới**. FE không bắt buộc phải migrate:

- **GET /api/lectures/{id}/quizzes** — Lấy danh sách câu hỏi trắc nghiệm nhanh của bài giảng.
- **POST /api/interactions** — Học sinh nộp câu trả lời mini-quiz.

---

## Phụ lục B: Result Policy — Bảng quyền xem điểm cho FE

Bảng hiển thị FE dựa trên `resultPolicy` của Assignment:

| Cấu hình | Xem tổng điểm | Xem câu Đúng/Sai | Xem Đáp án + Lời giải |
|---|---|---|---|
| **AFTER_SUBMISSION** | ✅ Ngay khi nộp | ✅ Ngay khi nộp | ✅ Ngay khi nộp |
| **AFTER_GRADING** | ⏳ Chờ Teacher chấm | ⏳ Chờ Teacher chấm | ⏳ Chờ Teacher chấm |
| **AFTER_CLOSE_AT** | ⏳ Sau `closeAt` | ⏳ Sau `closeAt` | ⏳ Sau `closeAt` |
| **NEVER** | ❌ Không bao giờ | ❌ Không bao giờ | ❌ Không bao giờ |

⚠️ **Đặc biệt quan trọng:** Nếu bài làm có câu tự luận và đang chờ chấm, trạng thái sẽ là `REVIEW_REQUIRED`. Khi đó FE **phải hiển thị** chữ **"Chờ giáo viên chấm tự luận"** thay vì hiển thị điểm số không hoàn chỉnh.
