import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class DiscordProofListenerBot extends ListenerAdapter {

    // Reads DISCORD_TOKEN exclusively from environment variables or local fallback
    private static final String BOT_TOKEN = System.getenv("DISCORD_TOKEN");

    // Configured Proof Channels
    private static final String PNP_PROOF_CHANNEL = "duty-proofs";
    private static final String GOV_PROOF_CHANNEL = "gov-proof"; 

    // Authorized Discord User IDs allowed to approve proofs and adjust points
    private static final List<String> AUTHORIZED_STAFF_IDS = Arrays.asList(
        "472624267415257089",  // Carlo (PGEN)
        "1486621674735534111", // KYY (MAYOR)
        "784409513608478721",  // Aljade (GOV)
        "1441430308753899640"  // Kzh (HR)
    );

    private static final Set<String> APPROVED_MESSAGE_IDS = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        // Determine server port from Render environment variable or default to 8080
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }

        // 1. Start Web Dashboard API Server
        try {
            DashboardApiServer.startServer(port);
        } catch (Exception e) {
            System.err.println("⚠️ Warning: DashboardApiServer failed to start on port " + port + ": " + e.getMessage());
        }

        // 2. Start Process Heartbeat Task
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Heartbeat: Bot process active - " + System.currentTimeMillis());
        }, 0, 4, TimeUnit.MINUTES);

        // 3. Authenticate Discord Bot
        if (BOT_TOKEN == null || BOT_TOKEN.trim().isEmpty()) {
            System.err.println("⚠️ DISCORD_TOKEN environment variable is not set. Dashboard API is running, but Discord Bot is disabled.");
            return;
        }

        System.out.println("Connecting to Discord Gateway...");

        try {
            JDA jda = JDABuilder.createDefault(BOT_TOKEN.trim())
                    .enableIntents(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS
                    )
                    .addEventListeners(new DiscordProofListenerBot())
                    .build();

            // Block thread until bot successfully authenticates with Discord
            jda.awaitReady();
            System.out.println("✅ DISCORD BOT LOGGED IN SUCCESSFULLY AS: " + jda.getSelfUser().getAsTag());
        } catch (InvalidTokenException e) {
            System.err.println("❌ DISCORD BOT ERROR: Provided Discord token is invalid or expired. Check DISCORD_TOKEN environment variable.");
        } catch (Exception e) {
            System.err.println("❌ DISCORD BOT ERROR: Failed to log into Discord: " + e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private static void runAutomaticDatabaseImport() {
        System.out.println("Attempting automatic database schema import on Render startup...");
        try (Connection conn = PointsDatabaseHelper.getConnection()) {
            if (conn == null) {
                System.err.println("⚠️ Database connection returned null. Skipping automatic import.");
                return;
            }

            InputStream is = DiscordProofListenerBot.class.getClassLoader().getResourceAsStream("roleplay_conso (1).sql");
            if (is == null) {
                System.out.println("⚠️ roleplay_conso.sql not found in resources. Skipping SQL import.");
                return;
            }

            try (Statement stmt = conn.createStatement();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                stmt.execute("SET SESSION sql_require_primary_key = 0;");
                StringBuilder sqlQuery = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.isEmpty()) {
                        continue;
                    }
                    sqlQuery.append(line).append("\n");
                    if (trimmed.endsWith(";")) {
                        try {
                            stmt.execute(sqlQuery.toString());
                        } catch (Exception qEx) {
                            System.out.println("⚠️ Skipping existing/problematic query: " + qEx.getMessage());
                        }
                        sqlQuery.setLength(0);
                    }
                }
                System.out.println("🎉 SQL Schema imported to Aiven successfully!");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Database startup import skipped or failed: " + e.getMessage());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String messageText = event.getMessage().getContentRaw().trim();
        if (messageText.isEmpty()) return;

        // Diagnostic log: Prints every received user message to console
        System.out.println("📩 [INCOMING] #" + event.getChannel().getName() + " | " + event.getAuthor().getName() + ": " + messageText);

        String[] lines = messageText.split("\\r?\\n");
        String[] args = lines[0].trim().split("\\s+");
        String command = args[0].toLowerCase();
        String channelName = event.getChannel().getName().toLowerCase();

        try {
            // ----------------- GENERAL / HELP COMMAND -----------------
            if (command.equals("!help") || command.equals("!commands")) {
                String helpMessage = "📜 **SYSTEM COMMANDS MENU**\n\n" +
                        "🛡️ **PNP (Police) Commands:**\n" +
                        "• `!link <badge_no>` — Link your Discord account to your PNP Badge.\n" +
                        "• `!mypoints` / `!points` — View your badge, rank, and current total points.\n" +
                        "• `!checkpoints <badge_no>` — Check the points and rank of another officer.\n" +
                        "• `!topofficers` / `!leaderboard` — View the Top 10 PNP Officers Leaderboard.\n\n" +
                        "🏛️ **Government Office Commands:**\n" +
                        "• `!linkgov <id_no>` — Link your Discord account to your Gov Member ID.\n" +
                        "• `!govpoints` — View your government ID, position, and total points.\n" +
                        "• `!topgov` / `!govleaderboard` — View the Top 10 Government Members Leaderboard.\n\n" +
                        "🛠️ **Staff / Admin Commands:**\n" +
                        "• `!addpoints <badge_no> <amount> [reason]` — Award points to a PNP officer.\n" +
                        "• `!removepoints <badge_no> <amount> [reason]` — Deduct points from a PNP officer.\n" +
                        "• `!addgovpoints <id_no> <amount> [reason]` — Award points to a Government member.\n" +
                        "• `!removegovpoints <id_no> <amount> [reason]` — Deduct points from a Government member.\n" +
                        "• **Reaction Approval:** React with `✅` on uploaded proof in `#duty-proofs` or `#gov-proofs` to credit points.";

                event.getChannel().sendMessage(helpMessage).queue();
                return;
            }

            // ----------------- DYNAMIC LEADERBOARD ROUTING -----------------
            if (command.equals("!leaderboard") || command.equals("!top")) {
                String leaderboardMsg;
                if (channelName.contains("gov")) {
                    leaderboardMsg = GovDatabaseHelper.getGovLeaderboard();
                } else {
                    leaderboardMsg = PointsDatabaseHelper.getPnpLeaderboard();
                }
                
                if (leaderboardMsg == null || leaderboardMsg.trim().isEmpty()) {
                    leaderboardMsg = "⚠️ Leaderboard is currently empty or unavailable.";
                }
                event.getChannel().sendMessage(leaderboardMsg).queue();
                return;
            }

            // ----------------- PNP COMMANDS -----------------
            if (command.equals("!link")) {
                if (channelName.contains("gov")) {
                    event.getChannel().sendMessage("⚠️ Please run `!link` inside PNP channels, not Government channels.").queue();
                    return;
                }
                if (args.length < 2) {
                    event.getChannel().sendMessage("❌ **Usage:** `!link <badge_no>` (e.g., `!link O09-01002`)").queue();
                    return;
                }
                String badgeNo = args[1].toUpperCase();
                if (PointsDatabaseHelper.linkDiscordAccount(badgeNo, event.getAuthor().getId())) {
                    event.getChannel().sendMessage("✅ **PNP Account Linked!** " + event.getAuthor().getAsMention() + " is bound to Badge `" + badgeNo + "`.").queue();
                } else {
                    event.getChannel().sendMessage("❌ **Failed to link account.** Ensure Badge `" + badgeNo + "` exists in the database.").queue();
                }
                return;
            }

            if (command.equals("!mypoints") || command.equals("!points")) {
                String badgeNo = PointsDatabaseHelper.getBadgeByDiscordId(event.getAuthor().getId());
                if (badgeNo == null) {
                    event.getChannel().sendMessage("⚠️ " + event.getAuthor().getAsMention() + " **Account not linked.** Run `!link <badge_no>` first.").queue();
                    return;
                }
                int points = PointsDatabaseHelper.getPointsByBadge(badgeNo);
                String rank = PointsDatabaseHelper.getRankByBadge(badgeNo);
                event.getChannel().sendMessage("📊 **Officer Profile:**\n• **User:** " + event.getAuthor().getAsMention() + "\n• **Badge:** `" + badgeNo + "`\n• **Rank:** " + rank + "\n• **Total Points:** `" + points + " pts`").queue();
                return;
            }

            if (command.equals("!checkpoints")) {
                if (args.length < 2) {
                    event.getChannel().sendMessage("❌ **Usage:** `!checkpoints <badge_no>`").queue();
                    return;
                }
                String badgeNo = args[1].toUpperCase();
                int points = PointsDatabaseHelper.getPointsByBadge(badgeNo);
                String rank = PointsDatabaseHelper.getRankByBadge(badgeNo);

                if (rank != null) {
                    event.getChannel().sendMessage("📊 **Officer Status:**\n• **Badge:** `" + badgeNo + "`\n• **Rank:** " + rank + "\n• **Total Points:** `" + points + " pts`").queue();
                } else {
                    event.getChannel().sendMessage("❌ **Badge not found.** Ensure Badge `" + badgeNo + "` exists in the database.").queue();
                }
                return;
            }

            if (command.equals("!topofficers")) {
                String leaderboardMsg = PointsDatabaseHelper.getPnpLeaderboard();
                event.getChannel().sendMessage(leaderboardMsg).queue();
                return;
            }

            // ----------------- GOV COMMANDS -----------------
            if (command.equals("!linkgov")) {
                if (channelName.equalsIgnoreCase(PNP_PROOF_CHANNEL) || channelName.contains("pnp")) {
                    event.getChannel().sendMessage("⚠️ Please run `!linkgov` inside Government channels (e.g., `#gov-proofs`).").queue();
                    return;
                }
                if (args.length < 2) {
                    event.getChannel().sendMessage("❌ **Usage:** `!linkgov <id_no>` (e.g., `!linkgov GOV-001`)").queue();
                    return;
                }
                String idNo = args[1].toUpperCase();
                String discordId = event.getAuthor().getId();

                if (GovDatabaseHelper.linkGovAccount(idNo, discordId)) {
                    event.getChannel().sendMessage("✅ **Gov Account Linked!** " + event.getAuthor().getAsMention() + " is bound to ID `" + idNo + "`.").queue();
                } else {
                    event.getChannel().sendMessage("❌ **Failed to link account.** Ensure ID `" + idNo + "` exists in `government_members`.").queue();
                }
                return;
            }

            if (command.equals("!govpoints")) {
                String discordId = event.getAuthor().getId();
                String idNo = GovDatabaseHelper.getIdByDiscordId(discordId);

                if (idNo == null) {
                    event.getChannel().sendMessage("⚠️ " + event.getAuthor().getAsMention() + " **Account not linked.** Run `!linkgov <id_no>` first.").queue();
                    return;
                }

                int points = GovDatabaseHelper.getPointsById(idNo);
                String position = GovDatabaseHelper.getPositionById(idNo);

                event.getChannel().sendMessage("📊 **Gov Profile:**\n• **User:** " + event.getAuthor().getAsMention() + "\n• **ID:** `" + idNo + "`\n• **Position:** " + position + "\n• **Total Points:** `" + points + " pts`").queue();
                return;
            }

            if (command.equals("!topgov") || command.equals("!govleaderboard")) {
                String leaderboardMsg = GovDatabaseHelper.getGovLeaderboard();
                event.getChannel().sendMessage(leaderboardMsg).queue();
                return;
            }

            // ----------------- PNP ADMIN COMMANDS -----------------
            if (command.equals("!addpoints") || command.equals("!removepoints")) {
                if (!AUTHORIZED_STAFF_IDS.contains(event.getAuthor().getId())) {
                    event.getChannel().sendMessage("⛔ **Access Denied.** You are not authorized to adjust points.").queue();
                    return;
                }

                if (args.length < 3) {
                    event.getChannel().sendMessage("❌ **Usage:** `!" + command.substring(1) + " <badge_no> <amount> [reason]`").queue();
                    return;
                }

                try {
                    String badgeNo = args[1].toUpperCase();
                    int pointsAmount = Integer.parseInt(args[2]);

                    if (command.equals("!removepoints")) {
                        pointsAmount = -Math.abs(pointsAmount);
                    }

                    String reason = "Manual Admin Adjustment";
                    if (args.length > 3) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 3; i < args.length; i++) {
                            sb.append(args[i]).append(" ");
                        }
                        reason = sb.toString().trim();
                    }

                    String targetDiscordId = PointsDatabaseHelper.getDiscordIdByBadge(badgeNo);
                    String targetMention = (targetDiscordId != null) ? "<@" + targetDiscordId + ">" : "Unlinked Officer";

                    String resultMessage = PointsDatabaseHelper.addCustomPointsAndCheckPromotion(
                        badgeNo, pointsAmount, reason, targetMention
                    );

                    if (resultMessage != null) {
                        DashboardApiServer.logActivity("Admin Adjustment (" + pointsAmount + " pts)", badgeNo, "PNP");
                        event.getChannel().sendMessage("🛠️ **Admin Adjustment:**\n" + resultMessage).queue();
                    } else {
                        event.getChannel().sendMessage("❌ **Failed to update points.** Ensure Badge `" + badgeNo + "` exists in `officers`.").queue();
                    }

                } catch (NumberFormatException e) {
                    event.getChannel().sendMessage("❌ **Invalid amount.** Please provide a numeric value for points.").queue();
                }
                return;
            }

            // ----------------- GOV ADMIN COMMANDS -----------------
            if (command.equals("!addgovpoints") || command.equals("!removegovpoints")) {
                if (!AUTHORIZED_STAFF_IDS.contains(event.getAuthor().getId())) {
                    event.getChannel().sendMessage("⛔ **Access Denied.** You are not authorized to adjust government points.").queue();
                    return;
                }

                if (args.length < 3) {
                    event.getChannel().sendMessage("❌ **Usage:** `!" + command.substring(1) + " <id_no> <amount> [reason]`").queue();
                    return;
                }

                try {
                    String idNo = args[1].toUpperCase();
                    int pointsAmount = Integer.parseInt(args[2]);

                    if (command.equals("!removegovpoints")) {
                        pointsAmount = -Math.abs(pointsAmount);
                    }

                    String reason = "Manual Admin Adjustment";
                    if (args.length > 3) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 3; i < args.length; i++) {
                            sb.append(args[i]).append(" ");
                        }
                        reason = sb.toString().trim();
                    }

                    String targetDiscordId = null;
                    String sql = "SELECT discord_id FROM government_members WHERE id_no = ?";
                    try (java.sql.Connection conn = PointsDatabaseHelper.getConnection();
                         java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, idNo);
                        try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                targetDiscordId = rs.getString("discord_id");
                            }
                        }
                    } catch (java.sql.SQLException e) {
                        e.printStackTrace();
                    }

                    String targetMention = (targetDiscordId != null) ? "<@" + targetDiscordId + ">" : "`" + idNo + "`";

                    String resultMessage = GovDatabaseHelper.addCustomGovPoints(
                        idNo, pointsAmount, reason, targetMention
                    );

                    if (resultMessage != null) {
                        DashboardApiServer.logActivity("Gov Admin Adjustment (" + pointsAmount + " pts)", idNo, "GOV");
                        event.getChannel().sendMessage(resultMessage).queue();
                    } else {
                        event.getChannel().sendMessage("❌ **Failed to update points.** Ensure ID `" + idNo + "` exists in `government_members`.").queue();
                    }

                } catch (NumberFormatException e) {
                    event.getChannel().sendMessage("❌ **Invalid amount.** Please provide a numeric value for points.").queue();
                }
                return;
            }

            // ----------------- PROOF CHANNEL REACTION LISTENER -----------------
            if (channelName.equalsIgnoreCase(PNP_PROOF_CHANNEL) || channelName.equalsIgnoreCase(GOV_PROOF_CHANNEL)) {
                Message message = event.getMessage();
                boolean hasAttachment = !message.getAttachments().isEmpty();
                boolean hasEmbedOrLink = message.getContentRaw().contains("http://") || message.getContentRaw().contains("https://");

                if (hasAttachment || hasEmbedOrLink) {
                    message.addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⏳")).queue();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Exception occurred during command execution (" + command + "):");
            e.printStackTrace();
            event.getChannel().sendMessage("❌ **Internal Error:** An issue occurred while processing that command. Check server logs.").queue();
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() != null && event.getUser().isBot()) return;

        String reactorDiscordId = event.getUserId();
        if (!AUTHORIZED_STAFF_IDS.contains(reactorDiscordId)) return;

        String channelName = event.getChannel().getName().toLowerCase();

        try {
            if (channelName.equalsIgnoreCase(PNP_PROOF_CHANNEL) && event.getEmoji().getName().equals("✅")) {
                processPnpProof(event, reactorDiscordId);
            } else if (channelName.equalsIgnoreCase(GOV_PROOF_CHANNEL) && event.getEmoji().getName().equals("✅")) {
                processGovProof(event, reactorDiscordId);
            }
        } catch (Exception e) {
            System.err.println("❌ Exception occurred during reaction handling:");
            e.printStackTrace();
        }
    }

    private void processPnpProof(MessageReactionAddEvent event, String reactorDiscordId) {
        String messageId = event.getMessageId();
        if (!APPROVED_MESSAGE_IDS.add(messageId)) return;

        event.getChannel().retrieveMessageById(messageId).queue(message -> {
            try {
                String officerDiscordId = message.getAuthor().getId();
                String badgeNo = PointsDatabaseHelper.getBadgeByDiscordId(officerDiscordId);

                if (badgeNo != null) {
                    String caption = message.getContentRaw().toLowerCase().trim();
                    int pointsToAward = 5;
                    String criteriaType = "Duty";

                    if (caption.contains("training") || caption.contains("seminar")) {
                        pointsToAward = 20; criteriaType = "Training";
                    } else if (caption.contains("recruit") || caption.contains("recruitment")) {
                        pointsToAward = 15; criteriaType = "Recruit";
                    } else if (caption.contains("event") || caption.contains("meeting") || caption.contains("inspection") || caption.contains("drill")) {
                        pointsToAward = 10; criteriaType = "Event";
                    } else if (caption.contains("duty") || caption.contains("patrol")) {
                        pointsToAward = 5; criteriaType = "Duty";
                    }

                    String result = PointsDatabaseHelper.addCustomPointsAndCheckPromotion(badgeNo, pointsToAward, criteriaType, message.getAuthor().getAsMention());
                    DashboardApiServer.logActivity("Approved " + criteriaType + " Proof (+" + pointsToAward + " pts)", badgeNo, "PNP");

                    message.removeReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⏳")).queue(s -> {}, f -> {});
                    event.getChannel().sendMessage("🛡️ **Proof Approved by <@" + reactorDiscordId + ">!**\n" + result).setMessageReference(message).queue();
                } else {
                    APPROVED_MESSAGE_IDS.remove(messageId);
                    event.getChannel().sendMessage("❌ **Cannot award points:** " + message.getAuthor().getAsMention() + " is not linked to a Badge ID.").setMessageReference(message).queue();
                }
            } catch (Exception e) {
                APPROVED_MESSAGE_IDS.remove(messageId);
                System.err.println("❌ Exception inside processPnpProof:");
                e.printStackTrace();
            }
        }, throwable -> APPROVED_MESSAGE_IDS.remove(messageId));
    }

    private void processGovProof(MessageReactionAddEvent event, String reactorDiscordId) {
        String messageId = event.getMessageId();
        if (!APPROVED_MESSAGE_IDS.add(messageId)) return;

        event.getChannel().retrieveMessageById(messageId).queue(message -> {
            try {
                String staffDiscordId = message.getAuthor().getId();
                String idNo = GovDatabaseHelper.getIdByDiscordId(staffDiscordId);

                if (idNo != null) {
                    String caption = message.getContentRaw().toLowerCase().trim();
                    int pointsToAward = 5;
                    String criteriaType = "Office Shift";

                    if (caption.contains("event") || caption.contains("outreach") || caption.contains("project") || caption.contains("seminar")) {
                        pointsToAward = 20; criteriaType = "Special Project";
                    } else if (caption.contains("meeting") || caption.contains("hearing") || caption.contains("session") || caption.contains("inspection")) {
                        pointsToAward = 15; criteriaType = "Official Meeting";
                    } else if (caption.contains("document") || caption.contains("clearance") || caption.contains("permit") || caption.contains("filing")) {
                        pointsToAward = 10; criteriaType = "Admin Task";
                    } else if (caption.contains("duty") || caption.contains("shift") || caption.contains("office")) {
                        pointsToAward = 5; criteriaType = "Office Shift";
                    }

                    String result = GovDatabaseHelper.addGovPoints(idNo, pointsToAward, criteriaType, message.getAuthor().getAsMention());
                    DashboardApiServer.logActivity("Approved Gov " + criteriaType + " (+" + pointsToAward + " pts)", idNo, "GOV");

                    message.removeReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⏳")).queue(s -> {}, f -> {});
                    event.getChannel().sendMessage("🏛️ **Gov Proof Approved by <@" + reactorDiscordId + ">!**\n" + result).setMessageReference(message).queue();
                } else {
                    APPROVED_MESSAGE_IDS.remove(messageId);
                    event.getChannel().sendMessage("❌ **Cannot award points:** " + message.getAuthor().getAsMention() + " is not linked to an ID Number. Run `!linkgov <id_no>`.").setMessageReference(message).queue();
                }
            } catch (Exception e) {
                APPROVED_MESSAGE_IDS.remove(messageId);
                System.err.println("❌ Exception inside processGovProof:");
                e.printStackTrace();
            }
        }, throwable -> APPROVED_MESSAGE_IDS.remove(messageId));
    }
}