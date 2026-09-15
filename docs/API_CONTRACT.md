# API Contract — AI Video Lecture System

> **Mục đích:** Tài liệu này là nguồn sự thật duy nhất (single source of truth) cho tất cả API endpoint mà Frontend, Mobile, và Backend dùng chung.
> Khi cần thay đổi field name / kiểu dữ liệu, **phải cập nhật file này trước**, sau đó mới sửa code.

---

## Mục lục

1. [Base URLs](#1-base-urls)
2. [Enum VideoStatus](#2-enum-videostatus)
3. [Authentication](#3-authentication)
4. [video-service APIs](#4-video-service-apis)
5. [Backend APIs — Auth](#5-backend-apis--auth)
6. [Backend APIs — Lecture](#6-backend-apis--lecture)
7. [Flow đầy đủ (Teacher → Student)](#7-flow-đầy-đủ-teacher--student)

---

## 1. Base URLs

| Service | Local Dev | Mô tả |
|---------|-----------|-------|
| `backend` (Spring Boot) | `http://localhost:8080/api` | API chính — FE & Mobile gọi vào đây |
| `video-service` (Node.js + Remotion) | `http://localhost:3001` | Microservice render video — chỉ backend gọi |

> **Lưu ý Android Emulator:** `localhost` trỏ về emulator, dùng `10.0.2.2` để truy cập máy host.

---

## 2. Enum VideoStatus

Giá trị hợp lệ cho field `videoStatus` trong mọi response liên quan đến Lecture:

| Giá trị | Ý nghĩa |
|---------|---------|
| `PENDING` | Lecture vừa tạo, chưa gửi sang video-service |
| `PROCESSING` | Đã gửi jobId sang video-service, đang render |
| `DONE` | Render xong, `videoUrl` có giá trị |
| `FAILED` | Render thất bại (kiểm tra log video-service) |

---

## 3. Authentication

Tất cả các API yêu cầu xác thực (trừ `/api/auth/**` và một số endpoint public cho Student xem bài giảng) phải gửi kèm JWT token trong header:

```http
Authorization: Bearer <token>
```

**Common Error Responses:**
- `401 Unauthorized`: Không có token, token hết hạn hoặc không hợp lệ. Frontend cần tự động logout.
- `403 Forbidden`: Token hợp lệ nhưng user không có quyền (ví dụ: Student gọi API của Teacher, hoặc Teacher thao tác trên bài giảng không phải của mình).

---

## 4. video-service APIs

> ⚠️ **Nội bộ:** video-service chỉ được gọi bởi backend (Spring Boot). Frontend và Mobile không gọi trực tiếp.

### 3.1 `POST /generate-video`

Tạo mới một video render job (bất đồng bộ).

**Request Body:**

```json
{
  "lectureId": "42",
  "slides": [
    {
      "title": "Giới thiệu về Cây nhị phân",
      "bulletPoints": [
        "Mỗi node có tối đa 2 con",
        "Node trái < node cha",
        "Node phải > node cha"
      ],
      "narrationText": "Trong bài học hôm nay, chúng ta sẽ tìm hiểu về cây nhị phân tìm kiếm..."
    },
    {
      "title": "Các thao tác cơ bản",
      "bulletPoints": [
        "Insert: O(log n) trung bình",
        "Search: O(log n) trung bình",
        "Delete: O(log n) trung bình"
      ],
      "narrationText": "Cây nhị phân hỗ trợ 3 thao tác chính với độ phức tạp logarithm..."
    }
  ]
}
```

| Field | Kiểu | Bắt buộc | Mô tả |
|-------|------|----------|-------|
| `lectureId` | `string` | ✅ | ID bài giảng từ backend (dùng để đối chiếu) |
| `slides` | `Slide[]` | ✅ | Mảng slides, tối thiểu 1 phần tử |
| `slides[].title` | `string` | ✅ | Tiêu đề slide |
| `slides[].bulletPoints` | `string[]` | ✅ | Danh sách bullet points |
| `slides[].narrationText` | `string` | ✅ | Văn bản chuyển giọng đọc (TTS input) |

**Response 202 Accepted:**

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "pending",
  "message": "Video render đã bắt đầu. Poll GET /video-status/:jobId để kiểm tra tiến trình."
}
```

**Response 400 Bad Request (ví dụ):**

```json
{
  "error": "slides là bắt buộc và phải là mảng không rỗng"
}
```

---

### 3.2 `GET /video-status/:jobId`

Lấy trạng thái của một render job. Backend poll endpoint này mỗi 15 giây.

**Path Params:**

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `jobId` | `string (UUID)` | Job ID nhận từ POST /generate-video |

**Response 200 OK:**

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "lectureId": "42",
  "status": "done",
  "videoUrl": "http://localhost:3001/videos/550e8400-e29b-41d4-a716-446655440000.mp4",
  "error": null,
  "createdAt": "2026-06-21T16:00:00.000Z",
  "updatedAt": "2026-06-21T16:03:45.000Z"
}
```

| Field | Kiểu | Mô tả |
|-------|------|-------|
| `jobId` | `string` | UUID của job |
| `lectureId` | `string` | ID bài giảng tương ứng |
| `status` | `"pending" \| "processing" \| "done" \| "failed"` | Trạng thái render |
| `videoUrl` | `string \| null` | URL video MP4, có khi `status = "done"` |
| `error` | `string \| null` | Thông báo lỗi, có khi `status = "failed"` |

**Response 404 Not Found:**

```json
{
  "error": "Không tìm thấy job với jobId: xxx"
}
```

## 5. Backend APIs — Auth

### 5.1 `POST /api/auth/login`
Đăng nhập để nhận JWT token.

**Request Body:**
```json
{
  "email": "teacher@edumind.dev",
  "password": "Teacher123!"
}
```

**Response 200 OK:**
```json
{
  "token": "ey...",
  "user": {
    "userId": 1,
    "email": "teacher@edumind.dev",
    "name": "Nguyen Huy Hoang",
    "role": "ROLE_TEACHER"
  }
}
```

### 5.2 `POST /api/auth/register`
Đăng ký tài khoản mới. Trả về token tương tự login.

---

## 6. Backend APIs — Lecture

> FE và Mobile gọi các endpoint này. Auth header: `Authorization: Bearer <JWT>` bắt buộc.

### 6.1 `POST /api/lectures`

Teacher tạo mới bài giảng và trigger video generation.

**Request Body:**

```json
{
  "title": "Cấu trúc dữ liệu — Cây nhị phân",
  "originalSource": "Nội dung gốc trích xuất từ PDF (tùy chọn)",
  "slides": [
    {
      "title": "Giới thiệu về Cây nhị phân",
      "bulletPoints": ["Mỗi node có tối đa 2 con", "Node trái < node cha"],
      "narrationText": "Trong bài học hôm nay..."
    }
  ]
}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|-------|------|----------|-----------|
| `title` | `string` | ✅ | max 255 ký tự |
| `originalSource` | `string` | ❌ | Nội dung gốc PDF/DOCX |
| `slides` | `SlideDto[]` | ✅ | Ít nhất 1 slide |
| `slides[].title` | `string` | ✅ | |
| `slides[].bulletPoints` | `string[]` | ✅ | |
| `slides[].narrationText` | `string` | ✅ | |

**Response 202 Accepted:**

```json
{
  "lectureId": 42,
  "title": "Cấu trúc dữ liệu — Cây nhị phân",
  "originalSource": "...",
  "teacherName": "Nguyễn Huy Hoàng",
  "teacherId": 5,
  "videoStatus": "PENDING",
  "videoUrl": null,
  "createdAt": "2026-06-21T16:00:00"
}
```

---

```

---

### 6.2 `GET /api/lectures`

Lấy danh sách bài giảng của teacher — hỗ trợ Search, Sort, Paging, Filtering.

**Query Params:**

| Param | Kiểu | Mặc định | Mô tả |
|-------|------|----------|-------|
| `title` | `string` | — | Filter theo tiêu đề (case-insensitive, contains) |
| `page` | `integer` | `0` | Số trang (0-indexed) |
| `size` | `integer` | `10` | Số item mỗi trang |
| `sort` | `string` | `createdAt,desc` | Field sắp xếp. VD: `title,asc` |

**Ví dụ:** `GET /api/lectures?title=Cây&page=0&size=5&sort=createdAt,desc`

**Response 200 OK:**

```json
{
  "content": [
    {
      "lectureId": 42,
      "title": "Cấu trúc dữ liệu — Cây nhị phân",
      "originalSource": null,
      "teacherName": "Nguyễn Huy Hoàng",
      "teacherId": 5,
      "videoStatus": "DONE",
      "videoUrl": "http://localhost:3001/videos/abc.mp4",
      "createdAt": "2026-06-21T16:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

---

### 6.3 `GET /api/lectures/{id}`

Lấy chi tiết một bài giảng.

**Path Params:** `id` (integer)

**Response 200 OK:** (cùng schema với LectureResponse ở trên)

**Response 403 Forbidden:** Khi người gọi là Teacher nhưng không phải owner.

**Response 404 Not Found:** Lecture không tồn tại hoặc đã soft-deleted

---

### 6.4 `GET /api/lectures/{id}/video-status`

Poll trạng thái render video. **FE và Mobile gọi endpoint này mỗi 3 giây**.

**Path Params:** `id` (integer — lectureId)

**Response 200 OK:**

```json
{
  "lectureId": 42,
  "videoStatus": "PROCESSING",
  "videoUrl": null,
  "errorMessage": null
}
```

| Field | Kiểu | Mô tả |
|-------|------|-------|
| `lectureId` | `integer` | ID bài giảng |
| `videoStatus` | `VideoStatus` | Xem [Enum VideoStatus](#2-enum-videostatus) |
| `videoUrl` | `string \| null` | URL MP4, có khi `videoStatus = DONE` |
| `errorMessage` | `string \| null` | Thông báo lỗi, có khi `videoStatus = FAILED` |

---

### 6.5 `PUT /api/lectures/{id}`

Sửa tiêu đề bài giảng.

**Path Params:** `id` (integer)

**Request Body:**
```json
{
  "title": "Tên bài giảng mới"
}
```

**Response 200 OK:** (trả về đối tượng Lecture sau khi sửa)

**Response 403 Forbidden** — Không phải owner.

---

### 6.6 `DELETE /api/lectures/{id}`

Soft-delete bài giảng (BR-08: không hard-delete).

**Path Params:** `id` (integer)

**Response 204 No Content** — Thành công, không có body.

**Response 403 Forbidden** — Không phải owner.

---

### 6.7 `GET /api/lectures/{id}/quizzes`

Lấy danh sách câu hỏi trắc nghiệm của một bài giảng.

**Path Params:** `id` (integer)

**Auth:** STUDENT hoặc TEACHER

**Response 200 OK:**
- Với Student: `correctAnswer` sẽ là `null`.
- Với Teacher: `correctAnswer` hiển thị đầy đủ (A/B/C/D).

```json
[
  {
    "elementId": 1,
    "questionText": "Câu hỏi?",
    "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
    "orderIndex": 0,
    "correctAnswer": "A" // (null nếu là Student)
  }
]
```

---

### 6.8 `POST /api/interactions`

Học sinh nộp câu trả lời cho câu hỏi trắc nghiệm. Backend sẽ tự đánh giá đúng/sai.

**Auth:** STUDENT

**Request Body:**
```json
{
  "elementId": 1,
  "submittedAnswer": "A",
  "timeSpent": 15
}
```

**Response 201 Created:**
```json
{
  "logId": 100,
  "elementId": 1,
  "submittedAnswer": "A",
  "isCorrect": true,
  "correctAnswer": "A",
  "isFirstAttempt": true
}
```

---

## 7. Flow đầy đủ (Teacher → Student)

```
Teacher click "Tạo video bài giảng"
        │
        ▼
POST /api/lectures
  Body: { title, slides: [...] }
        │
        ▼
Backend tạo Lecture (videoStatus=PENDING) → trả về 202
        │
        ├──[Async, fire-and-forget]──▶ POST http://localhost:3001/generate-video
        │                                       { lectureId, slides }
        │                                            │
        │                              nhận jobId → lưu vào Lecture.videoJobId
        │                              set Lecture.videoStatus = PROCESSING
        │
        ▼
FE poll  GET /api/lectures/42/video-status   (mỗi 3 giây)
         → { videoStatus: "PROCESSING", videoUrl: null }
         → { videoStatus: "PROCESSING", videoUrl: null }
         ...

Đồng thời, Backend @Scheduled poll mỗi 15 giây:
GET http://localhost:3001/video-status/{jobId}
  → { status: "done", videoUrl: "http://localhost:3001/videos/abc.mp4" }
  → update Lecture.videoStatus = DONE, Lecture.videoUrl = "..."

FE poll tiếp theo nhận được:
GET /api/lectures/42/video-status
  → { videoStatus: "DONE", videoUrl: "http://localhost:3001/videos/abc.mp4" }
        │
        ▼
FE dừng polling → hiển thị <video> player

Student vào xem:
GET /api/lectures/42
  → { videoStatus: "DONE", videoUrl: "..." }
        │
        ▼
Student xem video trong <video> player (web) hoặc VideoPlayer widget (Flutter)
```

---

> **Cập nhật lần cuối:** 21/06/2026
> **Phiên bản:** v1.0 — Giai đoạn 1 (local storage, mock TTS)
