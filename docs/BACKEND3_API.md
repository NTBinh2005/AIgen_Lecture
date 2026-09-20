# Backend 3 API

Phạm vi tài liệu này chỉ gồm các module do Backend 3 sở hữu: Lecture, Asset,
GenerationJob và Presentation. Tất cả API yêu cầu JWT. API quản lý nội dung yêu
cầu `ROLE_TEACHER` hoặc `ROLE_ADMIN`; chủ sở hữu, cộng tác viên và admin được
kiểm tra thêm ở tầng service.

## Quy ước chung

- Bản ghi nghiệp vụ mới dùng UUID. `Lecture` vẫn giữ khóa `Long` cũ để tương
  thích với dữ liệu và API hiện tại, đồng thời có thêm `businessId` UUID.
- Thời gian được lưu theo UTC.
- Xóa là archive/soft delete; version và lịch sử job không bị xóa.
- Các API tạo nội dung bất đồng bộ bắt buộc header `Idempotency-Key` và trả về
  HTTP `202` cùng ID nội dung và `jobId`.
- Lỗi job trả về thông điệp an toàn, không lộ stack trace hay phản hồi thô từ
  dịch vụ AI.

## Lecture

| Method | Path | Mô tả |
| --- | --- | --- |
| POST | `/api/lectures` | Tạo bài giảng thủ công ở trạng thái `DRAFT` |
| POST | `/api/lectures/from-file` | Upload PDF/DOCX/PPTX và tạo bài giảng bất đồng bộ |
| GET | `/api/lectures` | Danh sách bài giảng sở hữu/chia sẻ, có phân trang và lọc title |
| GET | `/api/lectures/{id}` | Chi tiết bài giảng theo quyền truy cập |
| GET | `/api/lectures/{id}/versions` | Danh sách version bất biến |
| GET | `/api/lectures/{id}/versions/{versionId}` | Chi tiết một version |
| PATCH | `/api/lectures/{id}` | Sửa nội dung; sửa bản đã publish sẽ tạo draft version mới |
| POST | `/api/lectures/{id}/publish` | Publish current version hoặc `versionId` chỉ định |
| GET/POST/DELETE | `/api/lectures/{id}/collaborators...` | Quản lý giáo viên được chia sẻ |
| DELETE | `/api/lectures/{id}` | Archive bài giảng, vẫn giữ version lịch sử |

Luồng trạng thái: `DRAFT -> PROCESSING -> READY -> PUBLISHED -> ARCHIVED`.
Lỗi sinh nội dung chuyển sang `FAILED` và có thể retry qua GenerationJob.

Backend 3 chỉ công bố `LectureVersion` bất biến cho Class và Quiz. Kiểm tra lớp,
lịch học và enrollment được đặt sau cổng `LectureAccessGrantVerifier`; bản mặc
định fail-closed cho đến khi Backend 2 cung cấp adapter, tránh đọc trực tiếp DB
của module khác.

## Asset

| Method | Path | Mô tả |
| --- | --- | --- |
| POST | `/api/assets` | Upload asset cùng `purpose`, nguồn ngoài và license tùy chọn |
| GET | `/api/assets/{assetId}` | Metadata |
| GET | `/api/assets/{assetId}/content` | Tải nội dung nhị phân |
| DELETE | `/api/assets/{assetId}` | Archive asset |

Hệ thống đối chiếu extension, MIME và magic bytes; loại đường dẫn nguy hiểm,
macro/ActiveX/embedded executable, encrypted OOXML, ZIP bomb và active content
trong PDF. SHA-256 được lưu cùng nội dung bất biến.

Các giới hạn có thể cấu hình:

- `app.asset.max-source-size-bytes` (mặc định 20 MiB)
- `app.asset.max-image-size-bytes` (mặc định 10 MiB)
- `app.asset.max-export-size-bytes` (mặc định 20 MiB)
- `app.asset.max-other-size-bytes` (mặc định 20 MiB)

## GenerationJob

| Method | Path | Mô tả |
| --- | --- | --- |
| GET | `/api/generation-jobs/{jobId}` | Trạng thái, tiến độ và toàn bộ attempt |
| POST | `/api/generation-jobs/{jobId}/retry` | Tạo attempt mới cho job thất bại |
| POST | `/api/generation-jobs/{jobId}/cancel` | Hủy job đang chờ/đang chạy |

Trạng thái: `QUEUED`, `PROCESSING`, `DONE`, `FAILED`, `CANCELLED`. Mỗi attempt
được giữ lại; idempotency key chỉ dùng lại khi payload trùng khớp.

## Presentation

| Method | Path | Mô tả |
| --- | --- | --- |
| POST | `/api/presentations/from-lecture` | Sinh từ `lectureVersionId`, bất đồng bộ |
| POST | `/api/presentations/from-file` | Sinh từ PDF/DOCX, bất đồng bộ |
| GET | `/api/presentations` | Danh sách sở hữu/chia sẻ, có phân trang |
| GET | `/api/presentations/{presentationId}` | Chi tiết presentation |
| GET | `/api/presentations/{presentationId}/versions` | Danh sách version |
| PATCH | `/api/presentations/{presentationId}` | Tạo version mới từ title/slides đã sửa |
| POST | `/api/presentations/{presentationId}/versions/{versionId}/restore` | Khôi phục bằng cách tạo version mới |
| POST | `/api/presentations/{presentationId}/publish` | Publish một version bất biến |
| GET/POST/DELETE | `/api/presentations/{presentationId}/collaborators...` | Quản lý co-editor |
| POST | `/api/presentations/{presentationId}/versions/{versionId}/exports` | Xuất đúng version sang PPTX |
| GET | `/api/presentations/{presentationId}/exports/{exportId}/content` | Tải PPTX đã xuất |
| DELETE | `/api/presentations/{presentationId}` | Archive presentation |

Mỗi slide gồm `title`, `contentBlocks`, `speakerNotes`, `layout` và `order`.
Kết quả AI luôn tạo draft để giáo viên duyệt. Retry thất bại không ghi đè một
version đã sẵn sàng. Export gắn cố định với version và có SHA-256 để bảo đảm nội
dung tải về không thay đổi.

Template hợp lệ được cấu hình bởi `app.presentation.allowed-templates`, mặc
định là `standard,academic,minimal`.
