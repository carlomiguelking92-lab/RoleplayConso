import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookNotifier {

    // Certificate Webhook URL
    private static final String CERT_WEBHOOK_URL = "https://discord.com/api/webhooks/1543237954506465341/0anK5YKySOrgMKz2J9kDMX5O8B4HbwutjnuUxVpEGmmnXhPhpNuqCZnWycsNeIwvMaQI";

    // Unpaid Salary & Balance Webhook URL
    private static final String UNPAID_WEBHOOK_URL = "https://discord.com/api/webhooks/1544605127950864466/T-FsN7Aa3fySEuMcXuYU31PU8rm3tefe71LdwcSRTJHQxNaWmprM-UaOKt2CtrMoCS-n";

    /**
     * Existing method for Certificates
     */
    public static boolean sendEmbedNotification(String title, String description, int colorHex) {
        String jsonPayload = String.format(
            "{\"embeds\": [{\"title\": \"%s\", \"description\": \"%s\", \"color\": %d}]}",
            escapeJson(title), escapeJson(description), colorHex
        );
        return postToDiscord(CERT_WEBHOOK_URL, jsonPayload);
    }

    /**
     * New method for Unpaid Salary & Balance Webhooks
     */
    public static boolean sendUnpaidSalaryNotification(String rawJsonPayload) {
        return postToDiscord(UNPAID_WEBHOOK_URL, rawJsonPayload);
    }

    /**
     * Core HTTP POST Dispatcher
     */
    private static boolean postToDiscord(String targetUrl, String jsonPayload) {
        if (targetUrl == null || !targetUrl.startsWith("http")) {
            System.err.println("⚠️ Invalid or missing Discord Webhook URL.");
            return false;
        }

        try {
            URL url = URI.create(targetUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Java-Discord-Webhook");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("✅ Webhook notification sent successfully!");
                return true;
            } else {
                System.err.println("❌ Webhook delivery failed. HTTP Response Code: " + responseCode);
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Error sending webhook notification:");
            e.printStackTrace();
            return false;
        }
    }

    public static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "");
    }
}