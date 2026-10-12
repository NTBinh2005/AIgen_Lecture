# Backend 2 API

Phạm vi này triển khai các yêu cầu Class, Enrollment, Schedule, Live Session và Attendance trong đặc tả v3.2. Tất cả API phía người dùng yêu cầu JWT; quyền sở hữu lớp tiếp tục được kiểm tra ở service, không chỉ ở SecurityConfig.

## Class

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| GET | `/api/classes` | authenticated | Danh sách theo vai trò |
| GET | `/api/classes/{classId}` | thành viên/giáo viên lớp/admin | Chi tiết lớp |
| GET | `/api/classes/my` | teacher | Lớp giáo viên chính hoặc đồng giảng dạy |
| POST | `/api/classes` | teacher/admin | Tạo lớp DRAFT |
| PATCH | `/api/classes/{classId}` | giáo viên lớp/admin | Cập nhật lớp |
| PATCH | `/api/classes/{classId}/activate` | giáo viên lớp/admin | DRAFT → ACTIVE |
| PATCH | `/api/classes/{classId}/close` | giáo viên lớp/admin | ACTIVE → CLOSED; chặn khi session OPEN/LIVE |
| PATCH | `/api/classes/{classId}/archive` | giáo viên lớp/admin | CLOSED → ARCHIVED |
| GET/POST | `/api/classes/{classId}/lectures` | theo quyền lớp | Xem/gán bài giảng đã publish |
| DELETE | `/api/classes/{classId}/lectures/{lectureId}` | giáo viên lớp/admin | Bỏ gán bài giảng |

## Enrollment

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/classes/{classId}/students` | Danh sách thành viên; chỉ giáo viên lớp/admin |
| GET | `/api/students/me/classes` | Lớp ACTIVE/COMPLETED của student hiện tại |
| POST | `/api/classes/{classId}/students` | Ghi danh một student |
| POST | `/api/classes/{classId}/students/bulk` | Ghi danh nhiều student, trả kết quả từng dòng |
| PATCH | `/api/classes/{classId}/students/{studentId}` | ACTIVE/SUSPENDED/COMPLETED/CANCELLED |
| DELETE | `/api/classes/{classId}/students/{studentId}` | Chuyển sang CANCELLED, không xóa lịch sử |
| POST | `/api/classes/self-enroll` | Student tự ghi danh bằng mã lớp |
| POST | `/api/internal/events/payment-succeeded` | Nhận `PaymentSucceeded` idempotent qua `X-Internal-Token` |

Ghi danh mới chỉ được thực hiện khi lớp ACTIVE, trong cửa sổ đăng ký và chưa vượt sức chứa. Trạng thái COMPLETED chỉ được đặt sau thời điểm kết thúc lớp.

## Schedule và Live Session

| Method | Endpoint | Chức năng |
|---|---|---|
| GET/POST | `/api/schedules` | Xem lịch theo quyền / tạo lịch |
| GET/PATCH/DELETE | `/api/schedules/{scheduleId}` | Xem, đổi, hủy lịch |
| GET | `/api/schedules/class/{classId}` | Lịch của lớp |
| GET | `/api/live-sessions/my` | Session người dùng được phép xem |
| POST | `/api/live-sessions` | Tạo ONLINE/OFFLINE session |
| PATCH | `/api/live-sessions/{sessionId}` | Đổi lịch và phát event thông báo |
| POST | `/api/live-sessions/{sessionId}/cancel` | Hủy và phát event thông báo |
| POST | `/api/live-sessions/{sessionId}/{open\|live\|end}` | Chuyển lifecycle |
| POST | `/api/live-sessions/{sessionId}/join` | Token tham gia ngắn hạn |
| GET/PATCH | `/api/live-sessions/{sessionId}/participants[/{participantId}]` | Danh sách, mute/remove/presenter |
| GET/POST/DELETE | `/api/live-sessions/{sessionId}/resources[...]` | Presentation/asset dùng trong buổi học |

## Attendance, QR, recording và webhook

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/live-sessions/{sessionId}/attendance` | Tổng hợp attendance theo quyền |
| POST | `/api/live-sessions/{sessionId}/attendance/manual` | Giáo viên điểm danh thủ công |
| POST | `/api/live-sessions/{sessionId}/qr/generate` | Tạo QR có thời hạn |
| POST | `/api/live-sessions/{sessionId}/qr/scan` | Student quét QR, chống ghi nhận trùng |
| POST | `/api/live-sessions/{sessionId}/recording/request` | Tạo yêu cầu recording và correlation ID |
| GET | `/api/live-sessions/{sessionId}/recording` | Metadata recording theo quyền lớp |
| POST | `/api/webhooks/live/provider` | Webhook HMAC-SHA256 trên raw body, xử lý idempotent |

Các thay đổi lịch/session, attendance, recording và enrollment từ thanh toán được ghi vào bảng `backend2_outbox_events` để worker tích hợp có thể phát sang notification hoặc hệ thống khác. Việc gọi API thực tế của nhà cung cấp video cần cấu hình/adapter riêng của nhà cung cấp; API hiện tại giữ contract trung lập và nhận webhook có xác thực.

## Kiểm thử

`Backend2ApiIntegrationTest` kiểm tra luồng DRAFT → ACTIVE → enrollment, visibility theo vai trò, chặn student tạo lịch, chặn giáo viên khác truy cập lớp/session và xác thực webhook bằng đúng raw request body.
