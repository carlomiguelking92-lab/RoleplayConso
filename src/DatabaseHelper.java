import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseHelper {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/roleplay_conso?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USER = "root";      // Update with your MySQL username
    private static final String DB_PASS = "";  // Update with your MySQL password

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static class Personnel {
        private String idNo;
        private String name;
        private String title; // Rank or Position

        public Personnel(String idNo, String name, String title) {
            this.idNo = idNo;
            this.name = name;
            this.title = title;
        }

        public String getIdNo() { return idNo; }
        public String getName() { return name; }
        public String getTitle() { return title; }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // --- CERTIFICATES LOG & VERIFICATION METHODS ---

    public static boolean isRefCodeExists(String refCode) {
        String sql = "SELECT COUNT(*) FROM certificates WHERE ref_code = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, refCode);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String generateUniqueRefCode() {
        String refCode;
        do {
            refCode = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (isRefCodeExists(refCode));
        return refCode;
    }

    public static boolean saveCertificateRecord(String refCode, String personnelName, String idNo, String template, String date) {
        String sql = "INSERT INTO certificates (ref_code, personnel_name, id_or_badge, template_type, issued_date) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, refCode);
            pstmt.setString(2, personnelName);
            pstmt.setString(3, idNo);
            pstmt.setString(4, template);
            pstmt.setString(5, date);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String[]> getAllCertificates() {
        List<String[]> list = new ArrayList<>();
        String query = "SELECT ref_code, personnel_name, id_or_badge, template_type, issued_date FROM certificates ORDER BY id DESC";
        try (Connection conn = getConnection(); 
             Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("ref_code"),
                    rs.getString("personnel_name"),
                    rs.getString("id_or_badge"),
                    rs.getString("template_type"),
                    rs.getString("issued_date")
                });
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
        return list;
    }

    // --- PNP OFFICERS TABLE METHODS (officers) ---

    public static List<Personnel> getAllPNPOfficers() {
        List<Personnel> list = new ArrayList<>();
        String query = "SELECT badge_no, name, rank FROM officers ORDER BY name ASC";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(new Personnel(rs.getString("badge_no"), rs.getString("name"), rs.getString("rank")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static boolean savePNPOfficer(String badgeNo, String name, String rank) {
        String sql = "INSERT INTO officers (badge_no, name, rank) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name=?, rank=?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, badgeNo.trim()); pstmt.setString(2, name.trim()); pstmt.setString(3, rank.trim());
            pstmt.setString(4, name.trim()); pstmt.setString(5, rank.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public static boolean deletePNPOfficer(String badgeNo) {
        String sql = "DELETE FROM officers WHERE badge_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, badgeNo);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // --- GOVERNMENT MEMBERS TABLE METHODS (government_members) ---

    public static List<Personnel> getAllGovMembers() {
        List<Personnel> list = new ArrayList<>();
        String query = "SELECT id_no, name, position FROM government_members ORDER BY name ASC";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(new Personnel(rs.getString("id_no"), rs.getString("name"), rs.getString("position")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static boolean saveGovMember(String idNo, String name, String position) {
        String sql = "INSERT INTO government_members (id_no, name, position) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name=?, position=?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, idNo.trim()); pstmt.setString(2, name.trim()); pstmt.setString(3, position.trim());
            pstmt.setString(4, name.trim()); pstmt.setString(5, position.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public static boolean deleteGovMember(String idNo) {
        String sql = "DELETE FROM government_members WHERE id_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, idNo);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
    
    public static boolean deleteCertificateRecord(String refCode) {
        String sql = "DELETE FROM certificates WHERE ref_code = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, refCode);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }
    
    public static boolean updatePNPOfficer(String oldBadgeNo, String newBadgeNo, String name, String rank) {
        String sql = "UPDATE officers SET badge_no = ?, name = ?, rank = ? WHERE badge_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newBadgeNo.trim());
            pstmt.setString(2, name.trim());
            pstmt.setString(3, rank.trim());
            pstmt.setString(4, oldBadgeNo.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean linkDiscordAccount(String badgeNo, String discordId) {
        String sql = "UPDATE officers SET discord_id = ? WHERE badge_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, discordId);
            pstmt.setString(2, badgeNo.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get Badge No using Discord User ID
    public static String getBadgeByDiscordId(String discordId) {
        String sql = "SELECT badge_no FROM officers WHERE discord_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, discordId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("badge_no");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}