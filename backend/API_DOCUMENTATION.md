# 📖 TÀI LIỆU CHI TIẾT API — AI TEXT-TO-VIDEO LEARNING PLATFORM

Tài liệu đặc tả toàn bộ API của hệ thống Backend, bao gồm đầu vào, đầu ra, phân quyền và hướng dẫn tích hợp thanh toán SePay.

---

## 📌 MỤC LỤC
1. [Tổng quan & Khởi chạy Swagger UI](#1-tổng-quan--khởi-chạy-swagger-ui)
2. [Authentication Module](#2-authentication-module)
3. [User & Settings Module](#3-user--settings-module)
4. [Payment Module (SePay)](#4-payment-module-sepay)
5. [Invoice Module](#5-invoice-module)
6. [Refund Module](#6-refund-module)
7. [Authorization & Learning Access Module](#7-authorization--learning-access-module)
8. [Audit Log Module](#8-audit-log-module)
9. [Notification Module](#9-notification-module)
10. [Class, Lecture & AI Video Modules](#10-class-lecture--ai-video-modules)

---

## 1. TỔNG QUAN & KHỞI CHẠY SWAGGER UI

- **Base URL:** `http://localhost:8080`
- **Swagger UI Interactive:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **OpenAPI YAML Spec:** [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)
- **Header xác thực:** `Authorization: Bearer <access_token>`

---

## 2. AUTHENTICATION MODULE

### 2.1 Đăng ký tài khoản (2 bước, xác nhận OTP qua email)
**Bước 1** — gửi thông tin, hệ thống gửi OTP 6 số tới email (chưa tạo tài khoản). OTP hết hạn sau `auth.sms.otp-expiration-seconds`, tối đa 5 lần nhập sai, cooldown gửi lại theo `auth.sms.otp-resend-cooldown-seconds`.
- **Endpoint:** `POST /api/auth/register`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "name": "Nguyễn Văn A",
    "email": "vana@example.com",
    "password": "Password@123",
    "role": "STUDENT" // Giá trị: "STUDENT" hoặc "TEACHER"
  }
  ```
- **Response:** `200 OK` — `{ "message": "Mã OTP đã được gửi tới email của bạn", "expiresInSeconds": 300 }`

**Bước 2** — xác nhận OTP, tạo tài khoản và nhận token.
- **Endpoint:** `POST /api/auth/register/verify`
- **Request Body:** `{ "email": "vana@example.com", "otp": "123456" }`
- **Response:** `200 OK`
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "b9687e14-41d5-45d3-8b77-2f3b9cece419",
    "userId": 1,
    "name": "Nguyễn Văn A",
    "email": "vana@example.com",
    "role": "STUDENT",
    "phoneNumber": null,
    "authProvider": "LOCAL"
  }
  ```

### 2.2 Đăng nhập bằng Email/Mật khẩu
- **Endpoint:** `POST /api/auth/login`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "email": "vana@example.com",
    "password": "Password@123"
  }
  ```
- **Response:** `200 OK` (Cấu trúc `AuthResponse` tương tự Register)

### 2.3 Đăng nhập bằng Google ID Token
- **Endpoint:** `POST /api/auth/google`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "idToken": "eyJhbGciOiJSUzI1NiIs..."
  }
  ```
- **Response:** `200 OK` (`AuthResponse` với `authProvider: "GOOGLE"`)

### 2.4 Gửi mã OTP đăng nhập qua SMS
- **Endpoint:** `POST /api/auth/sms/request-otp`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "phoneNumber": "+84987654321"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "status": "SUCCESS",
    "message": "Mã OTP đã được gửi đến số điện thoại",
    "expiresInSeconds": 300,
    "testOtpCode": "123456"
  }
  ```

### 2.5 Xác thực OTP SMS
- **Endpoint:** `POST /api/auth/sms/verify`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "phoneNumber": "+84987654321",
    "otp": "123456"
  }
  ```
- **Response:** `200 OK` (`AuthResponse` với `authProvider: "PHONE"`)

### 2.6 Làm mới Token (Token Rotation)
- **Endpoint:** `POST /api/auth/refresh`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "refreshToken": "b9687e14-41d5-45d3-8b77-2f3b9cece419"
  }
  ```
- **Response:** `200 OK` (`AuthResponse` chứa Access Token mới & Refresh Token mới)

### 2.7 Đăng xuất
- **Endpoint:** `POST /api/auth/logout`
- **Quyền:** Public
- **Request Body (JSON):**
  ```json
  {
    "refreshToken": "b9687e14-41d5-45d3-8b77-2f3b9cece419"
  }
  ```
- **Response:** `204 No Content`

### 2.8 Xác thực Token liên dịch vụ (Inter-service Token Verify)
- **Endpoint:** `POST /api/auth/verify`
- **Quyền:** Public / Inter-service
- **Request Body (JSON):**
  ```json
  {
    "token": "eyJhbGciOiJIUzUxMiJ9..."
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "valid": true,
    "userId": 1,
    "email": "vana@example.com",
    "role": "STUDENT",
    "permissions": [
      "COURSE_VIEW",
      "COURSE_ENROLL",
      "VIDEO_VIEW",
      "PAYMENT_CREATE",
      "REFUND_CREATE"
    ],
    "message": "Token is valid"
  }
  ```

---

## 3. USER & SETTINGS MODULE

### 3.1 Xem hồ sơ tài khoản hiện tại
- **Endpoint:** `GET /api/settings/profile`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  {
    "userId": 1,
    "name": "Nguyễn Văn A",
    "email": "vana@example.com",
    "phoneNumber": "0987654321",
    "role": "STUDENT",
    "status": "ACTIVE",
    "authProvider": "LOCAL",
    "createdAt": "2026-09-15T08:30:00Z"
  }
  ```

### 3.2 Cập nhật hồ sơ cá nhân
- **Endpoint:** `PUT /api/settings/profile`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body (JSON):**
  ```json
  {
    "name": "Nguyễn Văn A (Cập nhật)",
    "phoneNumber": "0912345678"
  }
  ```
- **Response:** `200 OK` (`UserResponse`)

### 3.3 Đổi mật khẩu
- **Endpoint:** `PUT /api/settings/password`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body (JSON):**
  ```json
  {
    "oldPassword": "Password@123",
    "newPassword": "NewPassword@456"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "message": "Đổi mật khẩu thành công"
  }
  ```

### 3.4 Quản trị người dùng (Admin)
- `GET /api/users`: Lấy danh sách users (Headers: `Bearer <token>` role `ADMIN`) -> Output: `List<UserResponse>`
- `GET /api/users/{userId}`: Chi tiết một user -> Output: `UserResponse`
- `POST /api/users`: Tạo user bởi Admin -> Body: `{"name": "...", "email": "...", "password": "...", "role": "TEACHER"}`
- `PATCH /api/users/{userId}`: Cập nhật role / trạng thái user -> Body: `{"role": "ADMIN", "status": "INACTIVE"}`
- `DELETE /api/users/{userId}`: Xóa user -> Output: `204 No Content`

---

## 4. PAYMENT MODULE (SEPAY)

### 4.1 Khởi tạo thanh toán
- **Endpoint:** `POST /api/payments`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body (JSON):**
  ```json
  {
    "planId": 2,
    "paymentMethod": "SEPAY",
    "orderInfo": "Mua gói Student Monthly"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "paymentId": 10,
    "transactionId": "DH12345678",
    "userId": 1,
    "productId": 42,
    "amount": 150000.00,
    "status": "PENDING",
    "paymentMethod": "SEPAY",
    "paymentUrl": "https://vietqr.app/img?acc=...&bank=Vietcombank&amount=150000&des=DH12345678...",
    "orderInfo": "Mua khóa học AI Text-to-Video",
    "createdAt": "2026-09-20T15:00:00Z",
    "updatedAt": "2026-09-20T15:00:00Z"
  }
  ```

### 4.2 Chi tiết giao dịch
- **Endpoint:** `GET /api/payments/{id}`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`PaymentResponse`)

### 4.3 Lịch sử giao dịch của tôi
- **Endpoint:** `GET /api/payments/my-payments`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`List<PaymentResponse>`)

### 4.4 Hủy giao dịch đang PENDING
- **Endpoint:** `POST /api/payments/{id}/cancel`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`PaymentResponse` với `status: "CANCELLED"`)
- Giao dịch còn `PENDING` quá `SEPAY_PENDING_EXPIRATION_MINUTES` (mặc định 15 phút)
  cũng tự chuyển sang `CANCELLED`. Webhook tiền vào hợp lệ đến sau vẫn chuyển giao dịch sang
  `SUCCESS`, kích hoạt gói đúng một lần và thông báo admin để đối soát.

### 4.5 SePay Webhook Server-to-Server
- **Endpoint:** `POST /api/payments/webhook/sepay`
- **Quyền:** Public ở tầng JWT; bắt buộc HMAC-SHA256.
- **Headers:** `X-SePay-Signature: sha256=<hex>`, `X-SePay-Timestamp: <unix-seconds>`
- **Request Body (JSON):**
  ```json
  {
    "id": 92704,
    "gateway": "Vietcombank",
    "accountNumber": "0123456789",
    "code": "DH12345678",
    "content": "DH12345678 thanh toan",
    "transferType": "in",
    "transferAmount": 150000,
    "referenceCode": "FT24012345678"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "success": true
  }
  ```

---

## 5. INVOICE MODULE

### 5.1 Lấy danh sách hóa đơn
- **Endpoint:** `GET /api/invoices`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  [
    {
      "id": 1,
      "invoiceNumber": "INV-A1B2C3D4",
      "userId": 1,
      "paymentId": 10,
      "amount": 150000.00,
      "status": "ISSUED",
      "issuedAt": "2026-09-20T15:02:00Z",
      "createdAt": "2026-09-20T15:02:00Z"
    }
  ]
  ```

### 5.2 Chi tiết hóa đơn
- **Endpoint:** `GET /api/invoices/{id}`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`InvoiceResponse`)

### 5.3 Xuất file PDF hóa đơn
- **Endpoint:** `GET /api/invoices/{id}/pdf`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (File binary PDF download)

---

## 6. REFUND MODULE

### 6.1 Yêu cầu hoàn tiền
- **Endpoint:** `POST /api/refunds`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body (JSON):**
  ```json
  {
    "paymentId": 10,
    "reason": "Mua nhầm khóa học",
    "bankName": "Vietcombank",
    "bankAccount": "0012345678",
    "accountHolder": "NGUYEN VAN A"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "refundId": 1,
    "paymentId": 10,
    "userId": 1,
    "amount": 150000.00,
    "reason": "Mua nhầm khóa học",
    "status": "REQUESTED",
    "bankName": "Vietcombank",
    "bankAccount": "0012345678",
    "accountHolder": "NGUYEN VAN A",
    "refundCode": "HT1",
    "qrUrl": null,
    "sepayWebhookId": null,
    "sepayReferenceCode": null,
    "createdAt": "2026-09-20T15:10:00Z",
    "updatedAt": "2026-09-20T15:10:00Z"
  }
  ```

### 6.2 Chi tiết yêu cầu hoàn tiền
- **Endpoint:** `GET /api/refunds/{id}`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`RefundResponse`)

### 6.3 Danh sách yêu cầu hoàn tiền của tôi
- **Endpoint:** `GET /api/refunds/my-refunds`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`List<RefundResponse>`)

### 6.4 Duyệt hoặc từ chối yêu cầu (ADMIN)
- **Endpoint:** `PATCH /api/refunds/{id}/status`
- **Body:** `{"status":"APPROVED"}` hoặc `{"status":"REJECTED"}`
- Khi duyệt yêu cầu SePay, response có `qrUrl` để admin quét và chuyển đúng số tiền/nội dung `refundCode`.
- Không được gửi `COMPLETED` thủ công cho SePay. Webhook `transferType=out` hợp lệ tự chuyển refund sang
  `COMPLETED`, payment sang `REFUNDED` và thu hồi quyền của lần mua tương ứng.

---

## 7. AUTHORIZATION & LEARNING ACCESS MODULE

### 7.1 Danh sách quyền học của tôi
- **Endpoint:** `GET /api/access/my-access`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  [
    {
      "id": 1,
      "userId": 1,
      "productId": 42,
      "paymentId": 10,
      "accessType": "PREMIUM",
      "expiresAt": null,
      "grantedAt": "2026-09-20T15:02:00Z"
    }
  ]
  ```

### 7.2 Kiểm tra quyền học cá nhân
- **Endpoint:** `GET /api/access/check?productId=42`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  {
    "hasAccess": true
  }
  ```

### 7.3 Inter-service tra cứu quyền học theo User ID
- **Endpoint:** `GET /api/access/check-user?userId=1&productId=42`
- **Quyền:** Public / Inter-service
- **Response:** `200 OK`
  ```json
  {
    "userId": 1,
    "productId": 42,
    "hasAccess": true
  }
  ```

---

## 8. AUDIT LOG MODULE

### 8.1 Tra cứu lịch sử kiểm toán
- **Endpoint:** `GET /api/audit-logs`
- **Headers:** `Authorization: Bearer <token>` (Chỉ `ADMIN`)
- **Query Params:** `userId` (optional), `action` (optional), `page` (default: 0), `size` (default: 20)
- **Response:** `200 OK`
  ```json
  [
    {
      "id": 1,
      "userId": 1,
      "action": "PAYMENT_SUCCESS",
      "entityType": "PAYMENT",
      "entityId": "10",
      "detail": "Payment confirmed via SEPAY for amount: 150000.00",
      "createdAt": "2026-09-20T15:02:00Z"
    }
  ]
  ```

---

## 9. NOTIFICATION MODULE

### 9.1 Lấy danh sách thông báo
- **Endpoint:** `GET /api/notifications`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  [
    {
      "id": 1,
      "userId": 1,
      "title": "Thanh toán thành công",
      "message": "Giao dịch b130e521 đã thanh toán thành công 150000.00 VND",
      "type": "PAYMENT_SUCCESS",
      "isRead": false,
      "referenceId": "10",
      "createdAt": "2026-09-20T15:02:00Z"
    }
  ]
  ```

### 9.2 Số lượng thông báo chưa đọc
- **Endpoint:** `GET /api/notifications/unread-count`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  {
    "unreadCount": 3
  }
  ```

### 9.3 Đánh dấu đã đọc một thông báo
- **Endpoint:** `PATCH /api/notifications/{id}/read`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK` (`NotificationResponse` với `isRead: true`)

### 9.4 Đánh dấu đã đọc tất cả
- **Endpoint:** `PATCH /api/notifications/read-all`
- **Headers:** `Authorization: Bearer <token>`
- **Response:** `200 OK`
  ```json
  {
    "updatedCount": 3
  }
  ```

---

## 10. CLASS, LECTURE & AI VIDEO MODULES

### 10.1 Danh sách bài giảng
- **Endpoint:** `GET /api/lectures`
- **Quyền:** `TEACHER`, `ADMIN`
- **Phân trang:** `page` mặc định `0`, `size` mặc định `20` và tối đa `100`; giá trị ngoài phạm vi trả `400`.
- **Bộ lọc tùy chọn, có thể kết hợp:**
  - `title`: tìm gần đúng, không phân biệt hoa thường;
  - `teacherId`: chủ sở hữu bài giảng. Chỉ `ADMIN` được chọn giáo viên khác;
  - `status`: `DRAFT`, `PROCESSING`, `READY`, `PUBLISHED`, `FAILED`, `ARCHIVED`;
  - `videoStatus`: `NOT_REQUESTED`, `PENDING`, `PROCESSING`, `DONE`, `FAILED`;
  - `accessScope`: `PRIVATE`, `CLASS`.
- Giá trị enum không hợp lệ trả `400`. `TEACHER` luôn chỉ thấy bài do mình sở hữu hoặc được cộng tác.
- **Response:** `Page<LectureResponse>`.

### 10.2 Sinh video bài giảng từ file tài liệu
- **Endpoint:** `POST /api/lectures/generate-from-file`
- **Content-Type:** `multipart/form-data`
- **Form Data:**
  - `file`: File upload (`.pdf`, `.docx`, `.pptx`)
  - `title`: Tên bài giảng (Text)
- **Response:** `200 OK`
  ```json
  {
    "lectureId": 5,
    "title": "Nhập môn AI",
    "videoJobId": "job-uuid-1234",
    "videoStatus": "PROCESSING",
    "message": "File đã được phân tích và bắt đầu render video"
  }
  ```

### 10.3 Polling trạng thái render video
- **Endpoint:** `GET /api/lectures/{id}/video-status`
- **Quyền:** Public
- **Response:** `200 OK`
  ```json
  {
    "lectureId": 5,
    "videoStatus": "DONE", // "PENDING", "PROCESSING", "DONE", "FAILED"
    "videoUrl": "https://storage.example.com/videos/lecture_5.mp4"
  }
  ```

### 10.4 Tự ghi danh vào lớp học
- **Endpoint:** `POST /api/classes/self-enroll`
- **Headers:** `Authorization: Bearer <token>` (Role `STUDENT`)
- **Request Body (JSON):**
  ```json
  {
    "classCode": "AI101",
    "semester": "FALL2026"
  }
  ```
- **Response:** `200 OK` (`EnrollmentResponse`)

### 10.4 Nộp câu trả lời tương tác khi xem video
- **Endpoint:** `POST /api/interactions`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body (JSON):**
  ```json
  {
    "elementId": 12,
    "submittedAnswer": "A",
    "timeSpent": 45
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "isCorrect": true,
    "correctAnswer": "A",
    "explanation": "Chính xác! Machine learning là một tập con của Trí tuệ nhân tạo."
  }
  ```

## Quy ước phân trang (cập nhật FIX #74)

Các API danh sách lớn nhận `page` (mặc định `0`), `size` (mặc định `20`, tối đa `100`)
và `sort`. Response dùng Spring `Page`: dữ liệu nằm trong `content`; metadata gồm
`number`, `size`, `totalElements`, `totalPages`, `first`, `last`.

Áp dụng cho: `GET /api/users`, `/api/audit-logs`, `/api/notifications`,
`/api/payments/admin`, `/api/refunds` (admin), `/api/invoices`, `/api/subscriptions`,
`/api/enrollments`, `/api/classes`, `/api/lectures/{id}/comments`, `/api/exports`,
`/api/quiz-assignments/{id}/progress`, và `/api/students/me/quiz-history`.

Hai API `progress` và `quiz-history` trả `AttemptSummaryResponse` không chứa `answers`.
Dùng `GET /api/attempts/{id}` khi cần tải câu trả lời của một lượt làm bài.
