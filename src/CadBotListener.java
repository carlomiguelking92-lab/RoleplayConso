import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class CadBotListener extends ListenerAdapter {

    private static final String PNP_ROLE_MENTION = "<@&1545331696289718272>";

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        if ("duty".equalsIgnoreCase(commandName)) {
            // Acknowledge Discord immediately (ephemeral/private)
            event.deferReply(true).queue();

            OptionMapping unitOpt = event.getOption("unit");
            OptionMapping statusOpt = event.getOption("status");
            OptionMapping callOpt = event.getOption("call_id");

            if (unitOpt == null || statusOpt == null) {
                event.getHook().sendMessage("❌ Missing required fields.").queue();
                return;
            }

            String unit = unitOpt.getAsString();
            String status = statusOpt.getAsString().toUpperCase();
            String callId = callOpt != null ? callOpt.getAsString() : null;

            boolean success = CadDatabaseHelper.updateUnitStatus(unit, status, callId);

            if (success) {
                event.getHook().sendMessage("✅ **CAD Status Updated:** Unit `" + unit + "` is now **" + status + "**" +
                            (callId != null ? " (Assigned Call: `" + callId + "`)" : ""))
                     .queue();
            } else {
                event.getHook().sendMessage("❌ Database update failed for unit `" + unit + "`.")
                     .queue();
            }

        } else if ("callout".equalsIgnoreCase(commandName)) {
            // Acknowledge Discord immediately (public message)
            event.deferReply().queue();

            OptionMapping titleOpt = event.getOption("title");
            OptionMapping locOpt = event.getOption("location");
            OptionMapping prioOpt = event.getOption("priority");
            OptionMapping detailsOpt = event.getOption("details");

            if (titleOpt == null || locOpt == null || prioOpt == null) {
                event.getHook().sendMessage("❌ Missing required callout parameters.").queue();
                return;
            }

            String title = titleOpt.getAsString();
            String location = locOpt.getAsString();
            String priority = prioOpt.getAsString().toUpperCase();
            String details = detailsOpt != null ? detailsOpt.getAsString() : "No additional details.";

            int generatedCallId = CadDatabaseHelper.createCallout(title, location, priority, details);

            if (generatedCallId != -1) {
                event.getHook().sendMessage(PNP_ROLE_MENTION + " 🚨 **EMERGENCY CALLOUT BROADCASTED** 🚨\n" +
                            "**Call ID:** `#" + generatedCallId + "` | **Priority:** `" + priority + "`\n" +
                            "**Title:** " + title + "\n" +
                            "**Location:** " + location + "\n" +
                            "**Details:** " + details)
                     .queue();
            } else {
                event.getHook().sendMessage("❌ Failed to broadcast callout to CAD database.")
                     .queue();
            }

        } else if ("codes".equalsIgnoreCase(commandName)) {
            // Acknowledge Discord immediately (ephemeral/private)
            event.deferReply(true).queue();

            String codesMessage = 
                "📋 **POLICE RADIO 10-CODES REFERENCE SHEET** 📋\n\n" +
                "**10-0 to 10-19:**\n" +
                "`10-0` Caution | `10-1` Weak Signal | `10-2` Good Signal | `10-3` Stop Transmitting\n" +
                "`10-4` Affirmative | `10-5` Relay | `10-6` Busy | `10-7` Out of Service | `10-8` In Service\n" +
                "`10-9` Repeat | `10-10` Negative | `10-11` Badge # | `10-12` Stand By | `10-13` Weather\n" +
                "`10-14` Info | `10-15` Delivered | `10-16` Reply | `10-17` Enroute | `10-18` Urgent | `10-19` In Contact\n\n" +
                "**10-20 to 10-39:**\n" +
                "`10-20` Location | `10-21` Call | `10-22` Disregard | `10-23` Arrived | `10-24` Complete\n" +
                "`10-25` Report To | `10-26` ETA | `10-27` DL Check | `10-28` Vehicle Check | `10-29` Records Check\n" +
                "`10-30` Danger | `10-31` Pick-up | `10-32` Units Needed | `10-33` Backup Needed | `10-34` Time\n\n" +
                "**10-40 to 10-59:**\n" +
                "`10-40` Fight | `10-41` On Duty | `10-42` Off Duty | `10-43` Pursuit | `10-44` Riot\n" +
                "`10-45` Bomb Threat | `10-46` Bank Alarm | `10-47` Expedite | `10-48` Suspect Detained\n" +
                "`10-49` Drag Racing | `10-50` Accident | `10-51` Wrecker | `10-52` Ambulance | `10-53` Road Blocked\n" +
                "`10-54` Hit & Run | `10-55` DUI Driver | `10-56` DUI Pedestrian | `10-58` Traffic | `10-59` Escort\n\n" +
                "**10-60 to 10-85:**\n" +
                "`10-60` Suspicious Vehicle | `10-61` Vehicle Stop | `10-62` B&E | `10-64` Crime in Progress\n" +
                "`10-65` Armed Robbery | `10-66` Medical Examiner | `10-67` Death Report | `10-70` Parked Violation\n" +
                "`10-72` In Custody | `10-73` Mental Subject | `10-74` Jail Break | `10-75` Stolen | `10-76` Prowler\n" +
                "`10-80` Fire Alarm | `10-82` Fire in Progress | `10-85` Code 2 (Silent)";

            event.getHook().sendMessage(codesMessage).queue();
        }
    }
}