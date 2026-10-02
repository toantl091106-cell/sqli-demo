# Phần 1 — chạy web và MySQL bằng Docker

Cài Docker Desktop, dùng Linux containers và mở Docker engine. Java 17, Maven và MySQL 8.4 nằm trong Docker; máy không cần cài riêng ba công cụ này.

## Chạy

Mở terminal tại thư mục `docker-demo`, hoặc chạy `start.cmd` trên Windows:

```console
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

Lần đầu cần Internet để tải image và thư viện. Chờ `Started SqliWebDemoApplication`, mở **http://localhost:8080**. `Ctrl+C` chỉ thoát xem log. Tài khoản web: `admin` / `admin123`; `user1` / `pass123`.

Trên Linux/macOS có thể chạy `./start.sh`. Tất cả script xác định thư mục theo vị trí script, nên đường dẫn chứa khoảng trắng vẫn dùng được.

## Database và Workbench

`docker/mysql/init.sql` tạo bảng và 15 người dùng/15 bài viết khi volume MySQL mới được khởi tạo. Dữ liệu nằm trong volume `mysql_data` của project `sqli-demo-docker`.

Tạo kết nối **SQLi Demo Docker** trong Workbench:

| Trường | Giá trị |
| --- | --- |
| Method | Standard (TCP/IP) |
| Hostname | `127.0.0.1` |
| Port | `3307` |
| Username | `sqli_demo` |
| Password | `local_demo_app_2026` |
| Default Schema | `sqli_demo` |

Đây là mật khẩu mẫu công khai của gói demo. Nhấn **OK** trong hộp tạo kết nối để lưu, rồi nhấp đúp ô kết nối. Nếu Workbench cảnh báo phiên bản MySQL 8.4, chọn **Continue Anyway**; một số chức năng quản trị có thể không tương thích.

Mở **Schemas → sqli_demo → Tables**, chuột phải `users` hoặc `posts` → **Select Rows – Limit 1000**. Thay đổi tại đây tác động trực tiếp đến dữ liệu mà web Docker đọc.

## Dừng, mở lại và cập nhật

```console
docker compose down
docker compose up -d
```

`down` giữ database trong volume. Không thêm `-v` khi muốn giữ dữ liệu. Sau khi sửa source:

```console
docker compose up -d --build
```

## Cổng đang bị chiếm

Đóng gói này dùng project khác với bản `sqli-demo-local` cũ, nên không dùng chung database volume. Dừng bản đang dùng cổng 8080/3307, hoặc sao chép `.env.example` thành `.env` và đổi:

```dotenv
APP_PORT=8082
DB_PORT=3308
```

Chạy lại `docker compose up -d --build`. Khi đó mở web cổng 8082, Workbench dùng cổng 3308. Hai cổng chỉ được mở trên `127.0.0.1`.

## Kiểm tra lỗi và source

```console
docker compose logs --tail=100 db
docker compose logs --tail=100 app
```

Mã backend nằm trong `src/main/java/com/example/sqliwebdemo`, giao diện trong `src/main/resources/templates` và `static`. `Dockerfile` build web bằng Java 17 rồi chạy file JAR. Build bỏ qua test context vì MySQL chưa chạy ở bước đóng image; kiểm thử HTTP thực hiện sau khi stack riêng đã sẵn sàng.

Xem [hướng dẫn kiểm thử](verification/README.md). Nút khôi phục trên web đưa dữ liệu demo về mẫu ban đầu.
