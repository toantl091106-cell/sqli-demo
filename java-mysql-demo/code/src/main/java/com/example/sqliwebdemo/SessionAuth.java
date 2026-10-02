package com.example.sqliwebdemo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SessionAuth {
    private final JdbcTemplate jdbc;
    private final AtomicLong generation = new AtomicLong();

    public SessionAuth(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void login(HttpServletRequest request, Map<String, Object> account, String mode, boolean bypass) {
        logout(request);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute("demo.userId", ((Number) account.get("id")).longValue());
        session.setAttribute("demo.username", account.get("username"));
        session.setAttribute("demo.generation", generation.get());
        session.setAttribute("demo.mode", mode);
        session.setAttribute("demo.bypass", bypass);
    }

    public Map<String, Object> current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("demo.userId") == null) return null;
        if (!Long.valueOf(generation.get()).equals(session.getAttribute("demo.generation"))) {
            logout(request);
            return null;
        }
        // Always read current role from the database: a stale session must not retain removed privileges.
        var accounts = jdbc.queryForList("SELECT id, username, email, role FROM users WHERE id = ? AND username = ?",
                session.getAttribute("demo.userId"), session.getAttribute("demo.username"));
        if (accounts.isEmpty()) {
            logout(request);
            return null;
        }
        return publicUser(accounts.get(0));
    }

    public String mode(HttpServletRequest request) {
        return (String) request.getSession(false).getAttribute("demo.mode");
    }

    public boolean bypass(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getSession(false).getAttribute("demo.bypass"));
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    public void invalidateAll() { generation.incrementAndGet(); }

    public static Map<String, Object> publicUser(Map<String, Object> account) {
        Map<String, Object> user = new LinkedHashMap<>();
        for (String column : new String[]{"id", "username", "email", "role"}) user.put(column, account.get(column));
        return user;
    }
}
