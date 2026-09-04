import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GovDatabaseHelper {

    public static boolean linkGovAccount(String idNo, String discordId) {
        // Only update if discord_id is NULL or already matches the requesting user
        String updateSql = "UPDATE government_members SET discord_id = ? WHERE id_no = ? AND (discord_id IS NULL OR discord_id = ?)";
        String checkSql = "SELECT discord_id FROM government_members WHERE id_no = ?";

        try (Connection conn = PointsDatabaseHelper.getConnection()) {
            
            // 1. Attempt safe update (prevents overwriting another user's binding)
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, discordId);
                pstmt.setString(2, idNo);
                pstmt.setString(3, discordId);
                int rowsUpdated = pstmt.executeUpdate();
                if (rowsUpdated > 0) return true;
            }

            // 2. If update affected 0 rows, check if record exists with another Discord ID
            try (PreparedStatement checkPstmt = conn.prepareStatement(checkSql)) {
                checkPstmt.setString(1, idNo);
                try (ResultSet rs = checkPstmt.executeQuery()) {
                    if (rs.next()) {
                        // Record exists but is bound to someone else -> reject link
                        return false; 
                    }
                }
            }

            // 3. If record does not exist at all, insert new member record
            String insertSql = "INSERT INTO government_members (id_no, name, position, points, discord_id) VALUES (?, 'Gov Staff', 'Staff', 0, ?)";
            try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                insertPstmt.setString(1, idNo);
                insertPstmt.setString(2, discordId);
                return insertPstmt.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getIdByDiscordId(String discordId) {
        String sql = "SELECT id_no FROM government_members WHERE discord_id = ?";
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, discordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("id_no");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getPointsById(String idNo) {
        String sql = "SELECT points FROM government_members WHERE id_no = ?";
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, idNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("points");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static String getPositionById(String idNo) {
        String sql = "SELECT position FROM government_members WHERE id_no = ?";
        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, idNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("position");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String addGovPoints(String idNo, int pointsToAdd, String reason, String mention) {
        String updateSql = "UPDATE government_members SET points = points + ? WHERE id_no = ?";
        String selectSql = "SELECT name, position, points FROM government_members WHERE id_no = ?";

        try (Connection conn = PointsDatabaseHelper.getConnection()) {
            try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
                updatePstmt.setInt(1, pointsToAdd);
                updatePstmt.setString(2, idNo);
                int rows = updatePstmt.executeUpdate();

                if (rows == 0) {
                    String insertSql = "INSERT INTO government_members (id_no, name, position, points) VALUES (?, 'Gov Staff', 'Staff', ?)";
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, idNo);
                        insertPstmt.setInt(2, Math.max(0, pointsToAdd));
                        insertPstmt.executeUpdate();
                    }
                }
            }

            try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql)) {
                selectPstmt.setString(1, idNo);
                try (ResultSet rs = selectPstmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        String position = rs.getString("position");
                        int totalPoints = rs.getInt("points");

                        return "🏛️ **Gov Activity Logged!**\n" +
                               "• **Member:** " + mention + " (`" + name + "` - `" + idNo + "`)\n" +
                               "• **Position:** " + position + "\n" +
                               "• **Points Added:** `+" + pointsToAdd + " pts` (" + reason + ")\n" +
                               "• **Total Points:** `" + totalPoints + " pts`";
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String addCustomGovPoints(String idNo, int pointsToAdd, String reason, String mention) {
        String updateSql = "UPDATE government_members SET points = points + ? WHERE id_no = ?";
        String selectSql = "SELECT name, position, points FROM government_members WHERE id_no = ?";

        try (Connection conn = PointsDatabaseHelper.getConnection()) {
            try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
                updatePstmt.setInt(1, pointsToAdd);
                updatePstmt.setString(2, idNo);
                int rows = updatePstmt.executeUpdate();

                if (rows == 0) {
                    String insertSql = "INSERT INTO government_members (id_no, name, position, points) VALUES (?, 'Gov Staff', 'Staff', ?)";
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, idNo);
                        insertPstmt.setInt(2, Math.max(0, pointsToAdd));
                        insertPstmt.executeUpdate();
                    }
                }
            }

            try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql)) {
                selectPstmt.setString(1, idNo);
                try (ResultSet rs = selectPstmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        String position = rs.getString("position");
                        int totalPoints = rs.getInt("points");

                        String actionStr = pointsToAdd >= 0 ? "+" + pointsToAdd : String.valueOf(pointsToAdd);

                        return "🏛️ **Gov Admin Adjustment!**\n" +
                               "• **Member:** " + mention + " (`" + name + "` - `" + idNo + "`)\n" +
                               "• **Position:** " + position + "\n" +
                               "• **Points Adjusted:** `" + actionStr + " pts` (" + reason + ")\n" +
                               "• **Total Points:** `" + totalPoints + " pts`";
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getGovLeaderboard() {
        String sql = "SELECT id_no, name, position, points, discord_id FROM government_members ORDER BY points DESC LIMIT 10";
        StringBuilder sb = new StringBuilder("🏛️ **Government Office Leaderboard:**\n\n");

        try (Connection conn = PointsDatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            int rankPosition = 1;
            while (rs.next()) {
                String idNo = rs.getString("id_no");
                String name = rs.getString("name");
                String position = rs.getString("position");
                int points = rs.getInt("points");
                String discordId = rs.getString("discord_id");

                String userMention = (discordId != null && !discordId.trim().isEmpty()) ? "<@" + discordId + ">" : "`" + name + "`";

                sb.append("`").append(rankPosition).append(".` ")
                  .append(userMention).append(" (`").append(idNo).append("`) - **")
                  .append(position).append("** | `").append(points).append(" pts`\n");

                rankPosition++;
            }

            if (rankPosition == 1) {
                return "ℹ️ No government members found in the database.";
            }
            return sb.toString();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "❌ **Error:** Failed to fetch Government leaderboard.";
    }
}