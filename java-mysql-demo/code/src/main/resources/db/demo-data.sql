-- Stable sample IDs allow repeated, transactional resets without TRUNCATE.
INSERT INTO users (id, username, password, email, role) VALUES
(1, 'admin', 'admin123', 'admin@gmail.com', 'admin'),
(2, 'user1', 'pass123', 'user1@gmail.com', 'user'),
(3, 'john_doe', 'john2024', 'john.doe@company.com', 'user'),
(4, 'jane_smith', 'jane_pass', 'jane.smith@gmail.com', 'manager'),
(5, 'alice_w', 'alice_secret', 'alice.wonder@yahoo.com', 'user'),
(6, 'bob_builder', 'builder99', 'bob.builder@construction.org', 'user'),
(7, 'charlie_b', 'charlie_pass', 'charlie.brown@peanuts.com', 'moderator'),
(8, 'david_beck', 'david_777', 'david.beckham@sports.com', 'user'),
(9, 'eva_green', 'eva_pass', 'eva.green@cinema.fr', 'user'),
(10, 'frank_castle', 'punisher', 'frank.castle@marvel.com', 'admin'),
(11, 'grace_hopper', 'cobol_queen', 'grace.hopper@navy.mil', 'manager'),
(12, 'hank_pym', 'ant_man', 'hank.pym@pymtech.com', 'user'),
(13, 'ivy_poison', 'green_world', 'ivy.poison@gotham.org', 'user'),
(14, 'jack_sparrow', 'black_pearl', 'captain.jack@caribbean.com', 'user'),
(15, 'kevin_mitnick', 'hacker_legend', 'kevin.m@security.com', 'admin');

INSERT INTO posts (id, title, content) VALUES
(1, 'Huong dan Lap trinh Spring Boot', 'Bai viet chia se kien thuc co ban ve Spring Boot va MVC.'),
(2, 'Tim hieu ve lo hang SQL Injection', 'Khai niem, nguy co va cach phong tranh tan cong SQLi.'),
(3, 'Huong dan su dung MySQL Workbench', 'Cach thiet lap database va thuc thi cau lenh SQL.'),
(4, '10 Quy tac Bao mat Ung dung Web', 'Danh sach cac quy tac bao mat quan trong theo OWASP Top 10.'),
(5, 'Spring Security Toan tap', 'Huong dan cau hinh phan quyen nguoi dung trong Spring Boot.'),
(6, 'Khai niem Prepared Statement', 'Tai sao Prepared Statement lai chong duoc SQL Injection?'),
(7, 'Thuc hanh Penetration Testing', 'Cac buoc thuc hien danh gia an ninh mang cho he thong.'),
(8, 'Toi uu hoa Truy van SQL', 'Meo su dung Index va ghi cau lenh SQL hieu qua.'),
(9, 'Kien truc Microservices', 'Xay dung he thong phan tan voi Spring Cloud.'),
(10, 'Thiet lap CI/CD Pipeline', 'Tu dong hoa quy trinh kiem thu va trien khai ung dung.'),
(11, 'Docker cho Nguoi moi bat dau', 'Huong dan dong goi ung dung Spring Boot va MySQL voi Docker.'),
(12, 'Bao mat CSDL MySQL', 'Huong dan phan quyen va ma hoa du lieu trong MySQL.'),
(13, 'Phan tich Payload Ma doc', 'Cach phan tich cac chuoi payload SQLi trong thuc te.'),
(14, 'RESTful API Design Standards', 'Quy chuan thiet ke API chuan RESTful cho doanh nghiep.'),
(15, 'Tong quan ve Web Application Firewall', 'Vai tro cua WAF trong viec ngan chan cac cuoc tan cong Web.');
