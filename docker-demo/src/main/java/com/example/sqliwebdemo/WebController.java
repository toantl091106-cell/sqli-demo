package com.example.sqliwebdemo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    private final SessionAuth auth;
    private final JdbcTemplate jdbc;

    public WebController(SessionAuth auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/account")
    public String account(HttpServletRequest request, Model model) {
        var user = auth.current(request);
        if (user == null) return "redirect:/?loginRequired=1";
        model.addAttribute("user", user);
        model.addAttribute("mode", auth.mode(request));
        model.addAttribute("bypass", auth.bypass(request));
        return "account";
    }

    @GetMapping("/admin")
    public String admin(HttpServletRequest request, HttpServletResponse response, Model model) {
        var user = auth.current(request);
        if (user == null) return "redirect:/?loginRequired=1";
        boolean allowed = "admin".equals(user.get("role"));
        response.setStatus(allowed ? 200 : 403);
        model.addAttribute("allowed", allowed);
        model.addAttribute("user", user);
        if (allowed) model.addAttribute("users", jdbc.queryForList("SELECT id, username, email, role FROM users ORDER BY id"));
        return "admin";
    }
}
