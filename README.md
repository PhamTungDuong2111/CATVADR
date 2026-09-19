# ChupAnhTV - Ứng Dụng Chụp Ảnh Thiên Văn Android (Live Stacking & Star Alignment)

Dự án Android gốc mô phỏng và phát triển theo cơ chế của **AstroShader (iOS)** và **Google Camera Astrophotography**, chuyên dùng để chụp dải Ngân Hà (Milky Way), tinh vân, và vệt sao trên điện thoại Android.

---

## 📍 Vị trí dự án & File APK cài đặt

- **Thư mục dự án Android Studio**: `C:\Users\Admin\Downloads\ChupAnhTV`
- **File APK cài đặt trực tiếp trên điện thoại Android**:
  - `C:\Users\Admin\Downloads\ChupAnhTV\ChupAnhTV.apk` (16.7 MB)
  - `C:\Users\Admin\Downloads\ChupAnhTV.apk` (Bản copy tiện lợi trong thư mục Downloads)
  - Đường dẫn build debug: `app/build/outputs/apk/debug/app-debug.apk`

> 📱 **Lưu ý**: File APK là file chuẩn của hệ điều hành Android (chứa native library cho cả `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), hỗ trợ cài đặt trên tất cả các dòng máy Android thật (Samsung, Xiaomi, Oppo, Vivo, Google Pixel, Realme, v.v.).

---

## 🚀 Cách mở và chạy trong Android Studio

1. Mở **Android Studio**.
2. Chọn **File -> Open...** (hoặc Open Project).
3. Trỏ tới thư mục: `C:\Users\Admin\Downloads\ChupAnhTV`.
4. Nhấn **OK**, Android Studio sẽ tự động đồng bộ Gradle (đã tích hợp sẵn Gradle Wrapper 8.4 và cấu hình SDK).
5. Cắm điện thoại Android (bật USB Debugging) hoặc mở máy ảo -> Nhấn nút **Run (Shift + F10)**.

---

## 🌌 Các tính năng kỹ thuật nổi bật

### 1. Điều khiển Camera chuyên sâu (Camera2 API Native)
- **Khóa nét vô cực (Focus at Infinity)**: Đặt `LENS_FOCUS_DISTANCE = 0.0f` để các vì sao luôn sắc nét tối đa, không bị trôi nét trong đêm tối.
- **Tùy chỉnh ISO thủ công**: Chọn nhanh hoặc trượt từ `ISO 400` đến `ISO 12800` để tăng độ nhạy sáng thu tín hiệu tinh vân mờ.
- **Thời gian phơi sáng (Shutter Speed)**: 0.5s, 1s, 2s, 4s, 8s, 15s, 30s.
- **Tắt thuật toán khử nhiễu tự động của hãng**: Tắt `NOISE_REDUCTION_MODE` để tránh làm mờ các ngôi sao nhỏ.

### 2. Thuật toán Cân chỉnh Sao (Star Alignment Algorithm)
- **Phát hiện sao (Star Detection)**: Phân tích độ sáng cục bộ (local brightness peak) và tính tâm sao với độ chính xác dưới pixel (Subpixel Centroid Calculation) viết bằng C++ (Android NDK) kèm giải thuật dự phòng bằng Kotlin.
- **Khớp chòm sao & Khử chuyển động tự quay của Trái Đất**:
  - Dùng thuật toán tam giác sao (Asterism Matching) kết hợp RANSAC để tìm ma trận Affine / Homography giữa các khung hình liên tiếp.
  - Tự động xoay và tịnh tiến frame mới cho khớp hoàn hảo với frame gốc trước khi cộng dồn.

### 3. Động cơ Xếp chồng ảnh thời gian thực (Live Stacking - OpenGL ES 3.0)
- **🌌 Ngân Hà (Deep Sky Average Stacking)**: Cộng dồn và chia trung bình độ sáng liên tục trong bộ đệm FP32 (Floating Point Buffer), loại bỏ hạt nhiễu ISO cao, làm mịn nền trời đen và làm nổi bật dải Ngân Hà.
- **🛰️ Khử Vệ Tinh (Kappa-Sigma Clipping)**: Tự động loại bỏ các pixel tăng sáng bất thường qua các khung hình để xóa sạch vệt máy bay hoặc chùm vệ tinh Starlink bay ngang khung hình.
- **🌠 Vệt Sao (Star Trails - Maximum Blend)**: Giữ lại giá trị sáng cực đại qua từng frame để tạo ảnh vệt sao quay tròn quanh sao Bắc Cực (Polaris).

### 4. Giao diện (UI/UX) Astrophotography đỉnh cao & Chuyên nghiệp
- **Chế độ Ánh Sáng Đỏ (Red Night Mode)**: Nút bật tắt chế độ bảo vệ thị lực ban đêm (bước sóng đỏ > 650nm) biến toàn bộ giao diện sang màu đỏ thẫm/đen để mắt người dùng không bị mất khả năng thích ứng bóng tối khi đứng ngoài trời đêm.
- **Thước cân đường chân trời điện tử (Artificial Horizon Leveler)**:
  - Tích hợp cảm biến góc nghiêng và la bàn.
  - Đường chân trời xoay mượt mà theo góc nghiêng của tripod, đổi sang màu XANH LÁ khi máy đạt trạng thái cân bằng tuyệt đối (±1.5°).
  - Hiển thị góc ngẩng bầu trời (Zenith pitch) và hướng la bàn (Bắc, Nam, Đông, Tây).
- **Biểu đồ Histogram thời gian thực**: Hiển thị phân bổ ánh sáng vùng tối (Shadows), vùng trung tính (Midtones) và vùng cháy sáng (Highlights) theo thời gian thực.
- **Nút chụp Shutter hiện đại**: Vòng tròn cảm ứng lớn với viền phát sáng neon và vòng tiến trình đếm khung hình hoàn thành dạng tròn (`12 / 30 ảnh`).
- **Thanh trượt Kéo sáng động (Dynamic Log/MTF Stretch)**: Kéo sáng chi tiết tinh vân và khử ô nhiễm ánh sáng đô thị ngay trên màn hình Live Viewfinder.
- **🧪 Chế độ Bầu trời giả lập (Sky Simulator)**: Cho phép bật để kiểm tra thuật toán nhận diện sao, xoay sao và chồng ảnh ngay trong nhà hoặc ban ngày mà không cần đợi trời tối!
- **Hộp thoại xem kết quả & Xuất file (Result Inspector)**:
  - Xem ảnh độ phân giải cao sau khi hoàn tất chuỗi phơi sáng.
  - Lưu vào Thư viện Bộ sưu tập ảnh (MediaStore) với 1 cú chạm.
  - Xuất file 16-bit uncompressed TIFF cho các phần mềm xử lý thiên văn chuyên nghiệp (Siril, PixInsight).
