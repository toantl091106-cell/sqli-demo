-- Runs once when the dedicated Docker database volume is first initialized.
USE sqli_demo;

CREATE TABLE users (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'user'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE posts (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO users (username, password, email, role) VALUES ('admin', 'admin123', 'admin@gmail.com', 'admin'), ('user1', 'pass123', 'user1@gmail.com', 'user'), ('john_doe', 'john2024', 'john.doe@company.com', 'user'), ('jane_smith', 'jane_pass', 'jane.smith@gmail.com', 'manager'), ('alice_w', 'alice_secret', 'alice.wonder@yahoo.com', 'user'), ('bob_builder', 'builder99', 'bob.builder@construction.org', 'user'), ('charlie_b', 'charlie_pass', 'charlie.brown@peanuts.com', 'moderator'), ('david_beck', 'david_777', 'david.beckham@sports.com', 'user'), ('eva_green', 'eva_pass', 'eva.green@cinema.fr', 'user'), ('frank_castle', 'punisher', 'frank.castle@marvel.com', 'admin'), ('grace_hopper', 'cobol_queen', 'grace.hopper@navy.mil', 'manager'), ('hank_pym', 'ant_man', 'hank.pym@pymtech.com', 'user'), ('ivy_poison', 'green_world', 'ivy.poison@gotham.org', 'user'), ('jack_sparrow', 'black_pearl', 'captain.jack@caribbean.com', 'user'), ('kevin_mitnick', 'hacker_legend', 'kevin.m@security.com', 'admin');

INSERT INTO posts (title, content) VALUES ('Huong dan Lap trinh Spring Boot', 'Bai viet chia se kien thuc co ban ve Spring Boot va MVC.'), ('Tim hieu ve lo hang SQL Injection', 'Khai niem, nguy co va cach phong tranh tan cong SQLi.'), ('Huong dan su dung MySQL Workbench', 'Cach thiet lap database va thuc thi cau lenh SQL.'), ('10 Quy tac Bao mat Ung dung Web', 'Danh sach cac quy tac bao mat quan trong theo OWASP Top 10.'), ('Spring Security Toan tap', 'Huong dan cau hinh phan quyen nguoi dung trong Spring Boot.'), ('Khai niem Prepared Statement', 'Tai sao Prepared Statement lai chong duoc SQL Injection?'), ('Thuc hanh Penetration Testing', 'Cac buoc thuc hien danh gia an ninh mang cho he thong.'), ('Toi uu hoa Truy van SQL', 'Meo su dung Index va ghi cau lenh SQL hieu qua.'), ('Kien truc Microservices', 'Xay dung he thong phan tan voi Spring Cloud.'), ('Thiet lap CI/CD Pipeline', 'Tu dong hoa quy trinh kiem thu va trien khai ung dung.'), ('Docker cho Nguoi moi bat dau', 'Huong dan dong goi ung dung Spring Boot va MySQL voi Docker.'), ('Bao mat CSDL MySQL', 'Huong dan phan quyen va ma hoa du lieu trong MySQL.'), ('Phan tich Payload Ma doc', 'Cach phan tich cac chuoi payload SQLi trong thuc te.'), ('RESTful API Design Standards', 'Quy chuan thiet ke API chuan RESTful cho doanh nghiep.'), ('Tong quan ve Web Application Firewall', 'Vai tro cua WAF trong viec ngan chan cac cuoc tan cong Web.');
