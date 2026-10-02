package com.example.sqliwebdemo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Statement;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SessionAuth auth;
    private final DemoData demoData;

    public ApiController(JdbcTemplate jdbc, TransactionTemplate transactions, SessionAuth auth, DemoData demoData) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.auth = auth;
        this.demoData = demoData;
    }

    // Public observation panel for the local teaching lab, distinct from protected account pages.
    @GetMapping("/data")
    public Map<String, Object> data() {
        try {
            return Map.of("users", users(), "posts", posts());
        } catch (DataAccessException e) {
            return Map.of("error", databaseError(e));
        }
    }

    @PostMapping("/execute")
    public synchronized Map<String, Object> execute(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String scenario = payload.getOrDefault("scenario", "");
        String input1 = Objects.toString(payload.get("input1"), "");
        String input2 = Objects.toString(payload.get("input2"), "");
        String input3 = Objects.toString(payload.get("input3"), "");
        boolean prepared = "true".equals(payload.get("secure"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", prepared ? "prepared" : "vulnerable");
        result.put("sql", "");
        result.put("status", "success");
        if ("1".equals(scenario)) auth.logout(request);
        if (scenario == null || !Set.of("1", "2", "3", "4", "5").contains(scenario)) {
            return rejected(result, "Kịch bản không hợp lệ.");
        }
        try {
            return transactions.execute(tx -> {
                switch (scenario) {
                    case "1" -> login(result, request, prepared, input1, input2);
                    case "2" -> search(result, prepared, input1);
                    case "3" -> insert(result, prepared, input1, input2, input3);
                    case "4" -> update(result, prepared, input1);
                    case "5" -> delete(result, prepared, input1);
                    default -> throw new IllegalStateException("Unexpected scenario");
                }
                return result;
            });
        } catch (DataAccessException | TransactionException e) {
            // Do not leave successful mutation/authentication evidence in a rolled-back response.
            if (e instanceof DataAccessException || e instanceof CannotCreateTransactionException) result.put("affectedRows", 0);
            else result.remove("affectedRows");
            result.remove("rows");
            result.remove("user");
            result.remove("rowCount");
            result.remove("postsBefore");
            result.remove("postsAfter");
            if ("1".equals(scenario)) {
                auth.logout(request);
                result.put("authenticated", false);
                result.put("bypassDetected", false);
            }
            result.put("status", "error");
            result.put("outcome", e instanceof TransactionException ? "transaction_error" : "sql_error");
            result.put("message", (e instanceof CannotCreateTransactionException
                    ? "Không kết nối được CSDL để bắt đầu giao dịch; thao tác chưa được thực hiện. "
                    : e instanceof TransactionException
                    ? "Không xác nhận được giao dịch hoàn tất. Kiểm tra lại dữ liệu trước khi thử lại. "
                    : "Truy vấn thất bại; thao tác dữ liệu đã được hoàn tác. ") + databaseError(e));
            return result;
        }
    }

    private void login(Map<String, Object> result, HttpServletRequest request, boolean prepared, String username, String password) {
        String sql = prepared ? "SELECT * FROM users WHERE username = ? AND password = ?"
                : "SELECT * FROM users WHERE username = '" + comment(username) + "' AND password = '" + comment(password) + "'";
        result.put("sql", sql);
        List<Map<String, Object>> rows = prepared ? jdbc.queryForList(sql, username, password) : jdbc.queryForList(sql);
        result.put("rowCount", rows.size());
        result.put("authenticated", !rows.isEmpty());
        if (rows.isEmpty()) {
            result.put("status", "rejected");
            result.put("outcome", "authentication_failed");
            result.put("bypassDetected", false);
            result.put("message", "Đăng nhập thất bại. Truy vấn không tìm thấy tài khoản phù hợp; phiên đăng nhập cũ đã được xóa.");
            return;
        }
        Map<String, Object> account = rows.get(0);
        // Compare through the same database collation, not Java's case-sensitive equality.
        // A bypass exists only if bound credentials would not authenticate this returned account.
        var credentialMatches = prepared ? rows
                : jdbc.queryForList("SELECT id FROM users WHERE username = ? AND password = ?", username, password);
        long accountId = ((Number) account.get("id")).longValue();
        boolean bypass = credentialMatches.stream()
                .noneMatch(candidate -> ((Number) candidate.get("id")).longValue() == accountId);
        auth.login(request, account, prepared ? "prepared" : "vulnerable", bypass);
        result.put("user", SessionAuth.publicUser(account));
        result.put("bypassDetected", bypass);
        result.put("outcome", "authenticated");
        result.put("message", bypass
                ? "Vượt kiểm tra thông tin đăng nhập: truy vấn trả về tài khoản dù thông tin nhập không khớp. Đã tạo phiên của " + account.get("username") + "."
                : "Thông tin đăng nhập hợp lệ. Đã tạo phiên của " + account.get("username") + ".");
        result.put("protectedUrl", "/account");
    }

    private void search(Map<String, Object> result, boolean prepared, String input) {
        String sql = prepared ? "SELECT id, title, content FROM posts WHERE title LIKE ?"
                : "SELECT id, title, content FROM posts WHERE title LIKE '%" + comment(input) + "%'";
        result.put("sql", sql);
        List<Map<String, Object>> rows = prepared ? jdbc.queryForList(sql, "%" + input + "%") : jdbc.queryForList(sql);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("outcome", rows.isEmpty() ? "no_results" : "results_found");
        result.put("message", "Truy vấn trả về " + rows.size() + " dòng. Xem dữ liệu kết quả để xác định thông tin đã được trả về.");
    }

    private void insert(Map<String, Object> result, boolean prepared, String username, String password, String email) {
        String sql = prepared ? "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, 'user')"
                : "INSERT INTO users (username, password, email, role) VALUES ('" + comment(username) + "', '" + comment(password) + "', '" + comment(email) + "', 'user')";
        result.put("sql", sql);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        int affected = jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            if (prepared) {
                statement.setString(1, username);
                statement.setString(2, password);
                statement.setString(3, email);
            }
            return statement;
        }, key);
        result.put("affectedRows", affected);
        result.put("outcome", affected > 0 ? "inserted" : "no_change");
        List<Map<String, Object>> created = key.getKey() == null ? List.of()
                : jdbc.queryForList("SELECT id, username, email, role FROM users WHERE id = ?", key.getKey().longValue());
        result.put("rows", created);
        String actualRole = created.isEmpty() ? "chưa xác định" : Objects.toString(created.get(0).get("role"));
        result.put("message", "Đã thêm " + affected + " tài khoản. Quyền thực tế của tài khoản vừa thêm: " + actualRole + ".");
    }

    private void update(Map<String, Object> result, boolean prepared, String email) {
        List<Map<String, Object>> before = users();
        String sql = prepared ? "UPDATE users SET email = ? WHERE id = 2"
                : "UPDATE users SET email = '" + comment(email) + "' WHERE id = 2";
        result.put("sql", sql);
        int matched = prepared ? jdbc.update(sql, email) : jdbc.update(sql);
        List<Map<String, Object>> after = users();
        List<Map<String, Object>> changed = new ArrayList<>();
        Map<Object, Map<String, Object>> original = new HashMap<>();
        before.forEach(user -> original.put(user.get("id"), user));
        after.forEach(user -> { if (!user.equals(original.get(user.get("id")))) changed.add(user); });
        result.put("matchedRows", matched);
        result.put("affectedRows", changed.size());
        result.put("rows", changed);
        result.put("outcome", changed.isEmpty() ? "no_change" : "updated");
        result.put("message", changed.isEmpty() ? "Không có dữ liệu thay đổi. Tài khoản không tồn tại hoặc giá trị đã giống dữ liệu hiện tại."
                : "Đã thay đổi " + changed.size() + " tài khoản. Xem email và quyền sau thao tác ở dữ liệu kết quả.");
    }

    private void delete(Map<String, Object> result, boolean prepared, String input) {
        String sql = prepared ? "DELETE FROM posts WHERE id = ?" : "DELETE FROM posts WHERE id = " + comment(input);
        result.put("sql", sql);
        Long id = null;
        if (prepared) {
            try {
                if (!input.matches("[1-9][0-9]*")) throw new NumberFormatException();
                id = Long.parseLong(input);
                if (id > Integer.MAX_VALUE) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                rejected(result, "ID bài viết phải là số nguyên dương. Không chạy truy vấn xóa.");
                return;
            }
        }
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM posts", Long.class);
        int affected = prepared ? jdbc.update(sql, id) : jdbc.update(sql);
        long after = jdbc.queryForObject("SELECT COUNT(*) FROM posts", Long.class);
        result.put("affectedRows", affected);
        result.put("postsBefore", before);
        result.put("postsAfter", after);
        result.put("outcome", affected > 0 ? "deleted" : "no_change");
        result.put("message", affected == 0 ? "Không có bài viết phù hợp; đã xóa 0 bài viết."
                : "Đã xóa " + affected + " bài viết; còn " + after + " bài viết. Bảng users không bị thay đổi.");
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        Map<String, Object> user = auth.current(request);
        if (user == null) return Map.of("authenticated", false);
        return Map.of("authenticated", true, "user", user, "mode", auth.mode(request), "bypassDetected", auth.bypass(request));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        auth.logout(request);
        return Map.of("status", "success", "authenticated", false, "message", "Đã đăng xuất và hủy phiên đăng nhập.");
    }

    @GetMapping("/account")
    public Map<String, Object> account(HttpServletRequest request) {
        return Map.of("user", requireUser(request), "message", "Thông tin tài khoản chỉ được trả về khi có phiên đăng nhập hợp lệ.");
    }

    @GetMapping("/admin/users")
    public Map<String, Object> admin(HttpServletRequest request) {
        Map<String, Object> user = requireUser(request);
        if (!"admin".equals(user.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cần quyền admin");
        return Map.of("users", users(), "viewer", user);
    }

    @PostMapping("/reset")
    public synchronized Map<String, Object> reset(HttpServletRequest request) {
        try {
            demoData.reset();
            auth.invalidateAll();
            auth.logout(request);
            return Map.of("status", "success", "message", "Đã khôi phục dữ liệu mẫu và hủy các phiên đăng nhập cũ.");
        } catch (DataAccessException | TransactionException e) {
            return Map.of("status", "error", "message", (e instanceof CannotCreateTransactionException
                    ? "Không kết nối được CSDL; chưa chạy khôi phục dữ liệu. "
                    : e instanceof TransactionException
                    ? "Không xác nhận được khôi phục hoàn tất. Kiểm tra lại dữ liệu trước khi thử lại. "
                    : "Khôi phục thất bại; dữ liệu đã được hoàn tác. ") + databaseError(e));
        }
    }

    private Map<String, Object> requireUser(HttpServletRequest request) {
        Map<String, Object> user = auth.current(request);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");
        return user;
    }

    private List<Map<String, Object>> users() { return jdbc.queryForList("SELECT id, username, email, role FROM users ORDER BY id"); }
    private List<Map<String, Object>> posts() { return jdbc.queryForList("SELECT id, title, content FROM posts ORDER BY id"); }
    private static Map<String, Object> rejected(Map<String, Object> result, String message) {
        result.put("status", "rejected");
        result.put("outcome", "invalid_input");
        result.put("affectedRows", 0);
        result.put("message", message);
        return result;
    }
    private static String comment(String input) { return input.endsWith("--") ? input + " " : input; }
    private static String databaseError(NestedRuntimeException e) { return Objects.toString(e.getMostSpecificCause().getMessage(), "Lỗi cơ sở dữ liệu"); }
}
