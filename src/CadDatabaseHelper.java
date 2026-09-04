import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class CadDatabaseHelper {

    /**
     * Update unit status (e.g. 10-8, 10-6, 10-97, 10-7)
     */
    public static boolean updateUnitStatus(String identifier, String status, String callId) {
        String sql = "INSERT INTO cad_units (identifier, name, department, status, assigned_call_id) " +
                     "VALUES (?, ?, 'PNP', ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status = VALUES(status), assigned_call_id = VALUES(assigned_call_id)";
        
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, identifier);
            pstmt.setString(2, "Officer " + identifier);
            pstmt.setString(3, status);
            pstmt.setString(4, callId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create a new emergency callout with auto-incremented ID.
     * Returns the generated call_id integer, or -1 on failure.
     */
    public static int createCallout(String title, String location, String priority, String details) {
        String sql = "INSERT INTO cad_callouts (title, location, priority, details) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, title);
            pstmt.setString(2, location);
            pstmt.setString(3, priority);
            pstmt.setString(4, details);
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1); // Returns auto-generated ID
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Fetch all units as JSON for the web dashboard
     */
    public static String getActiveUnitsJson() {
        String sql = "SELECT * FROM cad_units ORDER BY last_updated DESC";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                String assignedCall = rs.getString("assigned_call_id");
                
                json.append(String.format(
                    "{\"identifier\":\"%s\",\"name\":\"%s\",\"department\":\"%s\",\"status\":\"%s\",\"assigned_call\":\"%s\"}",
                    escapeJson(rs.getString("identifier")),
                    escapeJson(rs.getString("name")),
                    escapeJson(rs.getString("department")),
                    escapeJson(rs.getString("status")),
                    assignedCall != null ? escapeJson(assignedCall) : "NONE"
                ));
                first = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Fetch active callouts as JSON for the web dashboard
     */
    public static String getActiveCallsJson() {
        String sql = "SELECT * FROM cad_callouts WHERE status != 'CLEARED' ORDER BY created_at DESC";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format(
                    "{\"call_id\":\"%s\",\"title\":\"%s\",\"location\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\"}",
                    escapeJson(rs.getString("call_id")),
                    escapeJson(rs.getString("title")),
                    escapeJson(rs.getString("location")),
                    escapeJson(rs.getString("priority")),
                    escapeJson(rs.getString("status"))
                ));
                first = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        json.append("]");
        return json.toString();
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
}