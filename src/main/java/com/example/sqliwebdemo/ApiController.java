package com.example.sqliwebdemo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/data")
    public Map<String, Object> getLiveDatabase() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, username, email, role FROM users");
            List<Map<String, Object>> posts = jdbcTemplate.queryForList("SELECT id, title, content FROM posts");
            result.put("users", users);
            result.put("posts", posts);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/execute")
    public Map<String, Object> executeAttack(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        String scenario = payload.get("scenario");
        String input1 = payload.get("input1") != null ? payload.get("input1") : "";
        String input2 = payload.get("input2") != null ? payload.get("input2") : "";
        String input3 = payload.get("input3") != null ? payload.get("input3") : "";
        boolean secure = "true".equals(payload.get("secure"));

        String cleanInput1 = sanitizeComment(input1);
        String cleanInput2 = sanitizeComment(input2);
        String cleanInput3 = sanitizeComment(input3);

        String sqlExecuted = "";
        try {
            if ("1".equals(scenario)) { // Bypass Auth
                if (!secure) {
                    sqlExecuted = "SELECT * FROM users WHERE username = '" + cleanInput1 + "' AND password = '" + cleanInput2 + "'";
                    List<Map<String, Object>> list = jdbcTemplate.queryForList(sqlExecuted);
                    if (!list.isEmpty()) {
                        response.put("message", "DANG NHAP THANH CONG! Xin chao user: " + list.get(0).get("username") + " (Role: " + list.get(0).get("role") + ")");
                    } else {
                        response.put("message", "Dang nhap that bai! Sai username hoac password.");
                    }
                } else {
                    sqlExecuted = "SELECT * FROM users WHERE username = ? AND password = ? [PREPARED STATEMENT]";
                    List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM users WHERE username = ? AND password = ?", input1, input2);
                    if (!list.isEmpty()) {
                        response.put("message", "Dang nhap thanh cong!");
                    } else {
                        response.put("message", "DANG NHAP THAT BAI! Da ngan chan SQLi thanh cong.");
                    }
                }
            } else if ("2".equals(scenario)) { // Data Exfiltration
                if (!secure) {
                    sqlExecuted = "SELECT id, title, content FROM posts WHERE title LIKE '%" + cleanInput1 + "%'";
                    List<Map<String, Object>> list = jdbcTemplate.queryForList(sqlExecuted);
                    response.put("message", "Danh cap du lieu thanh cong! Lay duoc " + list.size() + " ban ghi: " + list.toString());
                } else {
                    sqlExecuted = "SELECT id, title, content FROM posts WHERE title LIKE ? [PREPARED STATEMENT]";
                    List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT id, title, content FROM posts WHERE title LIKE ?", "%" + input1 + "%");
                    response.put("message", "Da ngan chan SQLi. Tim thay " + list.size() + " bai viet.");
                }
            } else if ("3".equals(scenario)) { // Insert Injection
                if (!secure) {
                    sqlExecuted = "INSERT INTO users (username, password, email, role) VALUES ('" + cleanInput1 + "', '" + cleanInput2 + "', '" + cleanInput3 + "', 'user')";
                    jdbcTemplate.execute(sqlExecuted);
                    response.put("message", "Them user thanh cong vao CSDL bang SQL Injection!");
                } else {
                    sqlExecuted = "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, 'user') [PREPARED STATEMENT]";
                    jdbcTemplate.update("INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, 'user')", input1, input2, input3);
                    response.put("message", "Them user an toan thanh cong!");
                }
            } else if ("4".equals(scenario)) { // Update Injection
                if (!secure) {
                    sqlExecuted = "UPDATE users SET email = '" + cleanInput1 + "' WHERE id = 2";
                    int rows = jdbcTemplate.update(sqlExecuted);
                    response.put("message", "Cap nhat thanh cong " + rows + " ban ghi!");
                } else {
                    sqlExecuted = "UPDATE users SET email = ? WHERE id = 2 [PREPARED STATEMENT]";
                    int rows = jdbcTemplate.update("UPDATE users SET email = ? WHERE id = 2", input1);
                    response.put("message", "Cap nhat an toan thanh cong!");
                }
            } else if ("5".equals(scenario)) { // Delete Injection (Xóa cả bảng posts lẫn users)
                if (!secure) {
                    sqlExecuted = "DELETE posts, users FROM posts LEFT JOIN users ON 1=1 WHERE posts.id = " + cleanInput1;
                    int rows = jdbcTemplate.update(sqlExecuted);
                    response.put("message", "NGUY HIEM! Da xoa sach " + rows + " ban ghi khoi CA BANG posts LAN users!");
                } else {
                    sqlExecuted = "DELETE FROM posts WHERE id = ? [PREPARED STATEMENT]";
                    int rows = jdbcTemplate.update("DELETE FROM posts WHERE id = ?", input1);
                    response.put("message", "Da xoa an toan 1 bai viet!");
                }
            }
            response.put("sql", sqlExecuted);
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("sql", sqlExecuted);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PostMapping("/reset")
    public Map<String, String> resetDb() {
        Map<String, String> res = new HashMap<>();
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
            jdbcTemplate.execute("TRUNCATE TABLE posts;");
            jdbcTemplate.execute("TRUNCATE TABLE users;");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");

            jdbcTemplate.execute("INSERT INTO users (username, password, email, role) VALUES " +
                "('admin', 'admin123', 'admin@gmail.com', 'admin'), " +
                "('user1', 'pass123', 'user1@gmail.com', 'user'), " +
                "('john_doe', 'john2024', 'john.doe@company.com', 'user'), " +
                "('jane_smith', 'jane_pass', 'jane.smith@gmail.com', 'manager'), " +
                "('alice_w', 'alice_secret', 'alice.wonder@yahoo.com', 'user'), " +
                "('bob_builder', 'builder99', 'bob.builder@construction.org', 'user'), " +
                "('charlie_b', 'charlie_pass', 'charlie.brown@peanuts.com', 'moderator'), " +
                "('david_beck', 'david_777', 'david.beckham@sports.com', 'user'), " +
                "('eva_green', 'eva_pass', 'eva.green@cinema.fr', 'user'), " +
                "('frank_castle', 'punisher', 'frank.castle@marvel.com', 'admin'), " +
                "('grace_hopper', 'cobol_queen', 'grace.hopper@navy.mil', 'manager'), " +
                "('hank_pym', 'ant_man', 'hank.pym@pymtech.com', 'user'), " +
                "('ivy_poison', 'green_world', 'ivy.poison@gotham.org', 'user'), " +
                "('jack_sparrow', 'black_pearl', 'captain.jack@caribbean.com', 'user'), " +
                "('kevin_mitnick', 'hacker_legend', 'kevin.m@security.com', 'admin');");

            jdbcTemplate.execute("INSERT INTO posts (title, content) VALUES " +
                "('Huong dan Lap trinh Spring Boot', 'Bai viet chia se kien thuc co ban ve Spring Boot va MVC.'), " +
                "('Tim hieu ve lo hang SQL Injection', 'Khai niem, nguy co va cach phong tranh tan cong SQLi.'), " +
                "('Huong dan su dung MySQL Workbench', 'Cach thiet lap database va thuc thi cau lenh SQL.'), " +
                "('10 Quy tac Bao mat Ung dung Web', 'Danh sach cac quy tac bao mat quan trong theo OWASP Top 10.'), " +
                "('Spring Security Toan tap', 'Huong dan cau hinh phan quyen nguoi dung trong Spring Boot.'), " +
                "('Khai niem Prepared Statement', 'Tai sao Prepared Statement lai chong duoc SQL Injection?'), " +
                "('Thuc hanh Penetration Testing', 'Cac buoc thuc hien danh gia an ninh mang cho he thong.'), " +
                "('Toi uu hoa Truy van SQL', 'Meo su dung Index va ghi cau lenh SQL hieu qua.'), " +
                "('Kien truc Microservices', 'Xay dung he thong phan tan voi Spring Cloud.'), " +
                "('Thiet lap CI/CD Pipeline', 'Tu dong hoa quy trinh kiem thu va trien khai ung dung.'), " +
                "('Docker cho Nguoi moi bat dau', 'Huong dan dong goi ung dung Spring Boot va MySQL voi Docker.'), " +
                "('Bao mat CSDL MySQL', 'Huong dan phan quyen va ma hoa du lieu trong MySQL.'), " +
                "('Phan tich Payload Ma doc', 'Cach phan tich cac chuoi payload SQLi trong thuc te.'), " +
                "('RESTful API Design Standards', 'Quy chuan thiet ke API chuan RESTful cho doanh nghiep.'), " +
                "('Tong quan ve Web Application Firewall', 'Vai tro cua WAF trong viec ngan chan cac cuoc tan cong Web.');");

            res.put("status", "success");
        } catch (Exception e) {
            res.put("status", "error");
        }
        return res;
    }

    private String sanitizeComment(String input) {
        if (input == null) return "";
        if (input.endsWith("--")) {
            return input + " ";
        }
        return input;
    }
}