# Backend 1 -- Authentication, User, Payment & Authorization

## 1. Tổng quan

**Project:** AI Text-to-Video Learning Platform\
**Mục tiêu:** Xây dựng hệ thống chuyển văn bản thành video AI phục vụ
học tập. Người dùng có thể đăng ký/đăng nhập, mua hoặc đăng ký các gói
học, thanh toán và được cấp quyền truy cập nội dung tương ứng.

Backend 1 phụ trách toàn bộ nhóm chức năng liên quan đến **Identity,
User, Payment và Access Control**, đồng thời cung cấp các điểm tích hợp
để các service khác biết được người dùng là ai, đã thanh toán hay chưa
và người dùng có quyền học nội dung nào.

------------------------------------------------------------------------

## 2. Phạm vi sở hữu

  -----------------------------------------------------------------------
Module                              Trách nhiệm
  ----------------------------------- -----------------------------------
**Authentication (Auth)**           Đăng ký, đăng nhập, logout, refresh
token, quản lý phiên/token, OAuth2.0, JWT, xác thực email, quên mật khẩu, reset password

**User**                            Thông tin tài khoản, profile, trạng
thái tài khoản

**Payment**                         Tạo yêu cầu thanh toán, xử lý trạng
thái thanh toán, tích hợp payment
gateway

**Invoice**                         Tạo và quản lý hóa đơn sau khi
thanh toán

**Refund**                          Tạo và xử lý yêu cầu hoàn tiền

**Authorization**                   Role, permission và quyền truy cập
khóa học/nội dung

**Audit**                           Ghi nhận các hành động quan trọng
của người dùng và hệ thống

**Notification**                    Gửi thông báo liên quan đến tài
khoản, thanh toán, quyền học
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 3. Chi tiết yêu cầu

## 3.1 Authentication

### Chức năng

-   User đăng ký tài khoản.
-   User đăng nhập bằng email/username và password.
-   Hash password trước khi lưu database.
-   Sinh Access Token sau khi đăng nhập thành công.
-   Hỗ trợ Refresh Token.
-   Logout và revoke/expire refresh token.
-   Quên mật khẩu.
-   Reset password bằng token.
-   Verify email nếu project yêu cầu.
-   Khóa/mở khóa tài khoản.
-   Kiểm tra trạng thái tài khoản trước khi cho phép đăng nhập.

### Security

-   Không lưu password dạng plain text.
-   Access Token có thời gian hết hạn.
-   Refresh Token phải được quản lý an toàn.
-   API cần xác thực phải kiểm tra JWT.
-   Không trả password hoặc sensitive information trong response.

------------------------------------------------------------------------

## 3.2 User Management

### Thông tin User cơ bản

Ví dụ:

``` text
User
├── id
├── username
├── email
├── passwordHash
├── fullName
├── avatar
├── status
├── createdAt
└── updatedAt
```

### Chức năng

-   Xem profile.
-   Cập nhật profile.
-   Đổi password.
-   Cập nhật avatar.
-   Xem trạng thái tài khoản.
-   Admin có thể quản lý user.
-   Disable/enable user.

### API dự kiến

``` http
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh
POST   /api/auth/logout

GET    /api/users/me
PUT    /api/users/me
PUT    /api/users/me/password

GET    /api/users
GET    /api/users/{id}
PUT    /api/users/{id}/status
```

------------------------------------------------------------------------

# 4. Payment

## Mục tiêu

Cho phép người dùng thanh toán để mua gói học, khóa học hoặc nội dung
Premium.

### Payment Flow

``` text
User
  │
  ▼
Select Course / Plan
  │
  ▼
Create Payment
  │
  ▼
Payment Gateway
  │
  ├── SUCCESS
  │      │
  │      ▼
  │   Confirm Payment
  │      │
  │      ▼
  │   Create Invoice
  │      │
  │      ▼
  │   Grant Learning Permission
  │
  └── FAILED
         │
         ▼
      Payment Failed
```

### Trạng thái Payment

``` text
PENDING
SUCCESS
FAILED
CANCELLED
REFUNDED
```

### Yêu cầu

-   Tạo payment transaction.
-   Tạo unique transaction/order ID.
-   Lưu amount, user, product/course/plan và thời gian thanh toán.
-   Xác nhận callback/webhook từ payment gateway.
-   Không tin tưởng hoàn toàn amount/status từ client.
-   Kiểm tra transaction trước khi cập nhật trạng thái.
-   Tránh xử lý callback nhiều lần (idempotency).
-   Publish event khi thanh toán thành công.

### Cổng thanh toán hỗ trợ
-   **VNPay:** Sinh URL chuyển hướng đến VNPay Sandbox/Production, xác thực chữ ký HMAC-SHA512 qua IPN callback.
-   **MoMo:** Sinh link thanh toán MoMo v2 API, xác thực chữ ký HMAC-SHA256 qua IPN webhook.

### API hiện tại
``` http
POST /api/payments                 # Tạo payment kèm paymentMethod (VNPAY | MOMO) -> trả về paymentUrl
GET  /api/payments/{id}            # Chi tiết payment
GET  /api/payments/my-payments     # Danh sách payment của user
POST /api/payments/{id}/cancel     # Huỷ payment PENDING
GET  /api/payments/webhook/vnpay   # VNPay IPN server-to-server webhook
GET  /api/payments/callback/vnpay  # VNPay return URL (UX redirect)
POST /api/payments/webhook/momo    # MoMo IPN server-to-server webhook
GET  /api/payments/callback/momo   # MoMo return URL (UX redirect)
POST /api/payments/webhook         # Legacy webhook
```

------------------------------------------------------------------------

# 5. Invoice

Invoice được tạo khi payment được xác nhận thành công.

### Thông tin cơ bản

``` text
Invoice
├── id
├── invoiceNumber
├── userId
├── paymentId
├── amount
├── status
├── issuedAt
└── createdAt
```

### Yêu cầu

-   Tạo invoice sau khi payment thành công.
-   Invoice phải liên kết với Payment.
-   Invoice number phải unique.
-   Không tạo duplicate invoice cho cùng một payment.
-   User có thể xem lịch sử invoice.
-   Có thể cung cấp invoice dưới dạng PDF nếu project yêu cầu.

### API

``` http
GET /api/invoices
GET /api/invoices/{id}
GET /api/invoices/{id}/pdf
```

------------------------------------------------------------------------

# 6. Refund

## Mục tiêu

Cho phép xử lý hoàn tiền cho những transaction đáp ứng điều kiện.

### Flow

``` text
User
  │
  ▼
Request Refund
  │
  ▼
Validate Payment
  │
  ├── Invalid → Reject
  │
  └── Valid
        │
        ▼
   Refund Processing
        │
        ▼
   Payment Gateway
        │
        ▼
   Refund Success
```

### Trạng thái

``` text
REQUESTED
PROCESSING
APPROVED
REJECTED
COMPLETED
FAILED
```

### Yêu cầu

-   Kiểm tra payment có tồn tại không.
-   Kiểm tra payment có đủ điều kiện refund không.
-   Không refund vượt quá số tiền đã thanh toán.
-   Không refund nhiều lần cho cùng transaction.
-   Ghi lại lý do refund.
-   Cập nhật Payment/Invoice tương ứng.
-   Ghi audit log cho refund.

### API

``` http
POST /api/refunds
GET  /api/refunds/{id}
GET  /api/refunds/my-refunds
```

------------------------------------------------------------------------

# 7. Authorization

## Mục tiêu

Xác định **user được phép truy cập chức năng hoặc nội dung nào**.

Authorization khác Authentication:

``` text
Authentication
→ "Bạn là ai?"

Authorization
→ "Bạn được phép làm gì?"
```

### Role ví dụ

``` text
USER
ADMIN
```

Có thể mở rộng:

``` text
STUDENT
INSTRUCTOR
ADMIN
```

### Permission

Ví dụ:

``` text
COURSE_VIEW
COURSE_ENROLL
VIDEO_VIEW
VIDEO_GENERATE
PAYMENT_CREATE
REFUND_CREATE
USER_MANAGE
COURSE_MANAGE
```

### Learning Access

Hệ thống cần kiểm tra:

``` text
User
 │
 ├── Free Course → Allow
 │
 └── Premium Course
        │
        ▼
     Check Payment
        │
        ├── Paid → Allow
        └── Not Paid → Deny
```

------------------------------------------------------------------------

# 8. Audit

Audit dùng để ghi nhận các hành động quan trọng nhằm phục vụ:

-   Security.
-   Debugging.
-   Tracking.
-   Compliance.
-   Điều tra khi xảy ra lỗi.

### Các action cần audit

``` text
USER_REGISTER
USER_LOGIN
USER_LOGOUT
PASSWORD_CHANGE
PAYMENT_CREATED
PAYMENT_SUCCESS
PAYMENT_FAILED
REFUND_REQUESTED
REFUND_COMPLETED
ROLE_CHANGED
PERMISSION_GRANTED
PERMISSION_REVOKED
```

### Audit Log

``` text
AuditLog
├── id
├── userId
├── action
├── resourceType
├── resourceId
├── ipAddress
├── userAgent
├── metadata
└── createdAt
```

Không ghi password, API key hoặc sensitive secret vào audit log.

------------------------------------------------------------------------

# 9. Notification

Notification chịu trách nhiệm thông báo các sự kiện liên quan đến
Backend 1.

### Các notification chính

-   Đăng ký tài khoản thành công.
-   Đăng nhập bất thường nếu có cơ chế phát hiện.
-   Thanh toán thành công.
-   Thanh toán thất bại.
-   Invoice được tạo.
-   Refund được chấp nhận/từ chối.
-   Quyền học được cấp.
-   Quyền học hết hạn.

### Kênh có thể hỗ trợ

``` text
In-App Notification
Email
Push Notification
```

------------------------------------------------------------------------

# 10. Ranh giới tích hợp với các Backend khác

Backend 1 **không sở hữu toàn bộ business logic của hệ thống học tập**.

Backend 1 chỉ cung cấp:

``` text
Identity
User
Payment
Invoice
Refund
Authorization
Audit
Notification
```

Các service khác chịu trách nhiệm về:

``` text
Course / Lesson
AI Text Generation
AI Video Generation
Learning Progress
Quiz / Exercise
Media / Video Storage
```

------------------------------------------------------------------------

# 11. Các điểm tích hợp bắt buộc

## 11.1 Cấp danh tính

Sau khi login thành công:

``` text
Backend 1
    │
    ▼
Issue Access Token / Identity
    │
    ▼
Other Services
```

Các service khác sử dụng identity để xác định:

``` text
userId
role
permissions
```

Không nên để mỗi service tự tạo user identity riêng.

------------------------------------------------------------------------

## 11.2 Xác nhận thanh toán

Khi payment thành công:

``` text
Payment Service
      │
      ▼
Payment SUCCESS
      │
      ▼
Publish PaymentConfirmed Event
      │
      ▼
Learning / Course Service
```

Event có thể có dạng:

``` json
{
  "eventType": "PaymentConfirmed",
  "paymentId": "PAY-001",
  "userId": 123,
  "productId": 456,
  "amount": 99000,
  "timestamp": "2026-09-15T10:00:00Z"
}
```

Service học tập sử dụng event này để cấp quyền truy cập nội dung tương
ứng.

------------------------------------------------------------------------

## 11.3 Phát sự kiện cấp quyền học

Sau khi xác nhận payment và xác định user đủ điều kiện:

``` text
Payment
   │
   ▼
Authorization
   │
   ▼
Grant Learning Access
   │
   ▼
Publish LearningAccessGranted
```

Ví dụ:

``` json
{
  "eventType": "LearningAccessGranted",
  "userId": 123,
  "courseId": 456,
  "accessType": "PREMIUM",
  "expiresAt": "2026-12-15T00:00:00Z"
}
```

Các service khác có thể consume event này để cập nhật quyền học.

------------------------------------------------------------------------

# 12. Nguyên tắc dữ liệu

Backend 1 phải là **source of truth** cho:

``` text
User
Payment
Invoice
Refund
Role
Permission
```

Các service khác không được trực tiếp chỉnh sửa database của Backend 1.

Thay vì:

``` text
Course Service
      │
      └── Direct access → User DB ❌
```

nên:

``` text
Course Service
      │
      ├── API → Backend 1
      │
      └── Event → Message Broker
```

------------------------------------------------------------------------

# 13. Idempotency

Đặc biệt quan trọng với Payment và Event.

Ví dụ payment gateway gửi callback 2 lần:

``` text
Payment SUCCESS
      │
      ├── Callback #1 → Process
      │
      └── Callback #2 → Ignore / Return existing result
```

Không được:

``` text
Callback #1 → Create Invoice
Callback #2 → Create another Invoice ❌
```

Cần đảm bảo:

``` text
paymentId / transactionId UNIQUE
invoice.paymentId UNIQUE
eventId UNIQUE
```

------------------------------------------------------------------------

# 14. Acceptance Criteria

## Authentication

-   [✓] User có thể register/login.
-   [✓] Password được hash.
-   [✓] JWT được cấp sau login.
-   [✓] API protected yêu cầu authentication.
-   [✓] Refresh token hoạt động.
-   [✓] Logout/revoke token hoạt động.

## User

-   [✓] User có thể xem/cập nhật profile.
-   [✓] User có thể đổi password.
-   [✓] Admin có thể quản lý user.
-   [✓] User bị disabled không thể đăng nhập.

## Payment

-   [✓] User có thể tạo payment.
-   [✓] Payment có trạng thái rõ ràng.
-   [✓] Callback/webhook được verify.
-   [✓] Payment callback có idempotency.
-   [✓] Payment SUCCESS phát event.

## Invoice

-   [✓] Invoice được tạo sau payment success.
-   [✓] Invoice number unique.
-   [✓] Không tạo duplicate invoice.
-   [✓] User xem được invoice của mình.

## Refund

-   [✓] User có thể request refund nếu đủ điều kiện.
-   [✓] Không refund quá số tiền đã thanh toán.
-   [✓] Không xử lý duplicate refund.
-   [✓] Refund status được tracking.

## Authorization

-   [✓] Role/permission được kiểm tra.
-   [✓] User chỉ truy cập resource được phép.
-   [✓] Premium content yêu cầu learning access.
-   [✓] Learning access được cấp sau payment hợp lệ.

## Audit

-   [✓] Các action quan trọng được ghi log.
-   [✓] Audit log không chứa secret/password.
-   [✓] Có thể truy vết payment/refund/permission actions.

## Notification

-   [✓] Payment success gửi notification.
-   [✓] Payment failed gửi notification.
-   [✓] Refund status gửi notification.
-   [✓] Learning access granted gửi notification.

## Integration

-   [✓] Token/identity có thể được các service khác verify.
-   [✓] PaymentConfirmed event được publish.
-   [✓] LearningAccessGranted event được publish.
-   [✓] Event có eventId để hỗ trợ idempotency.
-   [✓] Các service khác không truy cập trực tiếp database của Backend
    1.

------------------------------------------------------------------------

# 15. Definition of Done

Task được xem là hoàn thành khi:

1.  Các API chính đã implement.
2.  Authentication và Authorization hoạt động.
3.  Payment → Invoice → Learning Access flow hoạt động.
4.  Refund flow được xử lý.
5.  Audit log được ghi cho các action quan trọng.
6.  Notification được phát khi có event cần thiết.
7.  Payment và event processing có idempotency.
8.  API được document bằng Swagger/OpenAPI.
9.  Unit/Integration test cho các flow quan trọng.
10. Có README mô tả cách chạy Backend 1.
11. Không commit password, API key hoặc secret vào Git.
12. Các integration event/API được document để Backend khác có thể tích
    hợp.

------------------------------------------------------------------------

# 16. Tóm tắt trách nhiệm

> **Backend 1 chịu trách nhiệm xác định người dùng là ai, người dùng có
> quyền gì, người dùng đã thanh toán hay chưa, giao dịch có hợp lệ không
> và phát ra các sự kiện để các service khác thực hiện business logic
> tương ứng.**

### Integration Boundary

``` text
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
        Identity       PaymentConfirmed   Learning
        / JWT          Event              Access
                                          Granted
              │             │             │
              ▼             ▼             ▼
        Other Backend Services / Microservices
```
