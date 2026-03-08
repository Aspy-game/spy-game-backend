# Spy Game Backend

Dự án Backend cho trò chơi "Spy Game" (Keyword Spy) được xây dựng bằng Spring Boot và MongoDB.

## 🛠 Công nghệ sử dụng

- **Java 17**
- **Spring Boot 4.0.3**
- **MongoDB**
- **Spring Security + JWT** (JSON Web Token)
- **Maven**

## 🚀 Hướng dẫn cài đặt và chạy ứng dụng

### 1. Yêu cầu hệ thống
- Đã cài đặt **Java 17** trở lên.
- Đã cài đặt và đang chạy **MongoDB** cục bộ (mặc định tại `localhost:27017`).

### 2. Cấu hình Database
Đảm bảo dịch vụ MongoDB đang chạy. Ứng dụng sẽ tự động tạo database `spygame_dev` khi có dữ liệu đầu tiên được lưu xuống.

Cấu hình mặc định trong `src/main/resources/application.properties`:
```properties
spring.data.mongodb.uri=mongodb://127.0.0.1:27017/spygame_dev
spring.data.mongodb.database=spygame_dev
```

### 3. Chạy ứng dụng
Mở terminal tại thư mục `game/` và chạy lệnh sau:

**Trên Windows:**
```powershell
.\mvnw.cmd spring-boot:run
```

**Trên Linux/macOS:**
```bash
./mvnw spring-boot:run
```

Backend sẽ khởi chạy tại: `http://localhost:8080`

## 🔌 API Endpoints chính

| Endpoint | Phương thức | Mô tả | Quyền truy cập |
| :--- | :---: | :--- | :--- |
| `/api/auth/register` | `POST` | Đăng ký tài khoản mới | Public |
| `/api/auth/login` | `POST` | Đăng nhập và lấy JWT token | Public |
| `/api/health` | `GET` | Kiểm tra trạng thái kết nối DB | Public |

## 🛡 Cấu hình CORS
Backend đã được cấu hình cho phép các yêu cầu từ Frontend chạy tại `http://localhost:5173` (mặc định của Vite). Nếu bạn chạy Frontend ở cổng khác, hãy cập nhật trong `SecurityConfig.java`.

## 📝 Lưu ý cho thành viên mới
- Khi clone về, nếu gặp lỗi 403 khi gọi API đăng ký, hãy kiểm tra xem MongoDB đã được bật chưa.
- Mọi yêu cầu API (trừ Auth và Health) đều yêu cầu Header: `Authorization: Bearer <your_token>`.
