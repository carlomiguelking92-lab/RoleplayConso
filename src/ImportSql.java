import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ImportSql {

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

    private static final String DB_PASS = System.getenv("DB_PASS") != null 
            ? System.getenv("DB_PASS") 
            : "AVNS_KG-eoi0GF2BkwXvY6wM"; // Ensure this matches Aiven exact password

    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + 
            "?useSSL=true&requireSSL=true&verifyServerCertificate=false" +
            "&sslMode=REQUIRED&allowPublicKeyRetrieval=true&autoReconnect=true";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            System.out.println("✅ Authentication successful!");
            runImport(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void runImport(Connection conn) {
        try (InputStream is = ImportSql.class.getClassLoader().getResourceAsStream("roleplay_conso (1).sql")) {
            if (is == null) {
                System.err.println("❌ Could not find roleplay_conso.sql in project resources.");
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
                        } catch (Exception queryEx) {
                            System.out.println("⚠️ Skipping query: " + queryEx.getMessage());
                        }
                        sqlQuery.setLength(0);
                    }
                }
            }
            System.out.println("🎉 SQL file successfully imported to Aiven!");

        } catch (Exception e) {
            System.err.println("❌ Import failed:");
            e.printStackTrace();
        }
    }
}