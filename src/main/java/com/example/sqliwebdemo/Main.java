package com.example.sqliwebdemo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

public class Main {
    private static Scanner scanner = new Scanner(System.in);

    // Hàm lấy kết nối CSDL trực tiếp, không cần phụ thuộc file DBContext
    private static Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/sqli_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "root",
            "123456" // Doi thanh mat khau MySQL cua ban neu khac
        );
    }

    public static void main(String[] args) {
        while (true) {
            System.out.println("\n================ KICH BAN DEMO SQL INJECTION ================");
            System.out.println("1. Kich ban 1: Bypass Authentication (Vuot qua dang nhap)");
            System.out.println("2. Kich ban 2: Data Exfiltration (Danh cap du lieu - Select/UNION)");
            System.out.println("3. Kich ban 3: Insert Injection (Them du lieu trai phep)");
            System.out.println("4. Kich ban 4: Update Injection (Sua du lieu / Nang quyen)");
            System.out.println("5. Kich ban 5: Delete Injection (Xoa toan bo du lieu)");
            System.out.println("6. Khoi phuc du lieu ban dau (Reset Database)");
            System.out.println("0. Thoat chuong trinh");
            System.out.print("Moi chon kich ban (0-6): ");
            
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                switch (choice) {
                    case 1: demoBypassLogin(); break;
                    case 2: demoDataExfiltration(); break;
                    case 3: demoInsertInjection(); break;
                    case 4: demoUpdateInjection(); break;
                    case 5: demoDeleteInjection(); break;
                    case 6: resetDatabase(); break;
                    case 0: System.exit(0);
                    default: System.out.println("Lua chon khong hop le!");
                }
            } catch (Exception e) {
                System.out.println("Vui long nhap so hop le!");
            }
        }
    }

    // =========================================================================
    // KICH BAN 1: BYPASS AUTHENTICATION
    // =========================================================================
    private static void demoBypassLogin() {
        System.out.println("\n--- [KICH BAN 1: VUOT QUA DANG NHAP] ---");
        System.out.print("Nhap Username (Goi y Payload: ' OR '1'='1 ): ");
        String username = scanner.nextLine();
        System.out.print("Nhap Password (Nhap tuy y): ");
        String password = scanner.nextLine();

        System.out.println("\n--- [1. Code Bi Loi (Statement)] ---");
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            String sql = "SELECT * FROM users WHERE username = '" + username + "' AND password = '" + password + "'";
            System.out.println("[SQL Executed]: " + sql);
            
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                System.out.println("=> KET QUA: DANG NHAP THANH CONG! User: " + rs.getString("username") + " (Role: " + rs.getString("role") + ")");
            } else {
                System.out.println("=> KET QUA: Dang nhap that bai!");
            }
        } catch (Exception e) { System.out.println("Loi SQL: " + e.getMessage()); }

        System.out.println("\n--- [2. Code An Toan (PreparedStatement)] ---");
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                System.out.println("=> KET QUA: Dang nhap thanh cong!");
            } else {
                System.out.println("=> KET QUA: DANG NHAP THAT BAI (Ngan chan SQLi thanh cong)!");
            }
        } catch (Exception e) { System.out.println("Loi SQL: " + e.getMessage()); }
    }

    // =========================================================================
    // KICH BAN 2: DATA EXFILTRATION (SELECT / UNION)
    // =========================================================================
    private static void demoDataExfiltration() {
        System.out.println("\n--- [KICH BAN 2: TIM KIEM BAI VIET & DANH CAP DU LIEU] ---");
        System.out.print("Nhap tu khoa tim kiem (Goi y Payload: %' UNION SELECT id, username, password FROM users # ): ");
        String keyword = scanner.nextLine();

        System.out.println("\n--- [Code Bi Loi (Statement)] ---");
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            String sql = "SELECT id, title, content FROM posts WHERE title LIKE '%" + keyword + "%'";
            System.out.println("[SQL Executed]: " + sql);
            
            ResultSet rs = stmt.executeQuery(sql);
            System.out.println("\n[KET QUA TRA VE]:");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt(1) + " | Title/Col2: " + rs.getString(2) + " | Content/Col3: " + rs.getString(3));
            }
        } catch (Exception e) { System.out.println("Loi SQL: " + e.getMessage()); }
    }

    // =========================================================================
    // KICH BAN 3: INSERT INJECTION (THEM TAI KHOAN TRAI PHEP)
    // =========================================================================
    private static void demoInsertInjection() {
        System.out.println("\n--- [KICH BAN 3: DANG KY TAI KHOAN MOI] ---");
        System.out.print("Nhap Username (VD: hacker): ");
        String username = scanner.nextLine();
        System.out.print("Nhap Password (VD: 123456): ");
        String password = scanner.nextLine();
        System.out.print("Nhap Email (Goi y Payload: hacker@gmail.com', 'admin ): ");
        String email = scanner.nextLine();

        System.out.println("\n--- [Code Bi Loi (Statement)] ---");
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            
            String sql = "INSERT INTO users (username, password, email, role) VALUES ('" 
                         + username + "', '" + password + "', '" + email + "', 'user')";
            System.out.println("[SQL Executed]: " + sql);
            
            int rows = stmt.executeUpdate(sql);
            if (rows > 0) {
                System.out.println("=> Them user thanh cong! Hay vao CSDL kiem tra cot role.");
            }
        } catch (Exception e) { System.out.println("Loi SQL: " + e.getMessage()); }
    }

    // =========================================================================
    // KICH BAN 4: UPDATE INJECTION (NANG QUYEN)
    // =========================================================================
    private static void demoUpdateInjection() {
        System.out.println("\n--- [KICH BAN 4: CAP NHAT EMAIL CA NHAN (USER ID = 2)] ---");
        System.out.print("Nhap Email moi (Goi y Payload: hacker@gmail.com', role='admin ): ");
        String email = scanner.nextLine();

        System.out.println("\n--- [Code Bi Loi (Statement)] ---");
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            String sql = "UPDATE users SET email = '" + email + "' WHERE id = 2";
            System.out.println("[SQL Executed]: " + sql);
            
            int rows = stmt.executeUpdate(sql);
            if (rows > 0) {
                System.out.println("=> Cap nhat email thanh cong! Hay vao CSDL kiem tra role user id = 2.");
            }
        } catch (Exception e) { System.out.println("Loi SQL: " + e.getMessage()); }
    }

    // =========================================================================
    // KICH BAN 5: DELETE INJECTION (XOA DU LIEU)
    // =========================================================================
    private static void demoDeleteInjection() {
        System.out.println("\n--- [KICH BAN 5: XOA BAI VIET THEO ID] ---");
        System.out.print("Nhap ID bai viet (Goi y Payload xoa sach DB: 1 OR 1=1 ): ");
        String id = scanner.nextLine();

        System.out.println("\n--- [Code Bi Loi (Statement)] ---");
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            String sql = "DELETE FROM posts WHERE id = " + id;
            System.out.println("[SQL Executed]: " + sql);
            
            int rows = stmt.executeUpdate(sql);
            System.out.println("=> Da xoa " + rows + " ban ghi khoi bang posts!");
        } catch (Exception e) { System.out.println("Loi SQL: " + e.getMessage()); }
    }

    // =========================================================================
    // TINH NANG PHU: RESET DATABASE KHI DEMO BI XOA HET DU LIEU
    // =========================================================================
    private static void resetDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0;");
            stmt.executeUpdate("TRUNCATE TABLE posts;");
            stmt.executeUpdate("TRUNCATE TABLE users;");
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1;");
            
            stmt.executeUpdate("INSERT INTO users (username, password, email, role) VALUES " +
                    "('admin', 'admin123', 'admin@gmail.com', 'admin'), " +
                    "('user1', 'pass123', 'user1@gmail.com', 'user');");
                    
            stmt.executeUpdate("INSERT INTO posts (title, content) VALUES " +
                    "('Bai viet 1', 'Noi dung 1'), " +
                    "('Bai viet 2', 'Noi dung 2');");
                    
            System.out.println("=> DA KHOI PHUC DU LIEU BAN DAU THANH CONG!");
        } catch (Exception e) { System.out.println("Loi Reset: " + e.getMessage()); }
    }
}