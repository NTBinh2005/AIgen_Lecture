# Rà soát source backend — 15/10/2026

Đã kiểm kê source Java, rà soát cấu hình bảo mật, controller, repository, các nhánh nghiệp vụ và tác vụ chạy nền, rồi sửa các lỗi xác nhận được. Đây là một lượt rà soát source và kiểm thử hồi quy, không phải chứng nhận mọi tình huống vận hành đều không có lỗi.

## Các lỗi đã sửa

| Nhóm | Lỗi trước sửa | Hành vi sau sửa |
|---|---|---|
| Chính sách kết quả | API lịch sử trả điểm thẳng, bỏ qua NEVER/AFTER_GRADING/AFTER_CLOSE_AT. | Lịch sử và chi tiết học sinh áp dụng cùng chính sách; màn hình tiến độ giáo viên vẫn có điểm. |
| Hạn làm bài | Lượt mới chỉ dùng thời lượng, có thể vượt closeAt; lượt cũ không deadline vẫn lưu đáp án khi bài đã đóng. | Deadline lấy thời điểm sớm hơn giữa thời lượng và closeAt; autosave kiểm tra trạng thái và hạn bài giao. |
| Đáp án | Có thể gửi questionId không thuộc snapshot lượt làm. | Từ chối trước khi tạo/sửa đáp án. |
| Gọi đồng thời | Hai lần start có thể đụng unique constraint; hai autosave có thể tạo hai đáp án; autosave/nộp bài/đổi hạn không đồng bộ. | Khóa assignment khi tạo lượt và cập nhật bài giao, khóa attempt cho autosave/nộp/mở lại/chấm điểm/đổi hạn/tăng tín hiệu; giữ kiểm tra answerVersion. |
| Mở lại lượt làm | Vẫn giữ điểm và thông tin chấm cũ. | Xóa điểm tổng, điểm từng câu, gợi ý AI và thông tin chấm; tính lại deadline theo giờ đóng bài. |
| Chấm điểm | Có thể chấm lượt IN_PROGRESS; NaN lọt qua so sánh giới hạn; finalize có thể bỏ qua câu tự luận chưa chấm. | Chỉ chấm lượt đã nộp; điểm hữu hạn trong giới hạn câu hỏi; finalize vẫn nhận map rỗng nhưng yêu cầu các câu tự luận có nội dung đã được chấm. Quiz cũng chặn points NaN/Infinity. |
| Gợi ý điểm AI | CompletableFuture giữ entity cũ, có thể save đè đáp án/điểm mới và chạy trước commit. | Event sau commit, executor có giới hạn; gọi AI ngoài transaction; khi lưu khóa lượt và đối chiếu phiên bản, nội dung, trạng thái và điểm giáo viên. Điểm gợi ý hữu hạn, không vượt điểm tối đa. |
| Refresh token | Hai request có thể cùng sử dụng refresh token trước khi token bị thu hồi. | Khóa user rồi token trong transaction, kiểm tra trạng thái mới trước rotation; một token chỉ rotation thành công một lần. |
| Thu hồi phiên | Đổi mật khẩu/reset mật khẩu không thu hồi toàn bộ JWT/refresh; khóa rồi mở lại tài khoản có thể làm token cũ hoạt động trở lại. | Tăng tokenVersion và thu hồi refresh khi đổi/reset mật khẩu, thay quyền/trạng thái hoặc vô hiệu hóa; đóng SSE của user trên instance hiện tại sau commit. |
| ADMIN cuối cùng | Đếm admin trước thay đổi không khóa tập bản ghi, có thể đua giữa hai thao tác quản trị. | Khóa ADMIN hoạt động theo thứ tự userId trước kiểm tra và thay đổi tài khoản. |
| OTP | Verify theo get/remove có thể dùng mã hai lần; cập nhật số lần sai không nguyên tử; gửi lại không có cooldown. | Consume/tăng lần sai nguyên tử theo số điện thoại; cooldown 60 giây mặc định, lưu riêng để không bị mất khi mã đã dùng/hết lượt thử. Gửi SMS lỗi thì bỏ challenge tương ứng. |
| Cấu hình SMS | Thiếu Twilio vẫn trả thành công và in OTP ra log. | Báo dịch vụ chưa cấu hình; không giả lập đã gửi hoặc ghi mã OTP ra log. |
| Email hồ sơ | Kiểm tra trùng email phân biệt hoa/thường, không thống nhất với đăng nhập và index PostgreSQL. | Chuẩn hóa email, kiểm tra không phân biệt hoa/thường, xử lý cờ ambiguous khi cập nhật. |
| Cập nhật User đồng thời | Thay thông tin không liên quan có thể ghi đè passwordHash/tokenVersion từ bản entity cũ. | Khóa cập nhật hồ sơ; User dùng DynamicUpdate để chỉ ghi cột thực sự thay đổi. Có kiểm thử hai transaction cho cập nhật liên kết Google và đổi mật khẩu. |
| Ghi danh/sĩ số | Check-then-insert cho self-enroll/enroll có thể vượt sĩ số; PATCH CANCELLED → ACTIVE/SUSPENDED bỏ qua điều kiện. | Các đường ghi danh khóa cùng hàng class, kiểm tra lại cửa sổ, trạng thái lớp, chỗ trống và học sinh hoạt động. Event thanh toán nội bộ cũng khóa class/kiểm tra lại idempotency. |
| Buổi học | Hai request đồng thời có thể cùng vượt kiểm tra trùng lịch. PATCH sang OFFLINE không bắt địa điểm hoặc cho giờ kết thúc đã qua. | Create/reschedule khóa class; PATCH kiểm tra trạng thái kết quả. Các thao tác lifecycle/webhook cùng khóa session để tránh ghi đè trạng thái mới. |
| Điểm danh | Hai thao tác manual/QR có thể cùng vượt kiểm tra đã điểm danh. | Khóa cùng hàng session trước kiểm tra và ghi; online join cũng dùng khóa session. |
| Bình luận | DELETE bỏ qua lectureId trên URL, có thể xóa bình luận thuộc bài khác. | Kiểm tra quan hệ lecture/comment trước khi kiểm tra quyền và xóa; sai quan hệ trả 404. |
| Xuất bảng điểm | File đã tạo vẫn tải được sau khi giáo viên bị gỡ khỏi lớp; nhiều worker có thể xử lý cùng job. | Kiểm tra quyền lớp tại thời điểm download; khóa job khi worker bắt đầu, chỉ xử lý QUEUED một lần. |

Không thay chính sách mua gói mặc định, không đổi các hợp đồng response, không sửa DB ứng dụng hoặc `.env` thật. Đợt này không cần migration mới; migration V16 của hoàn tiền vẫn là thay đổi từ đợt #79.

## Kiểm chứng

- Bộ test đầy đủ: **118 test, 0 failure, 0 error, 0 skipped**, gồm 26 trường hợp audit mới, 18 trường hợp hoàn tiền và 2 trường hợp bài giảng trên PostgreSQL 17 thật. Các bộ tích hợp/unit còn lại dùng H2 hoặc provider giả lập như trước.
- Chạy riêng 26 trường hợp audit trên H2 để kiểm tra tương thích.
- `git diff --check` qua.
- API smoke test không bộ lọc: lectures, classes, enrollments, users, audit-logs, invoices, payments/admin, refunds, subscriptions, notifications, schedules, live-sessions/my, exports, plans, admin/plans.
- Có kiểm thử đồng thời thật với hai thread cho refresh, start, autosave, self-enroll, tạo lịch live, điểm danh và OTP. Kiểm thử quyền download lại file sau khi đổi giáo viên quản lý.
- Kiểm thử đổi/reset mật khẩu xác nhận access token và refresh token cũ bị từ chối; đổi mật khẩu đóng stream SSE đang mở.

Log toàn bộ: `backend/backend-source-audit.log`; H2: `backend/backend-source-audit-h2.log`; báo cáo từng test: `backend/target-audit/surefire-reports/` (lượt gần nhất).

Lượt đầu gặp class do IDE sinh có lỗi Lombok trong `target/classes`, dù source Maven biên dịch được. Thêm cấu hình tùy chọn `backend.build.directory`, mặc định vẫn `target`, để kiểm chứng trong `target-audit` tách khỏi output IDE:

```powershell
cd backend
./mvnw.cmd clean test '-Dbackend.build.directory=target-audit' '-Dspring.flyway.enabled=false' '-Dspring.jpa.show-sql=false' '-Dspring.mail.username='
```

Để chạy lại audit bằng PostgreSQL, chỉ trỏ các biến sau đến DB **có thể xóa**: `TEST_AUDIT_DB_URL`, `TEST_AUDIT_DB_DRIVER=org.postgresql.Driver`, `TEST_AUDIT_DB_USER`, `TEST_AUDIT_DB_PASSWORD`. Các test dùng Hibernate create-drop. Cấu hình DB riêng của refund và lecture giữ như tài liệu các đợt trước; không đặt biến PostgreSQL của lecture thì bộ đó được bỏ qua. Flyway chỉ tắt cho test, không phải hướng dẫn tắt Flyway khi deploy.

## Các điểm FE/vận hành cần lưu ý

- Sau khi đổi/reset mật khẩu hoặc đổi vai trò/trạng thái tài khoản, người dùng phải đăng nhập lại. SSE trên instance hiện tại được đóng sau commit; nhiều instance vẫn cần broker/cơ chế phát sự kiện giữa các instance như giới hạn kiến trúc SSE hiện có.
- Điểm trong lịch sử có thể null theo ResultPolicy; FE không nên tự coi null là điểm 0.
- Autosave với questionId sai hoặc bài đã đóng bị từ chối; reopen xóa điểm cũ. Muốn học sinh làm lại bài đã quá closeAt, giáo viên cần mở/gia hạn bài giao trước.
- Chưa cấu hình Twilio thì đăng nhập SMS không hoạt động. Cooldown OTP cấu hình bằng `auth.sms.otp-resend-cooldown-seconds` (mặc định 60). Challenge/cooldown OTP hiện lưu trong bộ nhớ của một instance; restart làm mất challenge, nhiều instance cần kho dùng chung hoặc định tuyến phiên phù hợp.
- Chưa gọi Gemini/Twilio thật, render video thật, chuyển khoản thật hoặc kiểm thử trình duyệt trong đợt này. Các kiểm thử provider dùng giả lập; không thay dữ liệu lịch sử nghi có lỗi trên DB thật.
- Không commit, push hoặc deploy. Container/DB PostgreSQL thử nghiệm được gỡ sau khi hoàn tất kiểm chứng.
