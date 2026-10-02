# Kiểm thử bản sửa demo

Chạy `docker compose -f verification/compose.yaml up -d --build` từ thư mục `docker-demo`, sau đó `python verification/verify_demo.py --allow-demo-reset`.

Stack này dùng MySQL và volume riêng, HTTP chỉ ở `127.0.0.1:18080`. Script từ chối địa chỉ khác và chưa gửi yêu cầu khi thiếu cờ cho phép khôi phục dữ liệu mẫu.

Các nhóm kiểm thử bao gồm đăng nhập hợp lệ/thất bại, phiên và phân quyền trên API/HTML, đổi ID phiên, reset từ trình duyệt khác, hiển thị HTML dưới dạng văn bản, collation của MySQL, tìm kiếm/UNION, thêm/sửa, không thay đổi dữ liệu, xóa ID bình thường, từ chối ID không hợp lệ, lỗi SQL và kịch bản không tồn tại.

Kiểm tra mất kết nối trong stack riêng:

```text
python verification/verify_connection_failure.py before --allow-demo-reset
docker compose -f verification/compose.yaml stop db
python verification/verify_connection_failure.py during --allow-demo-reset
docker compose -f verification/compose.yaml start db
python verification/verify_connection_failure.py after --allow-demo-reset
```

Luôn khởi động lại `db` sau phép thử mất kết nối, kể cả khi kiểm tra thất bại. Nhánh `during` yêu cầu thao tác xóa/khôi phục báo lỗi khi MySQL dừng; nhánh `after` so sánh toàn bộ dữ liệu với snapshot ban đầu. Dừng stack kiểm thử bằng `docker compose -f verification/compose.yaml down`.

Ngày 02/10/2026: bản sửa đã biên dịch thành công bằng Java 17; 415 kiểm tra HTTP qua, không có nhóm thất bại; phép thử mất kết nối trước/trong/sau qua. Kiểm tra trình duyệt xác nhận phiên thật, trang tài khoản sau vượt đăng nhập, Prepared Statement từ chối cùng đầu vào và giữ bảng quan sát khi CSDL mất kết nối.

Gói hai thư mục ngày 02/10/2026: build lại Docker bằng Java 17 và chạy lại 415 kiểm tra HTTP thành công với cookie `SQLI_DEMO_DOCKER`. Bản Java/MySQL riêng được kiểm tra bằng JDK 17 và MySQL 8.4 trong môi trường thử nghiệm: `run.sh` chạy từ đường dẫn có khoảng trắng, SQL tạo đủ 15/15 dòng, tài khoản DB có quyền dữ liệu tối thiểu, đăng nhập/phiên/trang bảo vệ/xóa một bài/khôi phục hoạt động với cookie `SQLI_DEMO_NATIVE`. Launcher Windows đã được kiểm tra trường hợp Java 25 và từ chối đúng trước khi chạy Maven; chưa chạy toàn bộ launcher Windows với JDK 17 trên MySQL cá nhân.
