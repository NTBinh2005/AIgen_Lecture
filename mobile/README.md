# Interactive AI-Generated Lecture System — Mobile App

Dự án Mobile App được phát triển bằng framework **Flutter**, thuộc hệ thống Bài giảng Tương tác AI (G-06). Dự án tích hợp giao diện hiện đại theo phong cách Material 3, hỗ trợ đầy đủ chế độ Sáng/Tối (Light/Dark Theme) và Menu Footer điều hướng mượt mà.

## 🛠️ Tech Stack & Thư viện sử dụng
- **Framework:** Flutter 3.x (Dart >=3.0.0)
- **Design System:** Material 3
- **Google Fonts:** Be Vietnam Pro (Font chữ chuẩn của dự án)
- **Boilerplates:** Tích hợp sẵn cấu hình Android, iOS và Web build template.

## 📁 Cấu trúc thư mục dự án `mobile/`
```text
mobile/
├── android/          # Cấu hình build Android (Gradle, Manifest, MainActivity)
├── ios/              # Cấu hình build iOS (Runner, Info.plist)
├── web/              # Cấu hình build Web (index.html)
├── lib/
│   ├── main.dart     # Entry point cấu hình Theme (Light/Dark) và Khởi chạy App
│   └── screens/      # Danh sách màn hình chính
│       ├── main_screen.dart       # Footer Menu sử dụng NavigationBar & IndexedStack
│       ├── home_screen.dart       # Trang Tổng quan tiến độ & Gợi ý từ AI
│       ├── lectures_screen.dart   # Danh sách bài giảng tương tác
│       ├── analytics_screen.dart  # AI Phân tích kết quả học tập & Đề xuất sửa sai
│       └── profile_screen.dart    # Hồ sơ cá nhân & Cấu hình ứng dụng
├── pubspec.yaml      # File cấu hình dependencies & assets của Flutter
└── README.md         # Tài liệu hướng dẫn này
```

## 🚀 Hướng dẫn cài đặt và chạy ứng dụng

### 1. Yêu cầu hệ thống
- Đã cài đặt [Flutter SDK](https://docs.flutter.dev/get-started/install) (phiên bản từ 3.0.0 trở lên).
- Cài đặt extension Flutter & Dart trong VS Code hoặc Android Studio.

### 2. Tải dependencies
Di chuyển vào thư mục `mobile` và chạy lệnh sau để tải các package cần thiết:
```bash
cd mobile
flutter pub get
```

### 3. Chạy ứng dụng
Chạy lệnh bên dưới để mở ứng dụng trên thiết bị giả lập (Emulator) hoặc trình duyệt Web:
```bash
flutter run
```

## 📱 Chi tiết thiết kế Footer Menu (Mục 12)
Footer Menu được xây dựng bằng widget `NavigationBar` kết hợp với `IndexedStack` để đảm bảo giữ nguyên trạng thái scroll và trạng thái dữ liệu của từng Tab khi người dùng chuyển đổi qua lại:
1. **Tổng quan (`home_screen.dart`):** Dashboard hiển thị thông tin học tập chung, tiến độ bài giảng gần đây và thẻ Banner thông báo gợi ý thông minh từ AI.
2. **Bài học (`lectures_screen.dart`):** Danh sách bài giảng dạng thẻ trực quan kèm theo tiến độ phần trăm, số câu hỏi, bộ lọc phân loại và thanh tìm kiếm bài học.
3. **Phân tích (`analytics_screen.dart`):** Trực quan hóa tỷ lệ đúng/sai thông qua biểu đồ tròn, đi kèm chi tiết các đề xuất sửa đổi và khắc phục điểm yếu từ AI.
4. **Cá nhân (`profile_screen.dart`):** Thông tin người dùng (Role: Student), nút Bật/Tắt thông báo đẩy, nút chuyển đổi nhanh giao diện và nút Đăng xuất.
