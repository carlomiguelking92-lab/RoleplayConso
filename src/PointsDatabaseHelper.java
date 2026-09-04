import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PointsDatabaseHelper {

    // Reads environment variables on Render, or falls back to default values
    private static final String DB_HOST = System.getenv("DB_HOST") != null 
            ? System.getenv("DB_HOST") 
            : "mysql-3272a288-carlomiguelking93-a176.f.aivencloud.com";

    private static final String DB_PORT = System.getenv("DB_PORT") != null 
            ? System.getenv("DB_PORT") 
            : "17577";

    private static final String DB_NAME = System.getenv("DB_NAME") != null 
            ? System.getenv("DB_NAME") 
            : "defaultdb";

    private static final String DB_USER = System.getenv("DB_USER") != null 
            ? System.getenv("DB_USER") 
            : "avnadmin";

    // Corrected DB_PASS fallback
    private static final String DB_PASS = System.getenv("DB_PASS") != null 
            ? System.getenv("DB_PASS") 
            : "AVNS_KG-eoi0GF2BkwXvY6wM";

    private static HikariDataSource dataSource = null;

    private static synchronized HikariDataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            
            // Appended serverTimezone=Asia/Manila to sync timestamps with Philippine Standard Time
            String jdbcUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + 
                             "?useSSL=true" +
                             "&requireSSL=true" +
                             "&verifyServerCertificate=false" +
                             "&sslMode=REQUIRED" +
                             "&allowPublicKeyRetrieval=true" +
                             "&autoReconnect=true" +
                             "&connectTimeout=30000" +
                             "&serverTimezone=Asia/Manila";

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASS);
            
            config.setMaximumPoolSize(2);               
            config.setMinimumIdle(1);
            config.setIdleTimeout(30000);
            config.setMaxLifetime(60000);
            config.setConnectionTimeout(30000);        
            config.setInitializationFailTimeout(-1);
            config.setConnectionTestQuery("SELECT 1");

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public static String getPnpLeaderboard() {
        String sql = "SELECT badge_no, name, `rank`, points, discord_id FROM officers ORDER BY points DESC LIMIT 10";
        StringBuilder sb = new StringBuilder("🏆 **PNP Top Officers Leaderboard:**\n\n");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            int rankPosition = 1;
            while (rs.next()) {
                String badgeNo = rs.getString("badge_no");
                String name = rs.getString("name");
                String rank = rs.getString("rank");
                int points = rs.getInt("points");
                String discordId = rs.getString("discord_id");

                String userMention = (discordId != null && !discordId.trim().isEmpty()) ? "<@" + discordId + ">" : "`" + name + "`";

                sb.append("`").append(rankPosition).append(".` ")
                  .append(userMention).append(" (`").append(badgeNo).append("`) - **")
                  .append(rank).append("** | `").append(points).append(" pts`\n");
                
                rankPosition++;
            }

            if (rankPosition == 1) {
                return "ℹ️ No officers found in the database.";
            }
            return sb.toString();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "❌ **Error:** Failed to fetch PNP leaderboard.";
    }

    public static boolean linkDiscordAccount(String badgeNo, String discordId) {
        String altBadge = badgeNo.startsWith("O") ? badgeNo.substring(1) : "O" + badgeNo;
        String updateSql = "UPDATE officers SET discord_id = ? WHERE badge_no = ? OR badge_no = ?";
        
        try (Connection conn = getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, discordId);
                pstmt.setString(2, badgeNo);
                pstmt.setString(3, altBadge);
                int rowsUpdated = pstmt.executeUpdate();
                if (rowsUpdated > 0) return true;
            }

            String insertSql = "INSERT INTO officers (badge_no, name, `rank`, points, discord_id) VALUES (?, 'Officer', 'Police General', 0, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, badgeNo);
                pstmt.setString(2, discordId);
                return pstmt.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getBadgeByDiscordId(String discordId) {
        String sql = "SELECT badge_no FROM officers WHERE discord_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, discordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("badge_no");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getDiscordIdByBadge(String badgeNo) {
        String altBadge = badgeNo.startsWith("O") ? badgeNo.substring(1) : "O" + badgeNo;
        String sql = "SELECT discord_id FROM officers WHERE badge_no = ? OR badge_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, badgeNo);
            pstmt.setString(2, altBadge);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("discord_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getPointsByBadge(String badgeNo) {
        String altBadge = badgeNo.startsWith("O") ? badgeNo.substring(1) : "O" + badgeNo;
        String sql = "SELECT points FROM officers WHERE badge_no = ? OR badge_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, badgeNo);
            pstmt.setString(2, altBadge);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("points");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String getRankByBadge(String badgeNo) {
        String altBadge = badgeNo.startsWith("O") ? badgeNo.substring(1) : "O" + badgeNo;
        String sql = "SELECT `rank` FROM officers WHERE badge_no = ? OR badge_no = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, badgeNo);
            pstmt.setString(2, altBadge);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("rank");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int getRequiredPointsForRank(String rank) {
        switch (rank) {
            case "Police Colonel": return 3400;
            case "Police Lieutenant Colonel": return 2900;
            case "Police Major": return 2400;
            case "Police Captain": return 1900;
            case "Police Lieutenant": return 1400;
            case "Police Executive Master Sergeant": return 1200;
            case "Police Chief Master Sergeant": return 1000;
            case "Police Senior Master Sergeant": return 800;
            case "Police Master Sergeant": return 600;
            case "Police Staff Sergeant": return 400;
            case "Police Corporal": return 200;
            default: return 0;
        }
    }

    public static String addCustomPointsAndCheckPromotion(String inputBadgeNo, int points, String criteriaName, String userMention) {
        String altBadgeNo = inputBadgeNo.startsWith("O") ? inputBadgeNo.substring(1) : "O" + inputBadgeNo;
        String updateSql = "UPDATE officers SET points = points + ? WHERE badge_no = ? OR badge_no = ?";
        String selectSql = "SELECT badge_no, name, `rank`, points FROM officers WHERE badge_no = ? OR badge_no = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, points);
                pstmt.setString(2, inputBadgeNo);
                pstmt.setString(3, altBadgeNo);
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected == 0) {
                    String insertSql = "INSERT INTO officers (badge_no, name, `rank`, points) VALUES (?, 'Officer', 'Police General', ?)";
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, inputBadgeNo);
                        insertPstmt.setInt(2, Math.max(0, points));
                        insertPstmt.executeUpdate();
                    }
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, inputBadgeNo);
                pstmt.setString(2, altBadgeNo);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String currentBadge = rs.getString("badge_no");
                        String name = rs.getString("name");
                        String currentRank = rs.getString("rank");
                        int totalPoints = rs.getInt("points");

                        String newRank = currentRank;
                        String newBadge = currentBadge;

                        if (!currentRank.equalsIgnoreCase("Police General") && !currentRank.equalsIgnoreCase("Police Lieutenant General")) {
                            if (totalPoints >= 3400) {
                                newRank = "Police Colonel";
                                if (!currentBadge.startsWith("O")) newBadge = "O" + currentBadge;
                            } else if (totalPoints >= 2900) {
                                newRank = "Police Lieutenant Colonel";
                                if (!currentBadge.startsWith("O")) newBadge = "O" + currentBadge;
                            } else if (totalPoints >= 2400) {
                                newRank = "Police Major";
                                if (!currentBadge.startsWith("O")) newBadge = "O" + currentBadge;
                            } else if (totalPoints >= 1900) {
                                newRank = "Police Captain";
                                if (!currentBadge.startsWith("O")) newBadge = "O" + currentBadge;
                            } else if (totalPoints >= 1400) {
                                newRank = "Police Lieutenant";
                                if (!currentBadge.startsWith("O")) newBadge = "O" + currentBadge;
                            } else if (totalPoints >= 1200) {
                                newRank = "Police Executive Master Sergeant";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            } else if (totalPoints >= 1000) {
                                newRank = "Police Chief Master Sergeant";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            } else if (totalPoints >= 800) {
                                newRank = "Police Senior Master Sergeant";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            } else if (totalPoints >= 600) {
                                newRank = "Police Master Sergeant";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            } else if (totalPoints >= 400) {
                                newRank = "Police Staff Sergeant";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            } else if (totalPoints >= 200) {
                                newRank = "Police Corporal";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            } else {
                                newRank = "Patrolman";
                                if (currentBadge.startsWith("O")) newBadge = currentBadge.substring(1);
                            }
                        }

                        boolean rankChanged = !newRank.equalsIgnoreCase(currentRank) || !newBadge.equalsIgnoreCase(currentBadge);

                        if (rankChanged) {
                            String updateDetailsSql = "UPDATE officers SET badge_no = ?, `rank` = ? WHERE badge_no = ? OR badge_no = ?";
                            try (PreparedStatement updatePstmt = conn.prepareStatement(updateDetailsSql)) {
                                updatePstmt.setString(1, newBadge);
                                updatePstmt.setString(2, newRank);
                                updatePstmt.setString(3, currentBadge);
                                updatePstmt.setString(4, altBadgeNo);
                                updatePstmt.executeUpdate();
                            }
                        }

                        String actionStr = points >= 0 ? "+" + points : String.valueOf(points);
                        String response = "🌟 " + actionStr + " points applied (" + criteriaName + ") to Badge `" + newBadge + "`. (Total: " + totalPoints + " pts)";

                        if (rankChanged) {
                            int oldReq = getRequiredPointsForRank(currentRank);
                            int newReq = getRequiredPointsForRank(newRank);

                            if (newReq > oldReq) {
                                response += "\n🎖️ **PROMOTION!** Officer **" + name + "** (" + userMention + ") has been promoted to **" + newRank + "**! Badge updated to `" + newBadge + "`.";
                            } else {
                                response += "\n⚠️ **DEMOTION!** Officer **" + name + "** (" + userMention + ") has been demoted to **" + newRank + "** due to point threshold drop. Badge updated to `" + newBadge + "`.";
                            }
                        }

                        return response;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}