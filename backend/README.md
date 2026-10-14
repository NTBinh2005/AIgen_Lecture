# Backend 1 — Identity, User, Payment, Authorization & Notification Service

## 1. Tổng quan (Overview)

Backend 1 chịu trách nhiệm chính về **Identity, User, Payment, Invoice, Refund, Authorization, Audit và Notification** cho nền tảng AI Text-to-Video Learning Platform.

Backend 1 đóng vai trò là **Source of Truth** cho danh tính và trạng thái thanh toán / quyền học của người dùng. Các backend service khác (Course Service, AI Generator Service, Media Storage, v.v.) tích hợp thông qua **REST API** và **Domain Events** mà không truy cập trực tiếp vào cơ sở dữ liệu của Backend 1.

---

## 2. Công nghệ & Yêu cầu môi trường

- **Java:** 21 LTS
- **Framework:** Spring Boot 3.5.x
- **Build tool:** Maven (đã bao gồm wrapper `./mvnw` / `mvnw.cmd`)
- **Database:** PostgreSQL (H2 Database cho unit/integration tests)
- **Security:** Spring Security 6, JWT (HMAC-SHA512), Token Rotation
- **Documentation:** OpenAPI 3 / Swagger UI (`springdoc-openapi`)

---

## 3. Cấu hình & Khởi chạy

### 3.1 Biến môi trường (Environment Variables)

Có thể cấu hình thông qua file `.env` hoặc truyền qua OS environment:

| Biến môi trường | Mặc định | Mô tả |
| :--- | :--- | :--- |
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/aigen_lecture` | URL kết nối PostgreSQL |
| `DATABASE_USERNAME` | `postgres` | Username DB |
| `DATABASE_PASSWORD` | `12345` | Password DB |
| `JWT_SECRET` | *(chuỗi bí mật)* | Key ký HMAC-SHA cho JWT Token |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Thời hạn Access Token |
| `JWT_REFRESH_EXPIRATION_MS` | `2592000000` (30 ngày) | Thời hạn Refresh Token |
| `GOOGLE_OAUTH_CLIENT_ID` | *(tùy chọn)* | Client ID xác thực Google Sign-In |
| `GEMINI_API_KEY` | *(tùy chọn)* | API key Google Gemini LLM |
| `TWILIO_ACCOUNT_SID` | *(tùy chọn)* | Twilio SID cho gửi SMS OTP |
| `TWILIO_AUTH_TOKEN` | *(tùy chọn)* | Twilio Token |
| `TWILIO_FROM_NUMBER` | *(tùy chọn)* | Số điện thoại gửi SMS |
| `VNPAY_TMN_CODE` | `DEMO0001` | Mã Terminal Website VNPay Sandbox |
| `VNPAY_HASH_SECRET` | *(secret key)* | Chuỗi bí mật HMAC-SHA512 VNPay |
| `VNPAY_PAYMENT_URL` | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` | Cổng thanh toán VNPay |
| `MOMO_PARTNER_CODE` | `MOMOTEST` | Mã đối tác MoMo |
| `MOMO_ACCESS_KEY` | `F8BBA842ECF85` | Access key kết nối MoMo |
| `MOMO_SECRET_KEY` | `K951B6PE1waDMi640xX08PD3vg6EkVlz` | Secret key HMAC-SHA256 MoMo |
| `MOMO_PAYMENT_URL` | `https://test-payment.momo.vn/v2/gateway/api/create` | Endpoint tạo thanh toán MoMo |

### 3.2 Khởi chạy với Maven Wrapper

```bash
# Trên Windows
.\mvnw.cmd spring-boot:run

# Trên Linux / macOS
./mvnw spring-boot:run
```

### 3.3 Chạy Test

```bash
.\mvnw.cmd test
```

### 3.4 Swagger UI / OpenAPI Specs

Sau khi khởi động ứng dụng:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

---

## 4. Hướng dẫn tích hợp cho các Backend Services (Microservices Integration Guide)

Theo kiến trúc phân rã (Microservices / Modular), các service khác **tuyệt đối không kết nối trực tiếp vào DB của Backend 1**. Sử dụng các điểm tích hợp chuẩn sau:

```text
                 ┌─────────────────────┐
                 │     Backend 1        │
                 │                     │
                 │ Auth / User         │
                 │ Payment / Invoice   │
                 │ Refund              │
                 │ Authorization       │
                 │ Audit / Notification│
                 └──────────┬──────────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
              ▼             ▼             ▼
        Token Verify   PaymentConfirmed   LearningAccessGranted
        API            Event              Event
              │             │             │
              ▼             ▼             ▼
        Other Backend Services / Microservices (Course, AI, Media)
```

### 4.1 Xác thực Token & Cấp danh tính (Token Verification API)

Các service khác gửi Access Token lên Backend 1 để kiểm tra token có hợp lệ không và nhận về danh tính, vai trò, quyền hạn của người dùng.

- **Endpoint:** `POST /api/auth/verify`
- **Auth:** Public / Inter-service
- **Request Body:**
  ```json
  {
    "token": "eyJhbGciOiJIUzUxMiJ9..."
  }
  ```
- **Response Hợp lệ (200 OK):**
  ```json
  {
    "valid": true,
    "userId": 123,
    "email": "student@example.com",
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
- **Response Không hợp lệ (200 OK):**
  ```json
  {
    "valid": false,
    "userId": null,
    "email": null,
    "role": null,
    "permissions": [],
    "message": "Invalid or expired token"
  }
  ```

### 4.2 Kiểm tra quyền học trực tiếp (Inter-service Learning Access Check)

Dành cho Course Service hoặc AI Video Service muốn kiểm tra nhanh xem một `userId` có quyền truy cập sản phẩm / khóa học `productId` hay không:

- **Endpoint:** `GET /api/access/check-user?userId={userId}&productId={productId}`
- **Auth:** Public / Inter-service
- **Response (200 OK):**
  ```json
  {
    "userId": 123,
    "productId": 456,
    "hasAccess": true
  }
  ```

### 4.3 Domain Events & Idempotency

Mỗi event đều mang một `eventId` (UUID duy nhất) để các subscriber có thể thực hiện **Idempotent processing** (tránh xử lý trùng lặp khi event gửi lại).

#### Event 1: `PaymentConfirmedEvent`
Phát ra ngay sau khi thanh toán thành công (Webhook xác thực):
```json
{
  "eventId": "a5d8b7c4-2e91-499b-9c94-bfd4b9611681",
  "eventType": "PaymentConfirmed",
  "paymentId": 101,
  "transactionId": "b130e521-8208-466d-8a5f-d2fa4d7e9b0b",
  "userId": 123,
  "productId": 456,
  "amount": 150000.00,
  "timestamp": "2026-09-20T10:00:00Z"
}
```

#### Event 2: `LearningAccessGrantedEvent`
Phát ra sau khi quyền học tương ứng với gói/sản phẩm đã được lưu trữ thành công:
```json
{
  "eventId": "e9c12345-ff12-4aa3-8890-7d6f5e4c3b2a",
  "eventType": "LearningAccessGranted",
  "userId": 123,
  "productId": 456,
  "paymentId": 101,
  "accessType": "PREMIUM",
  "grantedAt": "2026-09-20T10:00:01Z",
  "expiresAt": null
}
```

### 4.4 Tích hợp Cổng thanh toán VNPay & MoMo

Hệ thống hỗ trợ thanh toán qua 2 cổng trực tuyến thông qua kiến trúc **Strategy Pattern**:

```text
Client ──► POST /api/payments { paymentMethod: "VNPAY" | "MOMO" }
        ◄── 200 OK { paymentUrl: "https://sandbox.vnpayment.vn/..." }
Client ──► Redirect user to paymentUrl
Gateway ──► IPN Webhook (server-to-server) ──► Xác thực HMAC ──► Payment SUCCESS ──► Event
```

#### 1. Tạo thanh toán mới:
- **Endpoint:** `POST /api/payments`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body:**
  ```json
  {
    "productId": 42,
    "amount": 150000,
    "paymentMethod": "VNPAY",
    "orderInfo": "Mua khoa hoc Python Pro",
    "returnUrl": "https://myapp.com/payment-result"
  }
  ```
- **Response:** Trả về `paymentUrl`. Client chuyển hướng người dùng tới link này.

#### 2. Xử lý IPN Webhooks:
- **VNPay IPN:** `GET /api/payments/webhook/vnpay` (Xác thực chữ ký HMAC-SHA512 tự động, trả về `{RspCode: "00", Message: "Confirm Success"}`).
- **VNPay Return URL:** `GET /api/payments/callback/vnpay` (Redirect UX cho frontend).
- **MoMo IPN:** `POST /api/payments/webhook/momo` (Xác thực chữ ký HMAC-SHA256 tự động với JSON payload).
- **MoMo Return URL:** `GET /api/payments/callback/momo` (Redirect UX cho frontend).

---

## 5. Bảng ma trận Role & Permission

| Quyền hạn | STUDENT | TEACHER | ADMIN |
| :--- | :---: | :---: | :---: |
| `COURSE_VIEW` | ✓ | ✓ | ✓ |
| `COURSE_ENROLL` | ✓ | ✓ | ✓ |
| `VIDEO_VIEW` | ✓ | ✓ | ✓ |
| `PAYMENT_CREATE` | ✓ | ✓ | ✓ |
| `REFUND_CREATE` | ✓ | ✓ | ✓ |
| `COURSE_MANAGE` | - | ✓ | ✓ |
| `VIDEO_GENERATE` | - | ✓ | ✓ |
| `USER_MANAGE` | - | - | ✓ |
| `AUDIT_VIEW` | - | - | ✓ |
| `SETTINGS_MANAGE` | - | - | ✓ |

---

## 6. Tính toàn vẹn & Bảo mật dữ liệu

- Mật khẩu người dùng được băm bằng thuật toán **BCrypt** trước khi ghi vào cơ sở dữ liệu.
- Cơ chế Refresh Token áp dụng kỹ thuật **Token Rotation** và băm **SHA-256** khi lưu trữ; phiên cũ sẽ bị thu hồi khi token mới được sinh ra.
- Tự động xóa các refresh token hết hạn theo lịch trình (Scheduled task).
- Audit log ghi nhận mọi biến động liên quan đến tài khoản, phiên làm việc, giao dịch thanh toán và hoàn tiền mà không lưu trữ thông tin nhạy cảm.
