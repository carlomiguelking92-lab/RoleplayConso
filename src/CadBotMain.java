import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class CadBotMain {

    public static void main(String[] args) {
        try {
            String botToken = System.getenv("CAD_BOT_TOKEN");
            if (botToken == null || botToken.trim().isEmpty()) {
                botToken = "MTU0NTMyNzY0NzY3MDkzMTQ5Ng.GawSgc.lYOW9Y7mhy7fxxFcQtWB19QridkuEWrj2ZdrDs";
            }

            JDA jda = JDABuilder.createLight(botToken)
                    .addEventListeners(new CadBotListener())
                    .build();

            jda.awaitReady();

            // Automatically target the first Discord server the bot is connected to
            if (!jda.getGuilds().isEmpty()) {
                Guild guild = jda.getGuilds().get(0);

                guild.updateCommands().addCommands(
                    Commands.slash("duty", "Update unit CAD status")
                        .addOption(OptionType.STRING, "unit", "Your Badge/Unit Identifier (e.g. 09-O1012)", true)
                        .addOption(OptionType.STRING, "status", "Status Code (10-8 On Duty, 10-7 Off Duty, 10-6 Busy, 10-97 En Route)", true)
                        .addOption(OptionType.STRING, "call_id", "Assigned Callout ID (Optional)", false),

                    Commands.slash("callout", "Create a new emergency callout (Dispatch)")
                        .addOption(OptionType.STRING, "title", "Incident Title", true)
                        .addOption(OptionType.STRING, "location", "Incident Location", true)
                        .addOption(OptionType.STRING, "priority", "Priority (HIGH, MEDIUM, LOW)", true)
                        .addOption(OptionType.STRING, "details", "Incident details or description", false),

                    Commands.slash("codes", "View Police 10-Codes reference guide")
                ).queue();

                System.out.println("🚨 CAD Dispatch Bot online! Synced commands to: " + guild.getName());
            } else {
                System.err.println("❌ Bot is not inside any Discord server.");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to start CAD Bot:");
            e.printStackTrace();
        }
    }
}