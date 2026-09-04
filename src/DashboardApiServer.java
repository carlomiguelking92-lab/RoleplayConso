import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DashboardApiServer {

    public static final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();

    private static String getAuditWebhookUrl() {
        String env = System.getenv("AUDIT_WEBHOOK_URL");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return "https://discord.com/api/webhooks/1544605127950864466/T-FsN7Aa3fySEuMcXuYU31PU8rm3tefe71LdwcSRTJHQxNaWmprM-UaOKt2CtrMoCS-n";
    }

    private static String getCertWebhookUrl() {
        String env = System.getenv("DISCORD_WEBHOOK_URL");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return "https://discord.com/api/webhooks/1543237954506465341/0anK5YKySOrgMKz2J9kDMX5O8B4HbwutjnuUxVpEGmmnXhPhpNuqCZnWycsNeIwvMaQI";
    }

    public static class UserSession {
        public String username;
        public String badgeId;
        public String department;
        public String role;
        public String status;

        public UserSession(String username, String badgeId, String department, String role, String status) {
            this.username = username;
            this.badgeId = badgeId;
            this.department = department;
            this.role = role;
            this.status = status;
        }
    }

    public static class UnpaidSummaryInfo {
        public int count = 0;
        public double totalBalance = 0.0;
        public String formattedList = "";
    }

    public static void main(String[] args) {
        startServer(8080);
    }

    public static void startServer(int port) {
        String renderPort = System.getenv("PORT");
        if (renderPort != null && !renderPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(renderPort.trim());
            } catch (NumberFormatException ignored) {}
        }

        createTablesIfNotExists();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            
            server.createContext("/", exchange -> {
                if (handleCorsOptions(exchange)) return;
                setCorsHeaders(exchange);

                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path)) {
                    sendResponse(exchange, 200, "{\"status\": \"online\", \"message\": \"Region IX API Server is Running\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"404 Endpoint Not Found: " + escapeJson(path) + "\"}");
                }
            });

            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/activity", new ActivityHandler());
            server.createContext("/api/activity/pnp", new PnpActivityHandler());
            server.createContext("/api/activity/gov", new GovActivityHandler());

            server.createContext("/api/register", new RegisterHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/admin/pending-users", new PendingUsersHandler());
            server.createContext("/api/admin/update-user", new UpdateUserHandler());
            server.createContext("/api/admin/delete-user", new DeleteUserHandler());

            server.createContext("/api/officers", new OfficersHandler());
            server.createContext("/api/officers/add", new AddOfficerHandler());
            server.createContext("/api/officers/promote", new PromoteOfficerHandler());
            server.createContext("/api/officers/delete", new DeleteOfficerHandler());

            server.createContext("/api/government", new GovHandler());
            server.createContext("/api/government/add", new AddGovHandler());
            server.createContext("/api/government/delete", new DeleteGovHandler());

            server.createContext("/api/certificates", new CertificatesHandler());
            server.createContext("/api/certificates/generate", new GenerateCertificateHandler());

            server.createContext("/api/salary", new SalaryHandler());
            server.createContext("/api/salary/save", new SaveSalaryHandler());
            server.createContext("/api/salary/delete", new DeleteSalaryHandler());
            server.createContext("/api/salary/treasury", new TreasuryHandler());
            server.createContext("/api/salary/pre-release-check", new PreReleaseCheckHandler());
            server.createContext("/api/salary/transparency-report", new TransparencyReportHandler());
            server.createContext("/api/salary/webhook-audit", new WebhookAuditHandler());
            server.createContext("/api/salary/webhook-unpaid", new WebhookUnpaidHandler());

            // Registered Live Dispatch CAD Routes
            server.createContext("/api/cad/units", new CadUnitsHandler());
            server.createContext("/api/cad/calls", new CadCallsHandler());

            server.setExecutor(null);
            server.start();
            System.out.println("🌐 Dashboard API Server running on port " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String normalizeBadgeNo(String badge) {
        if (badge == null) return "";
        String b = badge.trim().toUpperCase();
        if (b.startsWith("O09-")) {
            return "09-O" + b.substring(4);
        }
        return b;
    }

    private static UnpaidSummaryInfo getUnpaidSummaryFromDb(String filterDept) {
        UnpaidSummaryInfo info = new UnpaidSummaryInfo();
        StringBuilder sb = new StringBuilder();

        String sql = "SELECT department, title, name, badge_id, base_salary, incentives FROM salaries WHERE LOWER(status) = 'unpaid' ORDER BY id ASC";

        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String dept = rs.getString("department");
                if (filterDept != null && !filterDept.equalsIgnoreCase("ALL") && !filterDept.equalsIgnoreCase(dept)) {
                    continue;
                }

                String title = rs.getString("title");
                String name = rs.getString("name");
                String badge = rs.getString("badge_id");
                double base = rs.getDouble("base_salary");
                double inc = rs.getDouble("incentives");
                double weekly = (base / 4.0) + inc;

                info.count++;
                info.totalBalance += weekly;

                sb.append(String.format(Locale.US, "• `[%s]` **%s** (%s) - ₱%.2f\n", badge, name, title, weekly));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (sb.length() > 0) {
            info.formattedList = sb.toString();
            if (info.formattedList.length() > 950) {
                info.formattedList = info.formattedList.substring(0, 920) + "\n... (truncated due to Discord character limits)";
            }
        } else {
            info.formattedList = "No unpaid personnel.";
        }

        return info;
    }

    private static void createTablesIfNotExists() {
        String usersSql = "CREATE TABLE IF NOT EXISTS staff_users (" +
                          "id INT AUTO_INCREMENT PRIMARY KEY, " +
                          "username VARCHAR(100) UNIQUE NOT NULL, " +
                          "password VARCHAR(255) NOT NULL, " +
                          "badge_id VARCHAR(50) UNIQUE NOT NULL, " +
                          "department VARCHAR(20) NOT NULL, " +
                          "role VARCHAR(20) NOT NULL DEFAULT 'MEMBER', " +
                          "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                          "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                          ")";

        String officersSql = "CREATE TABLE IF NOT EXISTS officers (" +
                             "id INT AUTO_INCREMENT PRIMARY KEY, " +
                             "badge_no VARCHAR(50) UNIQUE NOT NULL, " +
                             "name VARCHAR(255) NOT NULL, " +
                             "`rank` VARCHAR(100) NOT NULL, " +
                             "points INT DEFAULT 0" +
                             ")";

        String govSql = "CREATE TABLE IF NOT EXISTS government_members (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "id_no VARCHAR(50) UNIQUE NOT NULL, " +
                        "name VARCHAR(255) NOT NULL, " +
                        "position VARCHAR(255) NOT NULL, " +
                        "points INT DEFAULT 0" +
                        ")";

        String logsSql = "CREATE TABLE IF NOT EXISTS activity_logs (" +
                         "id INT AUTO_INCREMENT PRIMARY KEY, " +
                         "action_type VARCHAR(255) NOT NULL, " +
                         "user_details VARCHAR(255) NOT NULL, " +
                         "department VARCHAR(10) NOT NULL DEFAULT 'PNP', " +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                         ")";

        String certsSql = "CREATE TABLE IF NOT EXISTS certificates (" +
                          "id INT AUTO_INCREMENT PRIMARY KEY, " +
                          "ref_code VARCHAR(50) UNIQUE NOT NULL, " +
                          "personnel_name VARCHAR(255) NOT NULL, " +
                          "id_or_badge VARCHAR(50) NOT NULL, " +
                          "template_type VARCHAR(255) NOT NULL, " +
                          "issued_date VARCHAR(50) NOT NULL, " +
                          "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                          ")";

        String salarySql = "CREATE TABLE IF NOT EXISTS salaries (" +
                           "id INT AUTO_INCREMENT PRIMARY KEY, " +
                           "department VARCHAR(20) NOT NULL, " +
                           "title VARCHAR(100) NOT NULL, " +
                           "name VARCHAR(255) NOT NULL, " +
                           "badge_id VARCHAR(50) NOT NULL UNIQUE, " +
                           "base_salary DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                           "incentives DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                           "status VARCHAR(20) NOT NULL DEFAULT 'unpaid', " +
                           "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                           "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                           ")";

        String treasurySql = "CREATE TABLE IF NOT EXISTS treasury (" +
                             "id INT PRIMARY KEY DEFAULT 1, " +
                             "amount DECIMAL(12,2) NOT NULL DEFAULT 0.00, " +
                             "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                             ")";

        String cadUnitsSql = "CREATE TABLE IF NOT EXISTS cad_units (" +
                             "id INT AUTO_INCREMENT PRIMARY KEY, " +
                             "identifier VARCHAR(50) NOT NULL UNIQUE, " +
                             "name VARCHAR(100) NOT NULL, " +
                             "department VARCHAR(20) NOT NULL, " +
                             "status VARCHAR(20) DEFAULT '10-7', " +
                             "assigned_call_id VARCHAR(50) DEFAULT NULL, " +
                             "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                             ")";

        String cadCalloutsSql = "CREATE TABLE IF NOT EXISTS cad_callouts (" +
                               "id INT AUTO_INCREMENT PRIMARY KEY, " +
                               "call_id VARCHAR(50) NOT NULL UNIQUE, " +
                               "title VARCHAR(150) NOT NULL, " +
                               "location VARCHAR(150) NOT NULL, " +
                               "priority VARCHAR(20) DEFAULT 'MEDIUM', " +
                               "status VARCHAR(20) DEFAULT 'PENDING', " +
                               "details TEXT, " +
                               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                               ")";

        try (Connection conn = PointsDatabaseHelper.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(usersSql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(officersSql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(govSql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(logsSql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(certsSql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(salarySql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(treasurySql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(cadUnitsSql)) { pstmt.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(cadCalloutsSql)) { pstmt.executeUpdate(); }

            try (PreparedStatement pstmt = conn.prepareStatement("INSERT IGNORE INTO treasury (id, amount) VALUES (1, 0.00)")) {
                pstmt.executeUpdate();
            }

            try {
                conn.createStatement().executeUpdate("ALTER TABLE staff_users DROP INDEX password");
            } catch (Exception ignored) {}
            
            try {
                conn.createStatement().executeUpdate("ALTER TABLE staff_users DROP INDEX password_2");
            } catch (Exception ignored) {}

            try {
                conn.createStatement().executeUpdate("ALTER TABLE staff_users ADD UNIQUE INDEX idx_badge_id (badge_id)");
            } catch (Exception ignored) {}

            System.out.println("✅ Database tables, CAD system, treasury, and UNIQUE constraints verified.");
        } catch (Exception e) {
            System.err.println("❌ Table Creation Error:");
            e.printStackTrace();
        }
    }

    public static UserSession getSession(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return activeSessions.get(authHeader.substring(7));
        }
        return null;
    }

    public static boolean isSuperAdmin(HttpExchange exchange) {
        UserSession s = getSession(exchange);
        return s != null && "SUPER_ADMIN".equalsIgnoreCase(s.role) && "APPROVED".equalsIgnoreCase(s.status);
    }

    public static boolean isAdminOrSuperAdmin(HttpExchange exchange, String requiredDept) {
        UserSession s = getSession(exchange);
        if (s == null || !"APPROVED".equalsIgnoreCase(s.status)) return false;
        if ("SUPER_ADMIN".equalsIgnoreCase(s.role)) return true;
        if ("ADMIN".equalsIgnoreCase(s.role)) {
            return requiredDept == null || requiredDept.equalsIgnoreCase(s.department);
        }
        return false;
    }

    public static void logActivity(String action, String user) {
        logActivity(action, user, "PNP");
    }

    public static void logActivity(String action, String user, String department) {
        String deptStr = (department != null && !department.trim().isEmpty()) ? department.toUpperCase() : "PNP";
        String sql = "INSERT INTO activity_logs (action_type, user_details, department) VALUES (?, ?, ?)";
        
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, action);
            pstmt.setString(2, user);
            pstmt.setString(3, deptStr);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Error inserting activity log:");
            e.printStackTrace();
        }

        sendAuditLogToDiscord(action, user, deptStr);
    }

    private static void sendAuditLogToDiscord(String action, String userDetails, String department) {
        new Thread(() -> {
            try {
                int embedColor = "GOV".equalsIgnoreCase(department) ? 0x10b981 : 0x2563eb;

                String jsonPayload = String.format(
                    "{\"username\":\"Region IX Audit Logger\"," +
                    "\"embeds\":[{" +
                        "\"title\":\"🚨 System Audit Log\"," +
                        "\"color\":%d," +
                        "\"fields\":[" +
                            "{\"name\":\"Department\",\"value\":\"`%s`\",\"inline\":true}," +
                            "{\"name\":\"Action\",\"value\":\"%s\",\"inline\":false}," +
                            "{\"name\":\"Target / Personnel\",\"value\":\"%s\",\"inline\":false}" +
                        "]," +
                        "\"timestamp\":\"%s\"," +
                        "\"footer\":{\"text\":\"Region IX Dashboard Security Audit\"}" +
                    "}]}",
                    embedColor,
                    escapeJson(department),
                    escapeJson(action),
                    escapeJson(userDetails),
                    java.time.Instant.now().toString()
                );

                sendRawDiscordJson(jsonPayload);
            } catch (Exception e) {
                System.err.println("❌ Failed to send audit log to Discord webhook: " + e.getMessage());
            }
        }).start();
    }

    private static void sendRawDiscordJson(String jsonPayload) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getAuditWebhookUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            System.err.println("❌ Discord Webhook Error (" + response.statusCode() + "): " + response.body());
            throw new IOException("Discord Webhook Error (" + response.statusCode() + "): " + response.body());
        } else {
            System.out.println("✅ Discord Webhook Notification Sent Successfully!");
        }
    }

    private static boolean isLieutenantOrAbove(String rank) {
        if (rank == null) return false;
        String r = rank.toUpperCase();
        return r.contains("LT") || r.contains("LIEUTENANT") || 
               r.contains("CAPT") || r.contains("CAPTAIN") || 
               r.contains("MAJ") || r.contains("MAJOR") || 
               r.contains("COL") || r.contains("COLONEL") || 
               r.contains("GEN") || r.contains("GENERAL");
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);

            String user = params.getOrDefault("username", "").trim();
            String pass = params.getOrDefault("password", "").trim();
            String rawBadge = params.getOrDefault("badgeId", "").trim().toUpperCase();

            if (user.isEmpty() || pass.isEmpty() || rawBadge.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Username, password, and Badge/ID No are required.\"}");
                return;
            }

            String normalizedBadge = normalizeBadgeNo(rawBadge);
            String dept = rawBadge.startsWith("GO") ? "GOV" : "PNP";

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                String checkUserSql = "SELECT username FROM staff_users WHERE LOWER(username) = LOWER(?)";
                try (PreparedStatement checkUserStmt = conn.prepareStatement(checkUserSql)) {
                    checkUserStmt.setString(1, user);
                    try (ResultSet rs = checkUserStmt.executeQuery()) {
                        if (rs.next()) {
                            sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Username '" + escapeJson(user) + "' is already registered.\"}");
                            return;
                        }
                    }
                }

                String checkBadgeSql = "SELECT badge_id FROM staff_users WHERE badge_id = ? OR badge_id = ?";
                try (PreparedStatement checkBadgeStmt = conn.prepareStatement(checkBadgeSql)) {
                    checkBadgeStmt.setString(1, rawBadge);
                    checkBadgeStmt.setString(2, normalizedBadge);
                    try (ResultSet rs = checkBadgeStmt.executeQuery()) {
                        if (rs.next()) {
                            sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Badge/ID No '" + escapeJson(rawBadge) + "' is already registered to another account.\"}");
                            return;
                        }
                    }
                }

                boolean idExists = false;
                if ("GOV".equalsIgnoreCase(dept)) {
                    String checkGov = "SELECT COUNT(*) FROM government_members WHERE id_no = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(checkGov)) {
                        stmt.setString(1, rawBadge);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) idExists = true;
                        }
                    }
                } else {
                    String checkPnp = "SELECT COUNT(*) FROM officers WHERE badge_no = ? OR badge_no = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(checkPnp)) {
                        stmt.setString(1, rawBadge);
                        stmt.setString(2, normalizedBadge);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) idExists = true;
                        }
                    }
                }

                if (!idExists) {
                    sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Registration rejected: Badge/ID No does not exist in the official roster.\"}");
                    return;
                }

                boolean isSuperAdminBadge = normalizedBadge.equalsIgnoreCase("09-O01002") || rawBadge.equalsIgnoreCase("O09-01002");
                String assignedRole = isSuperAdminBadge ? "SUPER_ADMIN" : "MEMBER";
                String assignedStatus = isSuperAdminBadge ? "APPROVED" : "PENDING";

                String sql = "INSERT INTO staff_users (username, password, badge_id, department, role, status) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, user);
                    pstmt.setString(2, pass);
                    pstmt.setString(3, normalizedBadge);
                    pstmt.setString(4, dept);
                    pstmt.setString(5, assignedRole);
                    pstmt.setString(6, assignedStatus);
                    pstmt.executeUpdate();

                    logActivity("Account Registered (" + assignedRole + ")", user + " [" + normalizedBadge + "]", dept);

                    if (isSuperAdminBadge) {
                        sendResponse(exchange, 201, "{\"status\":\"success\",\"message\":\"Super Admin account auto-approved! You can now log in.\"}");
                    } else {
                        sendResponse(exchange, 201, "{\"status\":\"success\",\"message\":\"Registration submitted! Awaiting Super Admin approval.\"}");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Registration error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);

            String user = params.getOrDefault("username", "").trim();
            String pass = params.getOrDefault("password", "").trim();

            String sql = "SELECT badge_id, department, role, status FROM staff_users WHERE username = ? AND password = ?";
            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, user);
                pstmt.setString(2, pass);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String badgeId = rs.getString("badge_id");
                        String dept = rs.getString("department");
                        String role = rs.getString("role");
                        String status = rs.getString("status");

                        if ("REJECTED".equalsIgnoreCase(status)) {
                            sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Your application was rejected.\"}");
                            return;
                        }

                        String token = UUID.randomUUID().toString();
                        activeSessions.put(token, new UserSession(user, badgeId, dept, role, status));

                        String response = String.format(
                            "{\"status\":\"success\",\"token\":\"%s\",\"username\":\"%s\",\"badgeId\":\"%s\",\"department\":\"%s\",\"role\":\"%s\",\"accountStatus\":\"%s\"}",
                            token, escapeJson(user), escapeJson(badgeId), escapeJson(dept), escapeJson(role), escapeJson(status)
                        );
                        sendResponse(exchange, 200, response);
                    } else {
                        sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid username or password.\"}");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Database error during login.\"}");
            }
        }
    }

    static class PendingUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isSuperAdmin(exchange)) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Super Admin access required.\"}");
                return;
            }

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT id, username, badge_id, department, role, status, created_at FROM staff_users WHERE role != 'SUPER_ADMIN' ORDER BY id DESC";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format(
                        "{\"id\":%d,\"username\":\"%s\",\"badgeId\":\"%s\",\"department\":\"%s\",\"role\":\"%s\",\"status\":\"%s\",\"created_at\":\"%s\"}",
                        rs.getInt("id"),
                        escapeJson(rs.getString("username")),
                        escapeJson(rs.getString("badge_id")),
                        escapeJson(rs.getString("department")),
                        escapeJson(rs.getString("role")),
                        escapeJson(rs.getString("status")),
                        escapeJson(rs.getString("created_at"))
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, 200, jsonArray.toString());
        }
    }

    static class UpdateUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isSuperAdmin(exchange)) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Super Admin access required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);

            String userIdStr = params.getOrDefault("userId", "0");
            String newRole = params.getOrDefault("role", "MEMBER").toUpperCase();
            String newStatus = params.getOrDefault("status", "APPROVED").toUpperCase();

            String sql = "UPDATE staff_users SET role = ?, status = ? WHERE id = ? AND role != 'SUPER_ADMIN'";
            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newRole);
                pstmt.setString(2, newStatus);
                pstmt.setInt(3, Integer.parseInt(userIdStr));
                pstmt.executeUpdate();

                logActivity("User Permission Updated", "User ID #" + userIdStr + " -> Role: " + newRole + " | Status: " + newStatus, "PNP");
                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"User permissions updated.\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Failed to update user.\"}");
            }
        }
    }

    static class DeleteUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isSuperAdmin(exchange)) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Super Admin access required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST") && !exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);
            
            int userId;
            try {
                userId = Integer.parseInt(params.getOrDefault("userId", "0"));
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid user ID parameter.\"}");
                return;
            }

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                String fetchSql = "SELECT badge_id, username FROM staff_users WHERE id = ? AND role != 'SUPER_ADMIN'";
                String targetBadge = null;
                String targetUser = null;

                try (PreparedStatement fetchStmt = conn.prepareStatement(fetchSql)) {
                    fetchStmt.setInt(1, userId);
                    try (ResultSet rs = fetchStmt.executeQuery()) {
                        if (rs.next()) {
                            targetBadge = rs.getString("badge_id");
                            targetUser = rs.getString("username");
                        } else {
                            sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"User not found or is a protected Super Admin.\"}");
                            return;
                        }
                    }
                }

                String deleteSql = "DELETE FROM staff_users WHERE id = ? AND role != 'SUPER_ADMIN'";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    pstmt.setInt(1, userId);
                    pstmt.executeUpdate();
                }

                if (targetBadge != null) {
                    final String badgeToRevoke = targetBadge;
                    activeSessions.entrySet().removeIf(entry -> badgeToRevoke.equalsIgnoreCase(entry.getValue().badgeId));
                }

                logActivity("User Account Deleted", "Deleted User: " + targetUser + " [ID #" + userId + "]", "PNP");
                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"User deleted and active sessions revoked.\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Failed to delete user account.\"}");
            }
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            int totalOfficers = 0, totalGovMembers = 0;

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM officers");
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) totalOfficers = rs.getInt(1);
                } catch (Exception e) {
                    System.err.println("❌ Error counting officers:");
                    e.printStackTrace();
                }

                try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM government_members");
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) totalGovMembers = rs.getInt(1);
                } catch (Exception e) {
                    System.err.println("❌ Error counting gov members:");
                    e.printStackTrace();
                }
            } catch (Exception e) {
                System.err.println("❌ Database Connection Error in StatsHandler:");
                e.printStackTrace();
            }

            String jsonResponse = String.format("{\"total_officers\": %d, \"total_gov_members\": %d}", totalOfficers, totalGovMembers);
            sendResponse(exchange, jsonResponse);
        }
    }

    static class ActivityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT action_type, user_details, department FROM activity_logs ORDER BY created_at DESC LIMIT 10";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                int count = 1;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format("{\"id\": %d, \"action\": \"%s\", \"user\": \"%s\", \"department\": \"%s\"}",
                            count++, rs.getString("action_type"), rs.getString("user_details"), rs.getString("department")));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading activity_logs:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class PnpActivityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT action_type, user_details FROM activity_logs WHERE department = 'PNP' ORDER BY created_at DESC LIMIT 10";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                int count = 1;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format("{\"id\": %d, \"action\": \"%s\", \"user\": \"%s\"}",
                            count++, rs.getString("action_type"), rs.getString("user_details")));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading PNP activity_logs:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class GovActivityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT action_type, user_details FROM activity_logs WHERE department = 'GOV' ORDER BY created_at DESC LIMIT 10";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                int count = 1;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format("{\"id\": %d, \"action\": \"%s\", \"user\": \"%s\"}",
                            count++, rs.getString("action_type"), rs.getString("user_details")));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading GOV activity_logs:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class OfficersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT badge_no, name, `rank`, points FROM officers ORDER BY points DESC";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format("{\"badge\": \"%s\", \"name\": \"%s\", \"rank\": \"%s\", \"points\": %d}",
                            escapeJson(rs.getString("badge_no")),
                            escapeJson(rs.getString("name")),
                            escapeJson(rs.getString("rank")),
                            rs.getInt("points")));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading officers:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class AddOfficerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isAdminOrSuperAdmin(exchange, "PNP")) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Unauthorized: PNP Admin permissions required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);
            String name = params.getOrDefault("name", "").trim();
            String rank = params.getOrDefault("rank", "Patrolman").trim();

            if (name.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Name is required.\"}");
                return;
            }

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                int nextNumber = 1012;
                String maxSql = "SELECT MAX(CAST(REPLACE(REPLACE(badge_no, '09-', ''), 'O', '') AS UNSIGNED)) FROM officers";

                try (PreparedStatement maxStmt = conn.prepareStatement(maxSql);
                     ResultSet rs = maxStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        nextNumber = Math.max(1012, rs.getInt(1) + 1);
                    }
                }

                boolean isCommissioned = isLieutenantOrAbove(rank);
                String prefix = isCommissioned ? "09-O" : "09-";
                String badgeNo;

                do {
                    badgeNo = String.format("%s%05d", prefix, nextNumber);
                    String checkSql = "SELECT COUNT(*) FROM officers WHERE badge_no = ?";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setString(1, badgeNo);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                nextNumber++;
                            } else {
                                break;
                            }
                        }
                    }
                } while (true);

                String insertSql = "INSERT INTO officers (badge_no, name, `rank`, points) VALUES (?, ?, ?, 0)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, badgeNo);
                    pstmt.setString(2, name);
                    pstmt.setString(3, rank);
                    pstmt.executeUpdate();
                }

                logActivity("Added New Officer (" + badgeNo + ")", name + " (" + rank + ")", "PNP");
                sendResponse(exchange, 200, String.format("{\"status\":\"success\",\"badge\":\"%s\"}", badgeNo));

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class PromoteOfficerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isAdminOrSuperAdmin(exchange, "PNP")) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Unauthorized: PNP Admin permissions required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);
            String currentBadge = params.getOrDefault("badge", "").trim();
            String newRank = params.getOrDefault("newRank", "").trim();

            if (currentBadge.isEmpty() || newRank.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Badge and new rank are required.\"}");
                return;
            }

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                String updatedBadge = currentBadge;

                if (isLieutenantOrAbove(newRank)) {
                    if (!currentBadge.contains("O")) {
                        updatedBadge = currentBadge.replace("09-", "09-O");
                    }
                } else {
                    updatedBadge = currentBadge.replace("09-O", "09-").replace("O09-", "09-");
                }

                if (!updatedBadge.equalsIgnoreCase(currentBadge)) {
                    String checkSql = "SELECT COUNT(*) FROM officers WHERE badge_no = ?";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setString(1, updatedBadge);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Promotion failed: Target Badge " + updatedBadge + " already exists.\"}");
                                return;
                            }
                        }
                    }
                }

                String updateSql = "UPDATE officers SET `rank` = ?, badge_no = ? WHERE badge_no = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, newRank);
                    pstmt.setString(2, updatedBadge);
                    pstmt.setString(3, currentBadge);
                    pstmt.executeUpdate();
                }

                logActivity("Promoted Officer to " + newRank, updatedBadge, "PNP");
                sendResponse(exchange, 200, String.format("{\"status\":\"success\",\"newBadge\":\"%s\"}", updatedBadge));

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class DeleteOfficerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isAdminOrSuperAdmin(exchange, "PNP")) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Unauthorized: PNP Admin permissions required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST") && !exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);
            String badgeNo = params.getOrDefault("badge", "").trim();
            if (badgeNo.isEmpty()) {
                badgeNo = params.getOrDefault("badgeNo", "").trim();
            }

            if (badgeNo.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Badge number is required.\"}");
                return;
            }

            String sql = "DELETE FROM officers WHERE badge_no = ?";
            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, badgeNo);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    logActivity("Officer Deleted", "Badge: " + badgeNo, "PNP");
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Officer deleted successfully.\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"Officer badge not found.\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Failed to delete officer.\"}");
            }
        }
    }

    static class GovHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT id_no, name, position, points FROM government_members ORDER BY points DESC";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format("{\"id\": \"%s\", \"name\": \"%s\", \"position\": \"%s\", \"points\": %d}",
                            escapeJson(rs.getString("id_no")),
                            escapeJson(rs.getString("name")),
                            escapeJson(rs.getString("position")),
                            rs.getInt("points")));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading government_members:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class AddGovHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isAdminOrSuperAdmin(exchange, "GOV")) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Unauthorized: GOV Admin permissions required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);
            String name = params.getOrDefault("name", "").trim();
            String position = params.getOrDefault("position", "").trim();

            if (name.isEmpty() || position.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Name and position are required.\"}");
                return;
            }

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                int nextNumber = 7;
                String maxSql = "SELECT MAX(CAST(REPLACE(id_no, 'GO-', '') AS UNSIGNED)) FROM government_members";

                try (PreparedStatement maxStmt = conn.prepareStatement(maxSql);
                     ResultSet rs = maxStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        nextNumber = Math.max(7, rs.getInt(1) + 1);
                    }
                }

                String idNo;
                do {
                    idNo = String.format("GO-%04d", nextNumber);
                    String checkSql = "SELECT COUNT(*) FROM government_members WHERE id_no = ?";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setString(1, idNo);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                nextNumber++;
                            } else {
                                break;
                            }
                        }
                    }
                } while (true);

                String insertSql = "INSERT INTO government_members (id_no, name, position, points) VALUES (?, ?, ?, 0)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, idNo);
                    pstmt.setString(2, name);
                    pstmt.setString(3, position);
                    pstmt.executeUpdate();
                }

                logActivity("Added New Gov Staff (" + idNo + ")", name + " (" + position + ")", "GOV");
                sendResponse(exchange, 200, String.format("{\"status\":\"success\",\"id\":\"%s\"}", idNo));

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class DeleteGovHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!isAdminOrSuperAdmin(exchange, "GOV")) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Unauthorized: GOV Admin permissions required.\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST") && !exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);
            String idNo = params.getOrDefault("id", "").trim();
            if (idNo.isEmpty()) {
                idNo = params.getOrDefault("idNo", "").trim();
            }

            if (idNo.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"ID number is required.\"}");
                return;
            }

            String sql = "DELETE FROM government_members WHERE id_no = ?";
            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, idNo);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    logActivity("Gov Staff Deleted", "ID: " + idNo, "GOV");
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Government member deleted successfully.\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"Government member ID not found.\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Failed to delete government member.\"}");
            }
        }
    }

    static class CertificatesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT ref_code, personnel_name, id_or_badge, template_type, issued_date FROM certificates ORDER BY id DESC";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;
                    jsonArray.append(String.format(
                        "{\"ref_code\":\"%s\",\"personnel_name\":\"%s\",\"id_or_badge\":\"%s\",\"template_type\":\"%s\",\"issued_date\":\"%s\"}",
                        escapeJson(rs.getString("ref_code")),
                        escapeJson(rs.getString("personnel_name")),
                        escapeJson(rs.getString("id_or_badge")),
                        escapeJson(rs.getString("template_type")),
                        escapeJson(rs.getString("issued_date"))
                    ));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading certificates:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class GenerateCertificateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);

            String templateName = params.getOrDefault("template", "PNP - Certificate of Recognition");
            boolean isGov = templateName.startsWith("Gov Office");

            if (!isAdminOrSuperAdmin(exchange, isGov ? "GOV" : "PNP")) {
                sendResponse(exchange, 403, "{\"status\":\"error\",\"message\":\"Unauthorized: Insufficient certificate permissions.\"}");
                return;
            }

            String name = params.getOrDefault("name", "").trim();
            String title = params.getOrDefault("title", "").trim();
            String idNo = params.getOrDefault("idNo", "").trim();
            String reason = params.getOrDefault("reason", "").trim();

            if (name.isEmpty() || idNo.isEmpty() || reason.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\", \"message\":\"Missing required fields.\"}");
                return;
            }

            String templateFileName = resolveTemplateFileName(templateName);
            String idPrefix = isGov ? "ID NO: " : "BADGE NO: ";
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
            String discordTitle = title.isEmpty() ? name : title + " " + name;
            String certImageName = isGov ? name : discordTitle;

            try {
                String refNum = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String certSql = "INSERT INTO certificates (ref_code, personnel_name, id_or_badge, template_type, issued_date) VALUES (?, ?, ?, ?, ?)";
                
                try (Connection conn = PointsDatabaseHelper.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(certSql)) {
                    pstmt.setString(1, refNum);
                    pstmt.setString(2, discordTitle);
                    pstmt.setString(3, idNo);
                    pstmt.setString(4, templateName);
                    pstmt.setString(5, date);
                    pstmt.executeUpdate();
                }

                File templateFile = new File(templateFileName);
                if (!templateFile.exists()) {
                    templateFile = new File("certif.png");
                }

                BufferedImage image = ImageIO.read(templateFile);
                Graphics2D g2d = image.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics metrics;

                if (isGov) {
                    Font refFont = new Font("Arial", Font.BOLD, 20);
                    g2d.setFont(refFont);
                    g2d.setColor(new Color(120, 120, 120));
                    String refText = "NO: " + refNum;
                    metrics = g2d.getFontMetrics(refFont);
                    g2d.drawString(refText, image.getWidth() - metrics.stringWidth(refText) - 160, 140);

                    Font nameFont = new Font("Georgia", Font.BOLD, 52);
                    g2d.setFont(nameFont);
                    g2d.setColor(new Color(26, 26, 26));
                    String capsNameText = certImageName.toUpperCase();
                    metrics = g2d.getFontMetrics(nameFont);
                    g2d.drawString(capsNameText, (image.getWidth() - metrics.stringWidth(capsNameText)) / 2, 640);

                    Font idFont = new Font("Georgia", Font.BOLD, 22);
                    g2d.setFont(idFont);
                    g2d.setColor(new Color(90, 90, 90));
                    String idText = idPrefix + idNo;
                    metrics = g2d.getFontMetrics(idFont);
                    g2d.drawString(idText, (image.getWidth() - metrics.stringWidth(idText)) / 2, 710);

                    Font reasonFont = new Font("Georgia", Font.PLAIN, 46);
                    g2d.setFont(reasonFont);
                    g2d.setColor(new Color(40, 40, 40));
                    metrics = g2d.getFontMetrics(reasonFont);
                    drawWrappedText(g2d, reason.toUpperCase(), metrics, 1350, 770, image.getWidth());

                    Font dateFont = new Font("Georgia", Font.ITALIC, 24);
                    g2d.setFont(dateFont);
                    g2d.setColor(new Color(60, 60, 60));
                    String dateText = "Issued on " + date;
                    metrics = g2d.getFontMetrics(dateFont);
                    g2d.drawString(dateText, image.getWidth() - metrics.stringWidth(dateText) - 300, 1000);

                } else {
                    Font refFont = new Font("Arial", Font.BOLD, 18);
                    g2d.setFont(refFont);
                    g2d.setColor(new Color(100, 100, 100));
                    String refText = "NO: " + refNum;
                    metrics = g2d.getFontMetrics(refFont);
                    g2d.drawString(refText, image.getWidth() - metrics.stringWidth(refText) - 100, 90);

                    Font nameFont = new Font("Georgia", Font.BOLD, 52);
                    g2d.setFont(nameFont);
                    g2d.setColor(new Color(26, 26, 26));
                    String capsNameText = certImageName.toUpperCase();
                    metrics = g2d.getFontMetrics(nameFont);
                    g2d.drawString(capsNameText, (image.getWidth() - metrics.stringWidth(capsNameText)) / 2, 550);

                    Font badgeFont = new Font("Georgia", Font.BOLD, 22);
                    g2d.setFont(badgeFont);
                    g2d.setColor(new Color(80, 80, 80));
                    String badgeText = idPrefix + idNo;
                    metrics = g2d.getFontMetrics(badgeFont);
                    g2d.drawString(badgeText, (image.getWidth() - metrics.stringWidth(badgeText)) / 2, 600);

                    Font reasonFont = new Font("Georgia", Font.PLAIN, 52);
                    g2d.setFont(reasonFont);
                    g2d.setColor(new Color(40, 40, 40));
                    metrics = g2d.getFontMetrics(reasonFont);
                    drawWrappedText(g2d, reason.toUpperCase(), metrics, image.getWidth() - 300, 670, image.getWidth());

                    Font dateFont = new Font("Georgia", Font.ITALIC, 26);
                    g2d.setFont(dateFont);
                    g2d.setColor(new Color(26, 26, 26));
                    String dateText = "Issued on " + date;
                    metrics = g2d.getFontMetrics(dateFont);
                    g2d.drawString(dateText, image.getWidth() - metrics.stringWidth(dateText) - 250, 1020);
                }

                g2d.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] imageBytes = baos.toByteArray();

                sendToDiscord(discordTitle.toUpperCase(), idNo, reason, date, refNum, templateName, imageBytes);
                logActivity("Web Certificate Issued (" + templateName + ")", discordTitle, isGov ? "GOV" : "PNP");

                sendResponse(exchange, 200, String.format("{\"status\":\"success\",\"ref_code\":\"%s\"}", refNum));

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void drawWrappedText(Graphics2D g2d, String text, FontMetrics metrics, int maxWidth, int startY, int imageWidth) {
            String[] words = text.split(" ");
            StringBuilder currentLine = new StringBuilder();
            int lineHeight = metrics.getHeight() + 8;

            for (String word : words) {
                if (metrics.stringWidth(currentLine + " " + word) < maxWidth) {
                    if (currentLine.length() > 0) currentLine.append(" ");
                    currentLine.append(word);
                } else {
                    g2d.drawString(currentLine.toString(), (imageWidth - metrics.stringWidth(currentLine.toString())) / 2, startY);
                    startY += lineHeight;
                    currentLine = new StringBuilder(word);
                }
            }
            if (currentLine.length() > 0) {
                g2d.drawString(currentLine.toString(), (imageWidth - metrics.stringWidth(currentLine.toString())) / 2, startY);
            }
        }

        private String resolveTemplateFileName(String templateName) {
            switch (templateName) {
                case "PNP - Certificate of Recognition": return "certifi.png";
                case "PNP - Certificate of Achievement": return "certif.png";
                case "PNP - Certificate of Appreciation": return "certific.png";
                case "PNP - Certificate of Commendation": return "certifica.png";
                case "Gov Office - Certificate of Appreciation":
                case "Gov Office - General Certificate": return "govcert.png";
                default: return "govcerti.png";
            }
        }

        private void sendToDiscord(String fullTitle, String badge, String reason, String date, String refNum, String templateType, byte[] imageBytes) throws IOException, InterruptedException {
            String boundary = "----JavaBoundary" + UUID.randomUUID().toString();
            String messageText = String.format("📄 **New Certificate Generated via Web Dashboard!**\n**Template:** %s\n**Personnel:** %s\n**ID/Badge No:** `%s`\n**Reason:** %s\n**Date:** %s\n**Ref Code:** `%s`",
                    templateType, fullTitle, badge, reason, date, refNum);

            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bodyStream.write("Content-Disposition: form-data; name=\"content\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write((messageText + "\r\n").getBytes(StandardCharsets.UTF_8));

            bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bodyStream.write(String.format("Content-Disposition: form-data; name=\"file\"; filename=\"%s_Certificate.png\"\r\n", fullTitle.replace(" ", "_")).getBytes(StandardCharsets.UTF_8));
            bodyStream.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write(imageBytes);
            bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getCertWebhookUrl()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyStream.toByteArray()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new IOException("Discord Webhook error (" + response.statusCode() + "): " + response.body());
            }
        }
    }

    static class SalaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            StringBuilder jsonArray = new StringBuilder("[");
            String sql = "SELECT id, department, title, name, badge_id, base_salary, incentives, status FROM salaries ORDER BY id ASC";

            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonArray.append(",");
                    first = false;

                    double base = rs.getDouble("base_salary");
                    double inc = rs.getDouble("incentives");
                    double weekly = base / 4.0;
                    double totalWk = weekly + inc;

                    jsonArray.append(String.format(
                        Locale.US,
                        "{\"id\":%d,\"department\":\"%s\",\"title\":\"%s\",\"name\":\"%s\",\"badge_id\":\"%s\",\"badgeId\":\"%s\",\"salary\":%.2f,\"baseSalary\":%.2f,\"weekly\":%.2f,\"weeklySalary\":%.2f,\"incentives\":%.2f,\"totalWk\":%.2f,\"totalPerWeek\":%.2f,\"status\":\"%s\"}",
                        rs.getInt("id"),
                        escapeJson(rs.getString("department")),
                        escapeJson(rs.getString("title")),
                        escapeJson(rs.getString("name")),
                        escapeJson(rs.getString("badge_id")),
                        escapeJson(rs.getString("badge_id")),
                        base, base,
                        weekly, weekly,
                        inc,
                        totalWk, totalWk,
                        escapeJson(rs.getString("status"))
                    ));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading salaries:");
                e.printStackTrace();
            }
            jsonArray.append("]");
            sendResponse(exchange, jsonArray.toString());
        }
    }

    static class SaveSalaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);

            String dept = params.getOrDefault("department", "").trim();
            String title = params.getOrDefault("title", "").trim();
            String name = params.getOrDefault("name", "").trim();
            String badgeId = params.getOrDefault("badgeId", "").trim();
            if (badgeId.isEmpty()) {
                badgeId = params.getOrDefault("badge_id", "").trim();
            }

            String status = params.getOrDefault("status", "unpaid").trim().toLowerCase();

            if (badgeId.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Badge/ID No is required.\"}");
                return;
            }

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                if (title.isEmpty() || name.isEmpty() || dept.isEmpty()) {
                    String findSql = "SELECT department, title, name, base_salary, incentives FROM salaries WHERE badge_id = ?";
                    try (PreparedStatement findStmt = conn.prepareStatement(findSql)) {
                        findStmt.setString(1, badgeId);
                        try (ResultSet rs = findStmt.executeQuery()) {
                            if (rs.next()) {
                                if (dept.isEmpty()) dept = rs.getString("department");
                                if (title.isEmpty()) title = rs.getString("title");
                                if (name.isEmpty()) name = rs.getString("name");
                            }
                        }
                    }
                }

                if (dept.isEmpty()) dept = badgeId.startsWith("GO") ? "GOV" : "PNP";
                if (title.isEmpty()) title = "Personnel";
                if (name.isEmpty()) name = badgeId;

                double baseSalary = 0.0;
                try {
                    baseSalary = Double.parseDouble(params.getOrDefault("baseSalary", "0").replaceAll("[^0-9.]", ""));
                } catch (Exception ignored) {}

                double incentives = 0.0;
                try {
                    incentives = Double.parseDouble(params.getOrDefault("incentives", "0").replaceAll("[^0-9.]", ""));
                } catch (Exception ignored) {}

                String sql = "INSERT INTO salaries (department, title, name, badge_id, base_salary, incentives, status) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE department = VALUES(department), title = VALUES(title), name = VALUES(name), base_salary = VALUES(base_salary), incentives = VALUES(incentives), status = VALUES(status)";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, dept.toUpperCase());
                    pstmt.setString(2, title);
                    pstmt.setString(3, name);
                    pstmt.setString(4, badgeId);
                    pstmt.setDouble(5, baseSalary);
                    pstmt.setDouble(6, incentives);
                    pstmt.setString(7, status);
                    pstmt.executeUpdate();

                    String logSql = "INSERT INTO activity_logs (action_type, user_details, department) VALUES (?, ?, ?)";
                    try (PreparedStatement logStmt = conn.prepareStatement(logSql)) {
                        logStmt.setString(1, "Updated Salary Status (" + badgeId + " -> " + status.toUpperCase() + ")");
                        logStmt.setString(2, name + " (" + title + ")");
                        logStmt.setString(3, dept.toUpperCase());
                        logStmt.executeUpdate();
                    }

                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Salary record saved successfully.\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class DeleteSalaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST") && !exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJsonBody(body);

            String badgeId = params.getOrDefault("badgeId", "").trim();
            if (badgeId.isEmpty()) {
                badgeId = params.getOrDefault("badge_id", "").trim();
            }

            if (badgeId.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Badge/ID No is required.\"}");
                return;
            }

            String sql = "DELETE FROM salaries WHERE badge_id = ?";
            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, badgeId);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    logActivity("Salary Record Deleted", "Badge: " + badgeId, badgeId.startsWith("GO") ? "GOV" : "PNP");
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Salary record deleted successfully.\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"Salary record not found.\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Failed to delete salary record.\"}");
            }
        }
    }

    static class WebhookAuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String badgeId = extractJsonField(body, "badgeId");
            if (badgeId.isEmpty()) badgeId = "N/A";

            String name = extractJsonField(body, "name");
            if (name.isEmpty()) name = "N/A";

            String title = extractJsonField(body, "title");
            if (title.isEmpty()) title = "N/A";

            String prevStatus = extractJsonField(body, "previousStatus");
            if (prevStatus.isEmpty()) prevStatus = "UNPAID";

            String newStatus = extractJsonField(body, "newStatus");
            if (newStatus.isEmpty()) newStatus = "UNPAID";

            String updatedBy = extractJsonField(body, "updatedBy");
            if (updatedBy.isEmpty()) updatedBy = "Admin";

            String amount = extractJsonField(body, "amount");
            if (amount.isEmpty()) amount = "₱0.00";

            UnpaidSummaryInfo summary = getUnpaidSummaryFromDb("ALL");

            int color = "PAID".equalsIgnoreCase(newStatus) ? 0x10b981 : ("INACTIVE".equalsIgnoreCase(newStatus) ? 0xf59e0b : 0xef4444);

            String jsonPayload = String.format(
                Locale.US,
                "{\"username\":\"Region IX Audit Logger\"," +
                "\"embeds\":[{" +
                    "\"title\":\"📝 Salary Status Changed\"," +
                    "\"color\":%d," +
                    "\"fields\":[" +
                        "{\"name\":\"Personnel\",\"value\":\"`[%s]` **%s** (%s)\",\"inline\":false}," +
                        "{\"name\":\"Status Update\",\"value\":\"`%s` ➔ `%s`\",\"inline\":true}," +
                        "{\"name\":\"Weekly Amount\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Updated By\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Remaining Unpaid Balance\",\"value\":\"**₱%.2f** (%d personnel remaining)\",\"inline\":false}," +
                        "{\"name\":\"Unpaid Members List\",\"value\":\"%s\",\"inline\":false}" +
                    "]," +
                    "\"timestamp\":\"%s\"," +
                    "\"footer\":{\"text\":\"Region IX Audit Channel Notification\"}" +
                "}]}",
                color,
                escapeJson(badgeId), escapeJson(name), escapeJson(title),
                escapeJson(prevStatus), escapeJson(newStatus),
                escapeJson(amount),
                escapeJson(updatedBy),
                summary.totalBalance, summary.count,
                escapeJson(summary.formattedList),
                java.time.Instant.now().toString()
            );

            new Thread(() -> {
                try {
                    WebhookNotifier.sendUnpaidSalaryNotification(jsonPayload);
                } catch (Exception e) {
                    System.err.println("⚠️ Discord Audit Webhook Warning: " + e.getMessage());
                }
            }).start();

            sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Audit webhook processed.\"}");
        }
    }

    static class WebhookUnpaidHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String viewFilter = extractJsonField(body, "viewFilter");
            if (viewFilter.isEmpty()) viewFilter = "ALL";

            String triggeredBy = extractJsonField(body, "triggeredBy");
            if (triggeredBy.isEmpty()) triggeredBy = "Admin";

            UnpaidSummaryInfo summary = getUnpaidSummaryFromDb(viewFilter);

            String jsonPayload = String.format(
                Locale.US,
                "{\"username\":\"Region IX Audit Logger\"," +
                "\"embeds\":[{" +
                    "\"title\":\"📋 Unpaid Salary & Remaining Balance Report\"," +
                    "\"color\":15658734," +
                    "\"description\":\"**Filter:** `%s` | **Triggered By:** `%s`\\n\\n**Remaining Unpaid Balance:** **₱%.2f** (%d Unpaid Personnel)\"," +
                    "\"fields\":[" +
                        "{\"name\":\"Unpaid Members Roster\",\"value\":\"%s\",\"inline\":false}" +
                    "]," +
                    "\"timestamp\":\"%s\"," +
                    "\"footer\":{\"text\":\"Region IX Salary Audit Report\"}" +
                "}]}",
                escapeJson(viewFilter),
                escapeJson(triggeredBy),
                summary.totalBalance,
                summary.count,
                escapeJson(summary.formattedList),
                java.time.Instant.now().toString()
            );

            new Thread(() -> {
                try {
                    WebhookNotifier.sendUnpaidSalaryNotification(jsonPayload);
                } catch (Exception e) {
                    System.err.println("⚠️ Discord Unpaid Webhook Warning: " + e.getMessage());
                }
            }).start();

            sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Unpaid summary webhook processed.\"}");
        }
    }

    static class TreasuryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseJsonBody(body);
                double amount = 0.0;
                try {
                    amount = Double.parseDouble(params.getOrDefault("amount", "0").replaceAll("[^0-9.]", ""));
                } catch (Exception ignored) {}

                try (Connection conn = PointsDatabaseHelper.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("UPDATE treasury SET amount = ? WHERE id = 1")) {
                    pstmt.setDouble(1, amount);
                    pstmt.executeUpdate();

                    logActivity("Treasury Balance Updated", String.format(Locale.US, "New Balance: ₱%.2f", amount), "PNP");
                    sendResponse(exchange, 200, String.format(Locale.US, "{\"status\":\"success\",\"amount\":%.2f}", amount));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            double amount = 0.0;
            try (Connection conn = PointsDatabaseHelper.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT amount FROM treasury WHERE id = 1");
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    amount = rs.getDouble("amount");
                }
            } catch (Exception ignored) {}

            sendResponse(exchange, 200, String.format(Locale.US, "{\"status\":\"success\",\"amount\":%.2f}", amount));
        }
    }

    static class PreReleaseCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            double treasuryAmount = 0.0;
            double totalPayrollDue = 0.0;
            int totalMembersCount = 0;

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT amount FROM treasury WHERE id = 1");
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) treasuryAmount = rs.getDouble("amount");
                }

                try (PreparedStatement pstmt = conn.prepareStatement("SELECT base_salary, incentives FROM salaries");
                     ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        double base = rs.getDouble("base_salary");
                        double inc = rs.getDouble("incentives");
                        totalPayrollDue += (base / 4.0) + inc;
                        totalMembersCount++;
                    }
                }

                String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
                boolean isSufficient = treasuryAmount >= totalPayrollDue;
                String statusStr = isSufficient ? "✅ Sufficient Funds Available for Disbursement" : "⚠️ WARNING: Treasury Deficit (Short by " + String.format(Locale.US, "₱%.2f", totalPayrollDue - treasuryAmount) + ")";
                int statusColor = isSufficient ? 0x2563eb : 0xef4444;

                String jsonPayload = String.format(
                    Locale.US,
                    "{\"username\":\"Region IX Treasury Logger\"," +
                    "\"embeds\":[{" +
                        "\"title\":\"🏦 Saturday Pre-Disbursement Balance Check\"," +
                        "\"color\":%d," +
                        "\"description\":\"**Official pre-release balance audit prior to Saturday payroll release.**\\nDate: %s\"," +
                        "\"fields\":[" +
                            "{\"name\":\"Current Treasury Pool\",\"value\":\"**₱%.2f**\",\"inline\":true}," +
                            "{\"name\":\"Total Payroll Due\",\"value\":\"**₱%.2f**\",\"inline\":true}," +
                            "{\"name\":\"Active Members\",\"value\":\"%d Personnel\",\"inline\":true}," +
                            "{\"name\":\"Disbursement Status\",\"value\":\"%s\",\"inline\":false}" +
                        "]," +
                        "\"timestamp\":\"%s\"," +
                        "\"footer\":{\"text\":\"Region IX Pre-Release Balance Audit\"}" +
                    "}]}",
                    statusColor,
                    escapeJson(dateStr),
                    treasuryAmount,
                    totalPayrollDue,
                    totalMembersCount,
                    escapeJson(statusStr),
                    java.time.Instant.now().toString()
                );

                new Thread(() -> {
                    try {
                        sendRawDiscordJson(jsonPayload);
                    } catch (Exception e) {
                        System.err.println("⚠️ Discord Pre-Release Check Warning: " + e.getMessage());
                    }
                }).start();

                logActivity("Saturday Pre-Disbursement Check Sent", String.format(Locale.US, "Pool: ₱%.2f | Due: ₱%.2f", treasuryAmount, totalPayrollDue), "PNP");

                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Pre-disbursement check processed.\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class TransparencyReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
                return;
            }

            double initialTreasury = 0.0;
            double totalWeeklyGross = 0.0;
            double totalPaidDisbursed = 0.0;
            double pnpPaid = 0.0;
            double govPaid = 0.0;
            int paidMembersCount = 0;
            int totalMembersCount = 0;

            try (Connection conn = PointsDatabaseHelper.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT amount FROM treasury WHERE id = 1");
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) initialTreasury = rs.getDouble("amount");
                }

                try (PreparedStatement pstmt = conn.prepareStatement("SELECT department, base_salary, incentives, status FROM salaries");
                     ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        totalMembersCount++;
                        String dept = rs.getString("department");
                        double base = rs.getDouble("base_salary");
                        double inc = rs.getDouble("incentives");
                        double weeklyTotal = (base / 4.0) + inc;
                        String status = rs.getString("status");

                        totalWeeklyGross += weeklyTotal;

                        if ("paid".equalsIgnoreCase(status)) {
                            totalPaidDisbursed += weeklyTotal;
                            paidMembersCount++;
                            if ("GOV".equalsIgnoreCase(dept)) {
                                govPaid += weeklyTotal;
                            } else {
                                pnpPaid += weeklyTotal;
                            }
                        }
                    }
                }

                double remainingUnpaid = Math.max(0, totalWeeklyGross - totalPaidDisbursed);
                double netRemainingTreasury = initialTreasury - totalPaidDisbursed;

                try (PreparedStatement pstmt = conn.prepareStatement("UPDATE treasury SET amount = ? WHERE id = 1")) {
                    pstmt.setDouble(1, netRemainingTreasury);
                    pstmt.executeUpdate();
                }

                try (PreparedStatement pstmt = conn.prepareStatement("UPDATE salaries SET status = 'unpaid'")) {
                    pstmt.executeUpdate();
                }

                String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));

                String jsonPayload = String.format(
                    Locale.US,
                    "{\"username\":\"Region IX Treasury Logger\"," +
                    "\"embeds\":[{" +
                        "\"title\":\"📊 Saturday Weekly Payroll & Transparency Report\"," +
                        "\"color\":1095809," +
                        "\"description\":\"**Official weekly salary disbursement breakdown for attended group members.**\\nDate: %s\"," +
                        "\"fields\":[" +
                            "{\"name\":\"Starting Treasury Pool\",\"value\":\"₱%.2f\",\"inline\":true}," +
                            "{\"name\":\"Total Weekly Payroll\",\"value\":\"₱%.2f\",\"inline\":true}," +
                            "{\"name\":\"Attended / Paid Members\",\"value\":\"%d / %d Personnel\",\"inline\":true}," +
                            "{\"name\":\"Total Disbursed (Paid)\",\"value\":\"**₱%.2f**\",\"inline\":true}," +
                            "{\"name\":\"PNP Paid\",\"value\":\"₱%.2f\",\"inline\":true}," +
                            "{\"name\":\"GOV Paid\",\"value\":\"₱%.2f\",\"inline\":true}," +
                            "{\"name\":\"Unpaid / Pending Balance\",\"value\":\"₱%.2f\",\"inline\":true}," +
                            "{\"name\":\"Net Remaining Treasury Pool\",\"value\":\"**₱%.2f**\",\"inline\":false}" +
                        "]," +
                        "\"timestamp\":\"%s\"," +
                        "\"footer\":{\"text\":\"Region IX Official Saturday Transparency Audit\"}" +
                    "}]}",
                    escapeJson(dateStr),
                    initialTreasury,
                    totalWeeklyGross,
                    paidMembersCount,
                    totalMembersCount,
                    totalPaidDisbursed,
                    pnpPaid,
                    govPaid,
                    remainingUnpaid,
                    netRemainingTreasury,
                    java.time.Instant.now().toString()
                );

                new Thread(() -> {
                    try {
                        sendRawDiscordJson(jsonPayload);
                    } catch (Exception e) {
                        System.err.println("⚠️ Discord Transparency Report Warning: " + e.getMessage());
                    }
                }).start();

                logActivity("Saturday Transparency Report Published", String.format(Locale.US, "Paid: ₱%.2f to %d members | Rem. Pool: ₱%.2f", totalPaidDisbursed, paidMembersCount, netRemainingTreasury), "PNP");

                sendResponse(exchange, 200, String.format(Locale.US, "{\"status\":\"success\",\"disbursed\":%.2f,\"remainingPool\":%.2f}", totalPaidDisbursed, netRemainingTreasury));
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // CAD MDT Unit Status Handler
    static class CadUnitsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String json = CadDatabaseHelper.getActiveUnitsJson();
                sendResponse(exchange, 200, json);
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
            }
        }
    }

    // CAD Emergency Calls Handler
    static class CadCallsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsOptions(exchange)) return;
            setCorsHeaders(exchange);

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String json = CadDatabaseHelper.getActiveCallsJson();
                sendResponse(exchange, 200, json);
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed.\"}");
            }
        }
    }

    private static String extractJsonField(String json, String key) {
        if (json == null || key == null || json.trim().isEmpty()) return "";
        String searchKey = "\"" + key + "\":";
        int index = json.indexOf(searchKey);
        if (index == -1) return "";

        int start = index + searchKey.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return "";

        if (json.charAt(start) == '"') {
            start++;
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') {
                end++;
            }
            return json.substring(start, end).trim().replaceAll("^\"|\"$", "");
        }
    }

    private static Map<String, String> parseJsonBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;

        String sanitized = body.trim();
        if (sanitized.startsWith("{")) sanitized = sanitized.substring(1);
        if (sanitized.endsWith("}")) sanitized = sanitized.substring(0, sanitized.length() - 1);

        String[] pairs = sanitized.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
        }
        return map;
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "")
                    .replace("\n", "\\n")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\t", "\\t");
    }

    private static boolean handleCorsOptions(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        sendResponse(exchange, 200, response);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        setCorsHeaders(exchange);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}