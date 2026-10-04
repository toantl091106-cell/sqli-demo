# SQL Injection Demo — hai cách chạy

Web thực hành Spring Boot gồm đăng nhập, tìm kiếm, thêm, sửa và xóa dữ liệu. Mỗi kịch bản đối chiếu truy vấn nối chuỗi với Prepared Statement. Thông báo dựa trên kết quả thực tế; đăng nhập tạo phiên để kiểm tra trang Tài khoản và trang Quản trị.

| Phần | Thư mục | Cần cài | Web | Database |
| --- | --- | --- | --- | --- |
| Chạy toàn bộ bằng Docker | [docker-demo](docker-demo/README.md) | Docker Desktop | `http://localhost:8080` | MySQL Docker, cổng `3307`, schema `sqli_demo` |
| Chạy Java và MySQL riêng | [java-mysql-demo](java-mysql-demo/README.md) | JDK 17, MySQL Server, Workbench | `http://localhost:8081` | MySQL trên máy, cổng `3306`, schema `sqli_demo_native` |

## 1. Docker

Mở Docker Desktop. Trong terminal tại thư mục repo:

```console
cd docker-demo
docker compose up -d --build
docker compose logs -f app
```

Chờ log `Started SqliWebDemoApplication`, mở web ở cổng 8080. `Ctrl+C` thoát xem log; container vẫn chạy. [Hướng dẫn Docker và Workbench](docker-demo/README.md).

## 2. Code và database riêng

- Mã Java, HTML và JavaScript ở [`java-mysql-demo/code`](java-mysql-demo/code).
- SQL tạo database và tài khoản mẫu ở [`java-mysql-demo/db`](java-mysql-demo/db).
- Trên Windows: tải ZIP của nhánh `update` và giải nén toàn bộ repo, cài JDK và chuẩn bị MySQL một lần theo [hướng dẫn Java/MySQL](java-mysql-demo/README.md), rồi nhấp đúp **[`Mo-web.cmd`](Mo-web.cmd)**. File này cũng có trong `java-mysql-demo`.
- Launcher tự tìm JDK qua `JAVA_HOME` hoặc `PATH`, build code bằng Maven Wrapper và mở trình duyệt khi web kết nối được database. Không cần tải file JAR riêng hoặc sửa đường dẫn theo máy người tạo.
- Web chạy nền; nhấp đúp **[`Dung-web.cmd`](Dung-web.cmd)** để dừng. Khi sửa code, dừng rồi mở lại để build bản mới. MySQL và dữ liệu vẫn được giữ.
- Điểm khởi động web là `SqliWebDemoApplication.java`. Bản console `Main.java` cũ đã được bỏ khỏi gói web để tránh chạy nhầm kịch bản.

Hai cách dùng database và tên cookie riêng nên có thể chạy song song. Nếu cổng bị chiếm, xem hướng dẫn từng phần để đổi cổng. Bản Docker cũ đang chạy cổng 8080/3307 cần được dừng hoặc đổi cổng trước khi mở thêm bản Docker này.

## Dữ liệu và tài khoản mẫu

Mỗi bản khởi tạo 15 người dùng và 15 bài viết. Tài khoản trên **web**: `admin` / `admin123`, hoặc `user1` / `pass123`. Đây là tài khoản web, khác tài khoản kết nối MySQL.

Nút khôi phục trên web đưa dữ liệu của bản đang chạy về mẫu ban đầu và hủy các phiên đăng nhập. Xóa bài viết với ID bình thường chỉ ảnh hưởng bài viết đó; bảng người dùng không thuộc truy vấn xóa.

Đây là bài thực hành có chế độ SQL Injection cố ý và dữ liệu giả. Cấu hình mặc định chỉ mở trên máy cục bộ. Không dùng database hoặc mật khẩu thật cho bài thực hành.

## Sửa code và kiểm thử

Cả hai thư mục chứa cùng bản mã web. Khi thay đổi Java/HTML/JavaScript, áp dụng cho cả `docker-demo/src` và `java-mysql-demo/code/src`. Bản Docker cần build lại; bản Java cần dừng và chạy lại. Không đưa mật khẩu cá nhân hoặc `.env` vào Git.

Kiểm thử hồi quy dùng database Docker riêng và cổng 18080; xem [`docker-demo/verification`](docker-demo/verification/README.md). Không chạy script khôi phục kiểm thử trên database đang dùng để trình diễn.
