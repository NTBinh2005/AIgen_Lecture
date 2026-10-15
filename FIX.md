# Các lỗi backend trên nhánh `dev`

> **Kết quả sửa source ngày 15/10/2026:** đã sửa #76, #77, #78; kiểm thử API bài giảng trên PostgreSQL 17 thật và kiểm thử webhook ký bằng secret thử nghiệm. Chi tiết phạm vi, kết quả và cách chạy lại: [báo cáo sửa lỗi 15/10](docs/BACKEND_FIX_REPORT_2026-10-15.md). Nội dung review tại commit `3ce0327` bên dưới được giữ để đối chiếu; chưa triển khai lên môi trường thật.

> Cập nhật 2026-10-15 tại commit `3ce0327` (PR #29–#30).
> Cách kiểm tra: build image backend, chạy trên **bản sao DB đang dùng** của máy FE (đã ở V15, đợt này không có migration mới);
> gọi lại bộ kiểm tra thông báo lỗi / phân trang / phân quyền, thử bộ lọc `GET /lectures`, giả lập webhook SePay bằng secret thật trong `.env`
> (hạn giao dịch đặt 1 phút để thử tác vụ tự huỷ), và mở các trang web của 3 vai trò bằng Chrome với dữ liệu thật.
>
> **Kết quả:** backend khởi động được; không thấy lỗi hồi quy về phân quyền. **Nhưng có 3 lỗi mới (#76–#78) do chính đợt này gây ra, trong đó #76 làm hỏng danh sách bài giảng của giáo viên và admin — chưa nên deploy bản này.**
>
> **Đã sửa và xác nhận trong đợt này, nên đã xoá khỏi danh sách:**
> #58 ("Không tìm thấy học sinh: 99999", meetingUrl, lớp trùng tên báo "Bạn đã có lớp tên …" — kể cả khác chữ hoa/thường, GV khác vẫn dùng được cùng tên;
> phân trang: `page=-1`, `size=500`, `page=abc` đều trả 400 tiếng Việt; sai mật khẩu, thiếu tiêu đề đều tiếng Việt).
> #75 chỉ đạt **khi có `title`**: lọc `teacherId` / `status` / `videoStatus` / `accessScope` kết hợp đúng, `status=ABC` → 400, GV lọc `teacherId` của GV khác bị chặn. Không có `title` thì lỗi 500 → xem #76.
>
> **Đã sửa / đã bỏ từ các đợt trước:** #1–#41, #42–#57, #59–#68, #70, #71, #73, #74, #16b, #17b.
> Các mục còn lại giữ nguyên số để dễ tham chiếu.

## Cần nhóm thống nhất

**72. Bật bắt buộc mua gói theo mặc định**
- `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM` mặc định `true`: học sinh chưa có gói không xem được bài giảng và không làm được bài; giáo viên chưa có gói không dùng được AI và render video.
- Máy FE đã cấp gói 365 ngày cho `htduong2005@gmail.com`, `teacher@edumind.dev`, `student@edumind.dev`; các tài khoản khác (kể cả tài khoản mới đăng ký) vẫn bị chặn tới khi mua gói hoặc được admin cấp (FE: Admin → Gói đăng ký → Người đăng ký → Cấp gói).
- Khi demo có thể đặt `APP_SUBSCRIPTIONS_ENFORCE_PREMIUM=false` trong `.env`.

## Lỗi backend còn lại

**76. `GET /lectures` trả 500 khi không có `title`** (mới, nghiêm trọng — do bản sửa #75)
- Gọi `GET /lectures` **không kèm `title`** (cả ADMIN lẫn TEACHER) → 500 "Lỗi hệ thống". Log: `ERROR: function lower(bytea) does not exist`.
- Nguyên nhân: query mới `searchForAdmin` / `searchOwnedOrShared` trong [LectureRepository.java](backend/src/main/java/com/example/demo/repository/LectureRepository.java) dùng `(:title IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :title, '%')))`; khi `title = null`, PostgreSQL nhận tham số kiểu `bytea` nên `lower()` lỗi. Test chạy trên H2 nên không bắt được.
- Ảnh hưởng (đã thấy trên Chrome): trang chủ giáo viên (bài giảng gần đây), **Giáo viên → Bài giảng** (luôn "0 bài giảng"), chọn bài giảng khi giao cho lớp / tạo quiz AI / tạo trình chiếu, **Admin → Bài giảng** khi chưa gõ tìm kiếm.
- Cách sửa gợi ý: ép kiểu `CAST(:title AS string)` ở cả 2 chỗ của mỗi query (cả `countQuery`), hoặc tạo sẵn chuỗi `%title%` (chữ thường) trong service rồi truyền vào; thêm test chạy trên PostgreSQL (Testcontainers) cho `GET /lectures` không tham số.

**77. Bộ lọc thông báo lỗi mới che mất thông báo tiếng Việt hợp lệ** (mới — do bản sửa #58)
- `clientMessage()` trong [GlobalExceptionHandler.java](backend/src/main/java/com/example/demo/common/exception/GlobalExceptionHandler.java) thay **toàn bộ** thông báo bằng câu chung nếu trong câu có một từ tiếng Anh trong danh sách (`session`, `quiz`, `student`, `user`, `file`, `title`, `only`, …), kể cả khi câu là tiếng Việt và từ đó đến từ **dữ liệu người dùng nhập**.
- Đã gặp:
  - Tạo buổi live trùng giờ → 400 "Dữ liệu yêu cầu không hợp lệ" (đúng ra: "Lịch buổi học bị trùng với session #…"). Giáo viên không biết vì sao không tạo được buổi học.
  - Huỷ buổi đã huỷ → "Dữ liệu yêu cầu không hợp lệ" (đúng ra: "Session đã bị hủy trước đó").
  - Tạo lớp trùng tên mà tên lớp có chữ "Quiz" (VD "Lop Quiz 1") → "Dữ liệu bị xung đột với trạng thái hiện tại"; tên khác thì báo đúng "Bạn đã có lớp tên …".
  - Còn nhiều câu khác sẽ bị che: "Student đã được điểm danh trong buổi này", "File export đã hết hạn", "Bạn không có quyền thao tác với quiz này", "User không phải là giáo viên: …", "Parent comment không thuộc bài giảng này", "Không thể cập nhật session đã ENDED hoặc CANCELLED"…
- Cách sửa gợi ý: bỏ danh sách từ khoá; chỉ thay bằng câu chung khi thông báo **không có dấu tiếng Việt**, và Việt hoá hẳn các câu còn trộn tiếng Anh ở chỗ ném lỗi (VD "session" → "buổi học"). Hiện còn câu không dấu ở `linkGoogleAccount`: "Email nay da duoc lien ket voi mot tai khoan Google khac".

**78. Chuyển khoản tới sau khi giao dịch đã tự huỷ thì mất tiền** (mới — do tác vụ tự huỷ sau 15 phút)
- Đã thử: tạo giao dịch SePay, đợi tác vụ tự huỷ chuyển sang `CANCELLED`, rồi gửi webhook hợp lệ đúng mã / đúng số tiền → backend trả 200 `{"success":true}`, giao dịch vẫn `CANCELLED`, **không kích hoạt gói**, không thông báo cho admin; chỉ ghi log mức INFO "Payment already processed".
- Thực tế dễ gặp: người dùng quét QR nhưng chuyển chậm hơn 15 phút (hoặc bấm "Huỷ" rồi vẫn chuyển). Tiền đã vào tài khoản nhưng người dùng không có gói và admin không biết để hoàn tiền.
- Cách sửa gợi ý: webhook đúng số tiền tới giao dịch `CANCELLED` do hết hạn thì vẫn chấp nhận và kích hoạt gói (hoặc chuyển trạng thái riêng "cần đối soát"), ghi audit và gửi thông báo cho ADMIN; ít nhất phải lưu lại webhook để admin tra cứu.

## Việc triển khai / vận hành còn lại

**69. Thanh toán SePay**
- Đã kiểm (15/10): QR VietQR đúng ngân hàng / số tài khoản / số tiền / nội dung `DH…`; webhook sai chữ ký hoặc quá 5 phút → 401, sai số tiền / sai tài khoản nhận → 400; webhook đúng → `SUCCESS`, kích hoạt gói, tạo hoá đơn; gửi lại cùng webhook không cộng gói lần hai; giao dịch `PENDING` tự chuyển `CANCELLED` sau thời gian cấu hình (đã thử với 1 phút). Lỗi khi chuyển khoản trễ → #78.
- Chạy ở `localhost` thì SePay không gọi được webhook về backend. Cần địa chỉ công khai (VD ngrok) cho `POST /api/payments/webhook/sepay` và khai báo trong trang quản trị SePay cùng `SEPAY_WEBHOOK_SECRET`.
- Hoàn tiền: xem đề xuất #79.

**79. Đề xuất: hoàn tiền "admin duyệt là xong", không phải đánh dấu tay** (tính năng mới, cần backend làm)
- **SePay không có API chuyển tiền ra / hoàn tiền** cho kiểu tích hợp đang dùng (QR VietQR + webhook biến động số dư). Đã tra tài liệu developer.sepay.vn (15/10): chỉ có tạo thanh toán, IPN / webhook, tra cứu đơn; cổng thanh toán SePay có `voidTransaction` (chỉ thẻ tín dụng) và `cancel` (huỷ đơn QR **chưa** thanh toán) — không có refund cho chuyển khoản đã nhận. Nên **không thể** "admin bấm duyệt → backend gọi SePay chuyển tiền trả lại".
- **Cách làm được ngay với SePay** (gần tự động, admin chỉ quét QR 1 lần):
  1. Học viên gửi yêu cầu hoàn tiền kèm **tài khoản nhận** (ngân hàng, số tài khoản, tên chủ tài khoản). → `RefundCreateRequest` thêm `bankName`, `bankAccount`, `accountHolder`; lưu vào `refunds`.
  2. Admin xem và bấm **Duyệt** (`REQUESTED → APPROVED`) như hiện tại; thêm thông báo cho học viên.
  3. Khi duyệt, backend trả về **ảnh VietQR chuyển tiền tới tài khoản học viên**: `{SEPAY_QR_BASE_URL}?acc=<stk HV>&bank=<ngân hàng HV>&amount=<số tiền>&des=HT<refundId>` (VD thêm `qrUrl` vào `RefundResponse`). Admin mở app ngân hàng của tài khoản nhận tiền SePay, quét QR → chuyển (đúng số tiền, đúng nội dung, không phải gõ tay).
  4. SePay cũng gửi webhook cho **giao dịch tiền ra** (`transferType = "out"`). Backend nhận webhook `out` có nội dung `HT<refundId>` và đúng số tiền → tự chuyển yêu cầu sang `COMPLETED`, chuyển giao dịch gốc sang `REFUNDED`, huỷ / thu hồi gói, ghi audit và gửi thông báo "Đã hoàn tiền" cho học viên. Hiện `handleSepayWebhook` đang từ chối mọi webhook `out` ("Chỉ chấp nhận giao dịch tiền vào") → cần tách nhánh xử lý.
  5. Cần bật gửi webhook cho giao dịch tiền ra trong cấu hình SePay (mặc định có thể chỉ gửi tiền vào); cấu hình tiền tố mã `HT` để SePay nhận diện.
  - Kết quả: admin không phải tự gõ số tài khoản / số tiền, không phải bấm "Đã hoàn tiền"; trạng thái tự cập nhật khi tiền thật sự đi ra.
- **Muốn tự động 100% (backend tự chuyển tiền sau khi admin duyệt)** thì phải dùng dịch vụ có **API chi hộ / payout**, không phải SePay: VD API chi hộ của ngân hàng (cần tài khoản doanh nghiệp và ký hợp đồng Open Banking), hoặc dịch vụ trung gian có API hoàn tiền như VietQR.vn (có "VietQR Refund API": kiểm tra giao dịch rồi gọi refund, ký checksum bằng secret key). Phương án này cần đăng ký dịch vụ mới, có phí, và phải giữ khoá API chuyển tiền rất cẩn thận.
- FE sẽ làm sau khi backend có API: form hoàn tiền thêm 3 ô tài khoản nhận; trang Admin → Thanh toán → Hoàn tiền hiện QR khi đã duyệt và tự đổi sang "Đã hoàn tiền" khi webhook về.

## Ghi chú

- **#41 (ghi hình / người tham gia buổi live) đã bỏ:** backend xác nhận không dùng các API `POST /live-sessions/{id}/recording/request`, `GET /live-sessions/{id}/recording`, `GET /live-sessions/{id}/participants` nữa. FE web đã gỡ phần "Bản ghi hình" và "Người tham gia phòng họp" khỏi hộp chi tiết buổi học (15/10); **app mobile cần gỡ tương tự** nếu đang gọi. Backend nên xoá hẳn các endpoint này cho gọn.

- **Phân trang chặt hơn (đợt này):** `size` > 100 hoặc `page` < 0 giờ trả 400 thay vì tự sửa. FE web đã đổi các chỗ gọi `size=200` / `size=500` (danh sách quiz, bài giảng) sang gộp nhiều trang 100 bản ghi; **app mobile cần kiểm tra** nếu đang gọi `size` lớn.
- **Đổi định dạng API (#74) — client khác cần cập nhật:** các endpoint sau giờ trả `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`) thay vì mảng: `/notifications`, `/audit-logs`, `/classes`, `/enrollments`, `/users`, `/invoices`, `/payments/admin`, `/refunds`, `/subscriptions`, `/exports`, `/lectures/{id}/comments`, `/students/me/quiz-history`, `/quiz-assignments/{id}/progress`. FE web đã cập nhật (15/10); **app mobile cần cập nhật** nếu đang gọi các endpoint này.
- **Lỗi thiếu gói:** client nên dựa vào `code: "SUBSCRIPTION_REQUIRED"` trong body lỗi 403 để nhắc người dùng mua gói.
- **Chưa kiểm được với AI thật:** các đợt gần đây Gemini trả 429 / 503, nên chưa xác nhận nội dung bài giảng khi chọn ngôn ngữ `en` (#36) và hành vi khi Gemini lỗi (#44).
- Video cũ lưu trên project Supabase `gbhhlacjwkztlbwzjsuo` không truy cập được (tên miền không phân giải), cần khôi phục hoặc upload lại nếu còn cần.
- Dữ liệu cũ của lỗi #2 (snapshot quiz thiếu đáp án, xuất bản ngày 03/10) chỉ có ở 3 quiz thử của tài khoản `fixcheck…`; đã xử lý trên máy FE ngày 15/10: lưu trữ cả 3 quiz, gỡ 2 bài giao chưa có ai làm, đóng 2 bài giao đã có bài làm (giữ lại 4 lượt làm cũ, điểm trắc nghiệm của chúng vẫn sai). Nếu DB trên máy chủ thật có quiz xuất bản trước bản sửa #2 thì cần xử lý tương tự.
