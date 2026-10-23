# Hoàn tiền SePay bằng QR và webhook tiền ra (#79)

Backend tạo QR để admin chuyển khoản; chỉ webhook tiền ra hợp lệ mới xác nhận đã hoàn tiền. Backend không tự ra lệnh chuyển tiền ngân hàng. Giao diện FE đã được triển khai ngày 16/10/2026; chưa cấu hình tài khoản SePay thật.

## API cho FE

Tất cả API refund yêu cầu Bearer token. Học viên chỉ thao tác yêu cầu của mình; ADMIN được xem toàn bộ và duyệt/từ chối.

**Tạo yêu cầu:** `POST /api/refunds`

```json
{
  "paymentId": 123,
  "reason": "Đề nghị hoàn học phí",
  "bankName": "Vietcombank",
  "bankAccount": "0012345678",
  "accountHolder": "NGUYEN VAN A"
}
```

Ba trường tài khoản đều bắt buộc. `bankName` là mã BIN/tên viết tắt VietQR gồm 2–30 ký tự chữ/số (ví dụ `970436`, `Vietcombank`, `BIDV`); `bankAccount` là chuỗi 4–34 ký tự chữ/số, giữ nguyên số 0 đầu. `accountHolder` tối đa 150 ký tự, không chứa ký tự điều khiển. Backend lấy số tiền đầy đủ từ giao dịch SUCCESS của người yêu cầu, không lấy số tiền client gửi. Mỗi payment có tối đa một yêu cầu theo luồng hiện tại; thao tác tạo đồng thời được khóa theo payment.

**Duyệt:** `PATCH /api/refunds/{id}/status`, chỉ ADMIN:

```json
{"status": "APPROVED"}
```

Hoặc `{"status":"REJECTED"}` khi yêu cầu còn REQUESTED. Duyệt chưa thu hồi gói và chưa đánh dấu giao dịch REFUNDED. Phản hồi bổ sung các trường sau, bên cạnh các trường cũ:

```json
{
  "refundId": 12,
  "status": "APPROVED",
  "bankName": "Vietcombank",
  "bankAccount": "0012345678",
  "accountHolder": "NGUYEN VAN A",
  "refundCode": "HT12",
  "qrUrl": "https://vietqr.app/img?acc=0012345678&bank=Vietcombank&amount=150000&des=HT12&template=compact&showinfo=true&holder=NGUYEN+VAN+A",
  "sepayWebhookId": null,
  "sepayReferenceCode": null
}
```

`qrUrl` chỉ có cho refund SEPAY đã APPROVED và đủ thông tin nhận tiền; trạng thái khác trả null. Admin đối chiếu người nhận/số tiền trong app ngân hàng, quét QR và chuyển từ tài khoản nguồn đã cấu hình. Giữ đúng nội dung `refundCode`.

**Đọc trạng thái:** `GET /api/refunds/{id}` (chủ yêu cầu/ADMIN), `GET /api/refunds/my-refunds` (mảng của người đang đăng nhập), `GET /api/refunds?page=0&size=20` (Page, chỉ ADMIN). FE có thể gọi lại API khi đang chờ để cập nhật `COMPLETED`; không cần nút đánh dấu đã chuyển tiền. `sepayWebhookId` và `sepayReferenceCode` được trả sau khi đối soát thành công; raw webhook lưu phía server, không trả trong response.

**Sửa tài khoản trước duyệt:** `PATCH /api/refunds/{id}/bank-account`, chỉ chủ yêu cầu, body gồm `bankName`, `bankAccount`, `accountHolder`. Chỉ được sửa khi REQUESTED. Đặc biệt, bản ghi APPROVED cũ chưa có tài khoản được phép bổ sung; backend đưa lại REQUESTED để admin duyệt đúng thông tin mới. Đã APPROVED với tài khoản đầy đủ thì không được sửa, tránh đổi đích chuyển tiền sau khi admin duyệt.

`PATCH .../status` với COMPLETED bị chặn cho mọi payment SEPAY. Các cổng legacy vẫn giữ quy trình đối soát thủ công cũ.

## Webhook và đối soát

Dùng endpoint hiện có: `POST /api/payments/webhook/sepay`, HMAC-SHA256 trên raw body với `X-SePay-Timestamp` và `X-SePay-Signature`. Nhánh `in` tiếp tục xác nhận thanh toán; nhánh `out` xử lý hoàn tiền.

```json
{
  "id": 92705,
  "accountNumber": "TAI_KHOAN_NGUON_THUC",
  "code": "HT12",
  "content": "HT12 hoan hoc phi",
  "transferType": "out",
  "transferAmount": 150000,
  "referenceCode": "FT24012345679"
}
```

Backend kiểm tra chữ ký/thời hạn, ID dương, số tiền nguyên VND dương, đúng tài khoản nguồn, mã HT hợp lệ, refund APPROVED, payment gốc SEPAY/SUCCESS, số tiền bằng refund và payment. Nếu `code` null/rỗng, có thể nhận mã độc lập từ `content`; mã mơ hồ, nhiều refund ID, mã bị nối thêm ký tự hoặc code mâu thuẫn với content bị từ chối. Không nhận mã hoàn tiền từ webhook `in`.

Một transaction DB thực hiện: refund → COMPLETED; payment → REFUNDED; thu hồi credit của lần mua tương ứng; lưu ID/reference/raw payload; lưu dấu chống lặp `SEPAY:<id>`; ghi audit REFUND_COMPLETED. Nếu một bước lưu lỗi, toàn bộ rollback và webhook có thể retry. Thời hạn mua từ lần khác được giữ. Thông báo duyệt/từ chối/hoàn tiền được gửi sau commit qua hệ thống thông báo hiện tại.

Retry cùng ID cho refund đã hoàn trả 200 `{"success":true}`, không trừ quyền, ghi audit hoặc thông báo lần hai. Webhook đồng thời được khóa theo thứ tự payment rồi refund. Một ID ngân hàng không thể dùng cho refund khác. Giao dịch tiền ra có ID mới cho refund đã hoàn bị từ chối để đối soát, không tự coi là retry.

Payload chuẩn không có trường tài khoản **người nhận tiền ra**, nên backend xác minh tài khoản nguồn + mã + số tiền, không thể chứng minh tài khoản đích chỉ từ webhook này. Admin phải kiểm tra người nhận trên màn hình ngân hàng trước khi xác nhận chuyển. [Định dạng payload SePay](https://developer.sepay.vn/en/sepay-webhooks/tich-hop-webhook).

## Cấu hình triển khai

- Chạy migration **V16** trước khi đưa API mới vào sử dụng. Migration thêm 6 cột nullable để giữ nguyên yêu cầu cũ và unique index cho `sepay_webhook_id`. DB trống do Hibernate tạo sẽ có các trường tương ứng từ entity. Không tự dựng tài khoản ngân hàng cho dữ liệu cũ.
- Thiết lập endpoint HTTPS công khai, HMAC secret hiện có. Trên SePay chọn sự kiện **Money out** (hoặc Both), đúng tài khoản nguồn; cấu hình nhận diện tiền tố **HT**. Giữ webhook/mã DH cho tiền vào. Nếu dùng lọc tiền tố/“bỏ qua giao dịch không có mã”, phải đảm bảo SePay nhận diện được mã HT trước khi lọc; fallback từ content không thể khôi phục webhook mà SePay đã bỏ qua.
- `SEPAY_REFUND_SOURCE_ACCOUNT`: tài khoản thực mà admin dùng để chuyển trả tiền. Nếu bỏ trống, backend dùng `SEPAY_WEBHOOK_ACCOUNT_NUMBER`, rồi mới tới `SEPAY_BANK_ACCOUNT`. Nên đặt rõ khi hệ thống dùng tài khoản định danh/VA. Biến này đã được truyền qua Compose và có trong `.env.example`.
- QR dùng `SEPAY_QR_BASE_URL` hiện có. Không cần đăng ký dịch vụ payout hoặc cung cấp khóa API chuyển tiền.
- **Giới hạn provider:** tài liệu SePay hiện ghi Money out chỉ hỗ trợ Sacombank, TPBank, VietinBank và cần TKP (VA theo nội dung), không dùng official VA. Kiểm tra đúng ngân hàng/loại kết nối của tài khoản thực trước khi sử dụng. Nếu kết nối không phát webhook tiền ra, yêu cầu sẽ vẫn APPROVED; backend không giả lập đã hoàn tiền. [Cấu hình và giới hạn Money out](https://developer.sepay.vn/en/sepay-webhooks/tao-webhook).
- Thử một giao dịch ngân hàng thật có giá trị nhỏ sau khi cấu hình. Đợt phát triển này chỉ ký/gửi webhook thử nghiệm vào backend; không chuyển tiền thật, không thay `.env` hoặc tài khoản SePay đang dùng.

## Kiểm chứng

Kết quả cuối: **92 test, 0 failure, 0 error, 0 skipped**; gồm 18 trường hợp hoàn tiền và 2 trường hợp bài giảng chạy trên PostgreSQL 17 thật. `git diff --check` qua. Container/DB thử nghiệm đã được gỡ sau khi kiểm tra. Chưa commit, push hoặc deploy.

`RefundFlowIntegrationTest` chạy mặc định trên H2; có thể trỏ sang PostgreSQL **chỉ dùng cho test** bằng `TEST_REFUND_DB_URL`, `TEST_REFUND_DB_DRIVER=org.postgresql.Driver`, `TEST_REFUND_DB_USER`, `TEST_REFUND_DB_PASSWORD`. Test dùng Hibernate create-drop và tắt Flyway, sẽ tạo/xóa bảng trong DB đó.

```powershell
cd backend
./mvnw.cmd clean test '-Dspring.flyway.enabled=false' '-Dspring.jpa.show-sql=false' '-Dspring.mail.username='
```

Đã chạy luồng hoàn tiền trên H2 và PostgreSQL 17 thật: dữ liệu nhận/QR, quyền sở hữu và admin, yêu cầu chưa duyệt/bị từ chối, sai tiền/tài khoản/chữ ký/mã/hướng, thiếu code, bản ghi legacy, nhiều lần mua gói, trùng/đồng thời webhook và yêu cầu hoàn tiền. Có kiểm tra lỗi ghi DB để xác minh rollback cả trạng thái/quyền/audit/thông báo và retry thành công.

Migration V16 đã chạy bằng psql trên bảng refund cũ giả lập trong PostgreSQL 17, kiểm tra thêm đủ 6 cột, giữ nguyên bản ghi cũ và chạy lại an toàn. Chưa chạy migration trên bản sao DB production. Log kiểm thử: `backend/verification-refund-79.log` (H2), `backend/verification-refund-79-full.log` (toàn bộ backend, refund + lecture trên PostgreSQL).
