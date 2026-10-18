# Báo cáo sửa backend — 14/10/2026

Đã đọc `FIX.md`, đối chiếu source thực tế và sửa các lỗi xác nhận được trong repository. Các ghi nhận cũ trong `FIX.md` được giữ làm lịch sử; bảng dưới đây mô tả trạng thái source sau đợt sửa này.

**Kiểm chứng gần nhất (15/10):** biên dịch sạch bằng cấu hình Java 21 / Maven, chạy **58 test: 0 failure, 0 error**. `git diff --check` qua.

```powershell
cd backend
./mvnw.cmd clean test -Dspring.flyway.enabled=false -Dspring.jpa.show-sql=false -Dspring.mail.username=
```

Flyway được tắt trong lượt test H2; đây không phải cấu hình triển khai. Log kiểm chứng ở `backend/final-verification.log`. Có một lượt test bị class do IDE sinh ghi đè vào `target/classes`; đã kiểm tra lại bằng build sạch Maven thành công.

**Phạm vi xác nhận:** kiểm thử unit và tích hợp API/service với H2, provider Gemini giả lập, thanh toán dùng dữ liệu thử. Chưa chạy migration trên PostgreSQL thật, Docker container, Gemini thật, VNPay/MoMo thật hoặc render video thật. Docker daemon của máy hiện không chạy; Node/npm và dependency video-service chưa sẵn có để chạy TypeScript build. Không triển khai, sửa DB đang dùng, cấp khoá merchant hay thay `.env` thật.

| Mục trong FIX.md | Thay đổi source | Giới hạn còn lại |
|---|---|---|
| #25 | PATCH lớp phân biệt bỏ qua và explicit `null` cho `semester`, `endsAt`, `maxStudents`, thời gian mở/đóng ghi danh; giữ xử lý `description`. | — |
| #35, #62 | Compose truyền `VIDEO_PUBLIC_BASE_URL`, `APP_SWAGGER_ENABLED`; Google/ElevenLabs TTS dùng URL nội bộ riêng cho audio. | Video đã lưu trên project Supabase không truy cập được vẫn cần khôi phục/re-upload nguồn cũ. |
| #36 | Hai endpoint tạo bài giảng từ file nhận `language=auto\|vi\|en`; prompt và slide/TTS dùng lựa chọn này. Async job lưu language trong parameters và hash idempotency để retry giữ đúng yêu cầu. | Chưa kiểm chứng giọng đọc/video với provider thật. |
| #40 | Admin liệt kê/xem/tải hoá đơn của người khác; truy cập trái quyền trả 403. `access/check-user` chỉ cho ADMIN. Thông báo refund chuyển sang tiếng Việt. | Hoàn tiền thực tế qua merchant chưa tích hợp; admin vẫn phải chuyển tiền/đối soát trước khi đánh dấu COMPLETED. |
| #43 | XLSX/PDF đọc tên/email học sinh, lớp, quiz, từng lượt làm, trạng thái, điểm và câu trả lời thật. XLSX chống formula injection; PDF phân trang, nhúng Noto Sans để giữ tiếng Việt. Kiểm tra lại quyền lúc xử lý job và hạn tải file. | File phản ánh từng lượt làm trong phạm vi yêu cầu; không có dữ liệu bài làm thì file thể hiện không có kết quả. |
| #44 | Mặc định thiếu/sai khoá AI báo lỗi, không hoàn thành bằng slide mẫu. Không tự bù quiz thiếu bằng câu hỏi khuôn mẫu. Chế độ local fallback phải bật rõ ràng, trả `aiGenerated=false` và warning; async không lưu mẫu như nội dung AI. | Cần khoá/model có quyền truy cập thật. |
| #45 | Có endpoint collaborator details và tìm giáo viên; có restore phiên bản bài giảng thành draft mới, giữ snapshot đã publish; logout thu hồi cả refresh và access token bằng version lưu DB. | Logout hiện kết thúc mọi phiên của tài khoản. |
| #46 | Mọi phép lấy `LocalDateTime.now` trong backend dùng UTC rõ ràng, tài liệu OpenAPI ghi quy ước UTC. Thêm PATCH/DELETE bài quiz đã giao; explicit null xoá giờ mở/hạn nộp; cập nhật deadline lượt đang làm theo thời lượng. | DELETE bài đã có bài làm trả 409 để giữ kết quả; dùng PATCH `status=CLOSED`. Không chuyển JSON LocalDateTime hiện hữu sang dạng có Z để giữ tương thích FE. |
| #47 | Theo lựa chọn của người dùng: gói còn hiệu lực bắt buộc cho AI/render của giáo viên và nội dung/làm bài của học sinh. Admin được miễn, quyền lớp/ownership vẫn kiểm tra thêm. Lưu snapshot gói lúc tạo payment, ledger từng lần mua mới, giữ thời gian còn lại khi đổi gói, thu hồi quyền gói bị thay, trừ thời hạn khi refund, chống kích hoạt lặp, có API quản trị subscriptions. | Lịch sử mua trước migration chưa có ledger/snapshot đáng tin cậy; phải đối soát và điều chỉnh bằng API admin khi cần. Chưa có thu tiền tự động; response nói rõ PREPAID/automaticRenewal=false. Payment subscription không có mapping lớp nên không tự suy đoán planId thành classId để ghi danh. |
| #52 | Refresh token không hợp lệ khi logout trả 400; null/blank không gây lỗi hash 500. | — |
| #54 | Signals phải có body `{ "type": "TAB_SWITCH" }`; thiếu/rỗng/quá dài/sai loại trả 400. GET users nhận page/size, size 1–100; tìm học sinh/giáo viên cần 3–100 ký tự, tối đa 20 kết quả. | Web client gửi signal đã cập nhật tương ứng; mobile/client khác cần gửi body mới. |
| #58 | 401/403 dùng JSON có timestamp/status/error/message/path và tiếng Việt; sửa các lỗi được review; `my-result` kiểm tra quyền lớp trước khi tìm bài làm. Bộ xử lý lỗi chung giữ thông báo tiếng Việt chi tiết và thay chuỗi tiếng Anh cũ bằng thông báo tiếng Việt an toàn. | Chi tiết kỹ thuật đầy đủ chỉ ghi ở log, không trả thẳng cho client. |
| #60 | Thêm thông báo ghi danh, tham gia lớp, bài giảng/quiz mới, điểm, mở lại lượt, bài tự luận chờ chấm, live mới/hủy/sắp bắt đầu, bình luận/phản hồi, AI/video thành công/thất bại. Có targetType/targetId, nhắc hạn 24h cho người chưa nộp, nhắc live 15 phút, chống trùng. Có SSE `/api/notifications/stream`. | SSE dùng kết nối đang mở và bộ nhớ từng instance; nhiều instance cần broker/sticky routing. Mobile đóng app cần tích hợp FCM/APNs riêng. |
| #63 | Giới hạn đăng nhập theo email chuẩn hoá + địa chỉ peer, lưu DB để dùng chung giữa instance/khởi động lại; thao tác tăng đếm xử lý insert race, có cleanup. Không tin X-Forwarded-For tự gửi. | Khi deploy qua reverse proxy, phải cấu hình trusted proxy để peer thật được xác định đúng; cùng cặp email/IP vẫn chờ 5 phút sau 5 lần sai. |
| #64 | Cho tạo buổi bắt đầu ngay/đã bắt đầu, yêu cầu giờ kết thúc ở tương lai; scheduler kết thúc cả SCHEDULED đã quá giờ. | — |
| #67 | Migration chuẩn hoá email không trùng; tạo unique index lower(trim(email)). Các nhóm trùng giữ nguyên email/dữ liệu và được đánh dấu `emailAmbiguous`; không trả 500 khi đăng nhập. Admin gán email riêng để giải quyết. Có initializer index cho DB mới sau Hibernate tạo bảng. | Không tự gộp hoặc chọn chủ sở hữu email trùng. Tài khoản ambiguous không đăng nhập bằng email cho đến khi được phân biệt. |
| #68 | Dùng mặc định `gemini-3.8-flash` đồng nhất trong backend/Compose/env example; vẫn cho cấu hình `GEMINI_API_URL`. | 404 còn có thể do quyền/project/model của khoá thực tế. |
| #69 | Tích hợp SePay VietQR và webhook HMAC theo định dạng chính thức, chống replay/idempotency, kiểm tra tài khoản/số tiền, tự hủy `PENDING` quá hạn. Không đưa secret thật vào repo. | Cần HTTPS public và cấu hình webhook/secret thật. Hoàn tiền vẫn là quy trình chuyển khoản và đối soát thủ công của admin. |
| #75 | `GET /lectures` lọc kết hợp theo `title`, `teacherId`, `status`, `videoStatus`, `accessScope`; TEACHER không thể chọn chủ sở hữu khác. | FE có thể bổ sung các ô lọc tương ứng. |
| #16b | Counts và biểu đồ 12 tháng/7 ngày đọc DB; đếm bài giảng AI theo phiên bản đã tạo thành công; uptime là thời gian chạy JVM. Chi phí LLM trả null khi chưa có telemetry, thay vì số 0 giả. | Chi phí, tỷ lệ uptime, tương tác flashcard/note cần dữ liệu telemetry riêng; interaction series hiện dùng interaction_logs. |

Ngoài danh sách: sửa lỗi khai báo trùng biến `paidAmount` khiến source ban đầu không biên dịch. Callback kiểm tra cổng khớp payment, khoá transaction khi xử lý, kiểm tra số tiền một lần và kích hoạt subscription trong cùng transaction. JWT join-token của buổi live không được dùng như access token API dù cùng khoá ký.

**API/config cần nhóm FE/mobile cập nhật**

- Bật mặc định `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM=true` cho cả hai vai trò như người dùng đã chọn. Chưa có merchant key thì admin có thể dùng các API cấp/gia hạn gói dưới đây.
- `POST /api/admin/subscriptions`: `{ "userId": 7, "planId": 1, "days": 30 }`.
- `GET /api/subscriptions`: chỉ ADMIN; `POST /api/admin/subscriptions/{id}/extend`: `{ "days": 30 }`; `POST /api/admin/subscriptions/{id}/cancel`: huỷ ngay.
- `PATCH /api/quiz-assignments/{id}`: `openAt`, `closeAt`, `durationMinutes`, `maxAttempts`, `resultPolicy`, `status`. Không đổi thời lượng/số lượt khi đã có bài làm. `openAt`/`closeAt` nhận null để bỏ giới hạn.
- `POST /api/lectures/{id}/versions/{versionId}/restore`: tạo draft mới.
- `GET /api/lectures/{id}/collaborators/details` và `/api/presentations/{id}/collaborators/details`: thông tin user. Endpoint cũ vẫn trả ID để giữ tương thích.
- `GET /api/users/teachers?email=...`: TEACHER/ADMIN, tối thiểu 3 ký tự; `GET /api/users?page=0&size=50`: ADMIN, body vẫn là mảng.
- Tạo bài giảng multipart: thêm `language=vi`, `en` hoặc `auto`.
- Signals: body `{ "type": "TAB_SWITCH" }`. `session_resources.resourceId` phải là UUID thật.
- SSE cần Authorization Bearer, gửi các event `ready`, `notification`, `heartbeat`, timeout 120 giây để client kết nối lại; tối đa 5 kết nối/user. Logout đóng stream trên instance hiện tại.
- `NotificationResponse.targetId` là chuỗi, `targetType` chỉ loại đối tượng; `referenceId` giữ khoá chống trùng chi tiết.
- `UserResponse.emailAmbiguous=true` yêu cầu admin đổi email sang địa chỉ riêng và đã xác minh; mọi client vẫn phải quy đổi LocalDateTime UTC ↔ giờ địa phương.
- `StatisticsOverview.llmCostUsd` có thể null; `serverUptime` là ISO-8601 duration, không phải phần trăm SLA.

**Migration V8–V14**

V8 thêm token_version; V9 thêm snapshot/ledger mua gói; V10 xử lý email không phân biệt hoa thường; V11 đổi resourceId sang UUID; V12 cho phép loại thông báo học tập; V13 lưu bộ đếm đăng nhập; V14 chống ghi đè subscription đồng thời bằng row_version. Các migration kiểm tra bảng tồn tại để hỗ trợ DB trống trước Hibernate tạo schema, và không sửa V1–V7 đã chạy.

V11 giữ các link resource số cũ không thể trỏ đến UUID vào `legacy_session_resource_links` trước khi bỏ chúng khỏi danh sách hoạt động. Cần đối chiếu và thêm lại bằng UUID tài nguyên thật. V10 giữ nguyên các tài khoản/email trùng và quan hệ liên quan, không xoá/merge người dùng. Ledger/snapshot mới không tự dựng lại giá, tên gói hoặc thời lượng lúc mua trong lịch sử thiếu dữ liệu.

Cần chạy V8–V14 trên bản sao PostgreSQL có dữ liệu trước khi triển khai. Backend đã build được, nhưng việc chạy thành công migration trên PostgreSQL thật vẫn chưa được xác nhận ở môi trường hiện tại.

**Nguồn model và font**

Model mặc định tham chiếu tài liệu chính thức: https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash . Noto Sans được nhúng theo SIL Open Font License 1.1; nguồn và giấy phép ở `backend/src/main/resources/fonts/README.txt` và `LICENSE.txt`.
