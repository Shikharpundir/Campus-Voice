import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.Console;

/**
 * Creates JDBC connections to MySQL.
 *
 * Credentials are NEVER stored in source code. Set these environment variables:
 *   CV_DB_URL       (optional) default: jdbc:mysql://localhost:3306/campus_voice
 *   CV_DB_USER      (optional) default: root
 *   CV_DB_PASSWORD  (required) your MySQL password
 *
 * The MySQL Connector/J jar must be on the classpath (see README.md).
 */
public final class DatabaseConnection {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/campus_voice?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static String promptedPassword;

    private DatabaseConnection() { }

    public static Connection getConnection() throws SQLException {
        String url  = envOrDefault("CV_DB_URL", DEFAULT_URL);
        String user = envOrDefault("CV_DB_USER", "root");
        String pass = password();
        return DriverManager.getConnection(url, user, pass);
    }

    private static synchronized String password() throws SQLException {
        String configured = System.getenv("CV_DB_PASSWORD");
        if (configured != null && !configured.isEmpty()) return configured;
        if (promptedPassword != null) return promptedPassword;

        Console console = System.console();
        if (console == null) {
            throw new SQLException("CV_DB_PASSWORD is not set and no secure console is available. "
                    + "Set CV_DB_PASSWORD in this terminal before starting the application.");
        }
        char[] entered = console.readPassword("MySQL password for %s: ", envOrDefault("CV_DB_USER", "root"));
        if (entered == null || entered.length == 0) {
            throw new SQLException("No MySQL password was entered.");
        }
        promptedPassword = new String(entered);
        java.util.Arrays.fill(entered, '\0');
        return promptedPassword;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }
}
