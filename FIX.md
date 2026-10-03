
# Các lỗi backend trên nhánh `dev`

## Nghiêm trọng: hệ thống không chạy được hoặc mất chức năng

**1. Backend crash ngay khi khởi động nếu không có biến `GOOGLE_OAUTH_CLIENT_ID`**
- Vị trí: `application.properties:57` có dòng `google.oauth.client-id=${GOOGLE_OAUTH_CLIENT_ID}`, không có giá trị mặc định. `docker-compose.yml` cũng không truyền biến này.
- Hiện tượng: `PlaceholderResolutionException: Could not resolve placeholder 'GOOGLE_OAUTH_CLIENT_ID'`, container tự khởi động lại liên tục.
- Cách sửa: đổi thành `${GOOGLE_OAUTH_CLIENT_ID:}` (thêm dấu `:`).

**2. Luồng render video bị mất**
- Commit `03bca72` đã gỡ đoạn gọi `video-service` (`POST /generate-video`) và job polling trạng thái. Hiện không còn chỗ nào trong backend gọi video-service.
- Hậu quả: `videoStatus` của mọi bài giảng mãi là `PENDING`, và endpoint `GET /api/lectures/{id}/video-status` không còn ý nghĩa.
- Cần quyết định video được render ở bước nào (ví dụ khi publish), sau đó nối lại.

**3. Gemini quá tải (503) làm job tạo bài giảng thất bại ngay**
- Vị trí: `LlmServiceImpl.java:70` và `:162` chỉ gọi Gemini đúng một lần, không thử lại.
- Đã tái hiện: Gemini trả `503 "This model is currently experiencing high demand"` cho prompt dài, job FAILED ở 35% sau khoảng 11 giây. Hai lần chạy liên tiếp đều thất bại.
- Cách sửa: thử lại với thời gian chờ tăng dần (ví dụ 3, 6, 12 giây) khi gặp 429/500/503. Ngoài ra nên cho cấu hình model qua biến môi trường để đổi model khi model mặc định quá tải.

**4. Thêm cột bắt buộc (NOT NULL) làm hỏng DB đang có dữ liệu**
- Vị trí: `ddl-auto=update` (`application.properties:11`) kết hợp các cột NOT NULL mới của bảng `lectures` (`status`, `access_scope`, `business_id`, `row_version`, `updated_at`...).
- Hiện tượng: DB đã có dữ liệu từ bản cũ báo `column "status" of relation "lectures" contains null values`, sau đó lỗi tiếp `column "status" does not exist`.
- Ảnh hưởng: mọi thành viên có DB local cũ, và cả DB trên server nếu đã deploy. Cần migration (Flyway/Liquibase) hoặc giá trị mặc định cho các cột mới.

## Thiếu API: frontend không làm đủ được luồng

**5. Không có API sửa slide**
- `PATCH /api/lectures/{id}` (`LectureUpdateRequest`) chỉ nhận `title`, `content` và `accessScope`, không nhận `slides`.
- Giáo viên muốn sửa slide do AI tạo thì frontend phải tạo một bài giảng mới rồi lưu trữ bản cũ.
- Đề xuất: cho `LectureUpdateRequest` nhận thêm `slides` (lưu vào `slideContent` của version nháp).

**6. Không có API lưu `videoUrl` hay quiz**
- Bảng `ai_elements` không còn được ghi ở đâu cả. `GET /api/lectures/{id}/quizzes` (`InteractionServiceImpl:41`) vì vậy luôn trả về rỗng.
- `LectureCreateRequest.quizzes` vẫn còn trong DTO nhưng bị bỏ qua mà không báo lỗi.

**7. Danh sách bài giảng của học sinh luôn trống**
- `getAllLecturesForStudent` (`LectureServiceImpl:111`) trả `Page.empty()` cho tới khi có Backend 2 (phần giao bài giảng cho lớp). Đây là do thiết kế, nhưng cần biết Backend 2 khi nào xong.
- Học sinh gọi `GET /lectures/{id}` thì luôn nhận 403.

## Xử lý lỗi và bảo mật

**8. Lỗi của người dùng trả về 500 và lộ thông tin nội bộ**
- Vị trí: `GlobalExceptionHandler:80` không có handler cho `MissingServletRequestParameterException` hay `MissingRequestHeaderException`.
- Ví dụ: gọi `from-file` thiếu `title` thì nhận `500 "Lỗi hệ thống: Required request parameter 'title'..."`. Đúng ra phải là 400.
- Handler chung đang trả thẳng `ex.getMessage()` về cho client.

**9. Log của job không có nguyên nhân lỗi**
- Vị trí: `LectureGenerationProcessor.java:53` chỉ ghi `exception.getClass().getSimpleName()`.
- Lỗi 503 ở mục 3 chỉ tìm ra được sau khi sửa tạm thành `log.warn("...", jobId, exception)`.

**10. Gemini API key nằm trong URL**
- Vị trí: `LlmServiceImpl.java:67` và `:159` (`?key=`).
- Key có thể xuất hiện trong log hoặc thông báo lỗi. Trên `main` key đã từng bị hiện ra trên giao diện. Nên gửi qua header `x-goog-api-key`.

**11. Secret mặc định nằm trong code**
- `jwt.secret` (dòng 37) và `live.webhook.secret=change-me-in-production` (dòng 79).
- Nên bắt buộc đặt qua biến môi trường khi deploy.

## Cấu hình chưa khớp

**12. Đổi cổng backend nhưng app mobile chưa cập nhật**
- `docker-compose.yml` đổi backend sang `8081:8080`, nhưng app mobile vẫn ghi cố định `10.0.2.2:8080` (`mobile/lib/services/auth_service.dart:7`, `lecture_service.dart:7`).

**13. Cấu hình không khớp với SRS và Docker**
- Datasource local trỏ `127.0.0.1:5432` (`application.properties:5`), trong khi compose publish Postgres ở cổng `5436`.
- Giới hạn upload là 500MB (dòng 47), trong khi SRS (BR-01) quy định 20MB.

---
