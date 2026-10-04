# Chạy web bằng Java và MySQL trên máy

Bản này dùng **JDK (khuyến nghị 17)** và **MySQL 8.0/8.4 đang cài trên máy**, với MySQL Workbench để tạo và xem dữ liệu. Maven Wrapper đi kèm nên không cần cài Maven riêng. Web mặc định ở **http://127.0.0.1:8081**, database riêng **`sqli_demo_native`**, cookie riêng **`SQLI_DEMO_NATIVE`**. Có thể chạy song song với bản Docker ở cổng 8080.

Tải **Code → Download ZIP** khi đang chọn nhánh `update`, rồi giải nén toàn bộ repo. Giữ thư mục `java-mysql-demo` cùng các file launcher ở thư mục cha; không tải riêng từng file `.cmd`.

## 1. Chọn Java

Mở CMD và kiểm tra:

```cmd
java -version
javac -version
```

`Mo-web.cmd` tự tìm JDK qua `JAVA_HOME`, hoặc qua Java trong `PATH`, và yêu cầu JDK từ 17 trở lên. JDK 17 là lựa chọn khuyến nghị; launcher đã được kiểm tra với JDK 25 trên Windows. Code vẫn biên dịch theo cấu hình Java 17 trong `pom.xml`. Máy nào đã có JDK phù hợp thì dùng JDK đó.

Nếu cần chọn JDK cho **cửa sổ CMD hiện tại**, dùng đường dẫn cài thực tế:

```cmd
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x"
set "PATH=%JAVA_HOME%\bin;%PATH%"
```

Thay `jdk-17.0.x` bằng tên thư mục JDK 17 đã cài. Hai lệnh này chỉ áp dụng trong cửa sổ hiện tại.

## 2. Chuẩn bị database một lần

1. Mở MySQL Workbench, kết nối MySQL trên máy bằng tài khoản quản trị của bạn, thường là `root`, cổng `3306`.
2. Chọn **File → Open SQL Script**, mở **`db/init.sql`** trong thư mục này và chạy toàn bộ script. Script tạo schema mới `sqli_demo_native`, hai bảng `users`, `posts`, mỗi bảng **15 dòng mẫu**.
3. Mở và chạy **`db/create-user.sql`**. Script tạo tài khoản riêng `sqli_demo_native` chỉ có quyền `SELECT`, `INSERT`, `UPDATE`, `DELETE` trên schema demo.

Chạy mỗi script **một lần** và dừng nếu có lỗi. Nếu schema hoặc tài khoản đã tồn tại, kiểm tra trước khi tiếp tục; các script chủ ý không ghi đè database cũ. Tài khoản demo có mật khẩu mẫu **`local_code_db_2026`**. Mật khẩu quản trị cá nhân chỉ nhập trong Workbench, không lưu vào mã nguồn.

## 3. Chạy web

Trên Windows, sau khi chuẩn bị database, nhấp đúp **`Mo-web.cmd`** ở thư mục gốc repo hoặc trong `java-mysql-demo`. Launcher tự build code, chạy web nền, kiểm tra kết nối MySQL rồi mở trình duyệt. Mỗi máy dùng JDK và MySQL của máy đó; launcher không chứa đường dẫn ổ đĩa cá nhân.

Hoặc mở CMD trong thư mục `java-mysql-demo`:

```cmd
Mo-web.cmd
```

Trên macOS/Linux có JDK và MySQL tương ứng:

```sh
sh run.sh
```

Lần đầu cần Internet để Maven Wrapper tải Maven và thư viện; có thể mất vài phút. Khi launcher báo `Web ready`, web đã sẵn sàng ở **http://127.0.0.1:8081**. Có thể đóng cửa sổ launcher trên Windows. Đăng nhập thử bằng `admin` / `admin123` hoặc `user1` / `pass123`.

Launcher Windows chạy Maven với `-DskipTests package`, sau đó chạy JAR bằng Spring Boot. `-DskipTests` bỏ kiểm thử context cần database trong lúc build. Gói này chỉ chứa điểm khởi động web; chương trình console `Main.java` cũ đã được bỏ. Nếu cần build JAR riêng:

```cmd
cd code
mvnw.cmd -DskipTests package
java -jar target\sqli-web-demo-0.0.1-SNAPSHOT.jar
```

Trên Windows, nhấp đúp **`Dung-web.cmd`** để dừng web; MySQL và dữ liệu được giữ. Nhấp `Mo-web.cmd` khi cùng bản đã chạy chỉ mở lại trình duyệt. Khi sửa code, dừng rồi nhấp `Mo-web.cmd` để build và chạy bản mới. Launcher chỉ dừng tiến trình Java thuộc đúng bản repo này; nếu cổng thuộc chương trình hoặc bản khác, nó báo lỗi và không dừng chương trình đó.

Log Windows nằm ở `runtime/web-8081.stdout.log` và `runtime/web-8081.stderr.log` trong thư mục gốc repo (đổi số nếu đổi cổng). Nếu khởi động thất bại, launcher giữ thông báo lỗi để đọc. Nó không tự nhập SQL, ghi đè hoặc khôi phục database.

Các launcher cũ `run.cmd` và `run.sh` vẫn chạy Spring Boot trong Terminal và yêu cầu JDK 17–21. Với cách này giữ Terminal mở và nhấn **Ctrl + C** để dừng. Nút **Khôi phục dữ liệu** trên web thay dữ liệu trong schema demo bằng dữ liệu mẫu và hủy các phiên đăng nhập cũ.

## 4. Xem database trong Workbench

Tạo kết nối mới (**+** cạnh MySQL Connections):

| Mục | Giá trị mặc định |
|---|---|
| Connection Name | SQLi Native Demo |
| Hostname | `127.0.0.1` |
| Port | `3306` |
| Username | `sqli_demo_native` |
| Password | `local_code_db_2026` |

Mở kết nối, refresh **Schemas**, chọn `sqli_demo_native` và chạy:

```sql
USE sqli_demo_native;
SHOW TABLES;
SELECT * FROM users;
SELECT * FROM posts;
```

## 5. Đổi cấu hình khi cần

Ứng dụng đọc các biến môi trường; không cần sửa mã Java hay lưu mật khẩu MySQL cá nhân. `Mo-web.cmd` giữ các giá trị bạn đã đặt. Ví dụ trong CMD, trước khi chạy `Mo-web.cmd`:

```cmd
set "SQLI_DB_URL=jdbc:mysql://127.0.0.1:3306/sqli_demo_native?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
set "SQLI_DB_USER=sqli_demo_native"
set "SQLI_DB_PASSWORD=local_code_db_2026"
set "SQLI_HTTP_PORT=8081"
set "SQLI_HTTP_ADDRESS=127.0.0.1"
Mo-web.cmd
```

Nếu bạn tự đổi mật khẩu của tài khoản demo trong MySQL, đặt `SQLI_DB_PASSWORD` thành giá trị tương ứng trong Terminal. Giữ URL trỏ tới schema dành riêng cho bài thực hành vì các chức năng sửa, xóa và khôi phục sẽ thay đổi dữ liệu của schema được chọn.

Nếu lỗi `Access denied`, kiểm tra tài khoản demo và mật khẩu. Nếu lỗi `Unknown database`, chạy bước tạo schema. Nếu không kết nối được MySQL, kiểm tra dịch vụ MySQL đang chạy và cổng trong `SQLI_DB_URL`. Nếu cổng web bận, đặt `SQLI_HTTP_PORT=8082` rồi mở cùng cổng trên trình duyệt.
