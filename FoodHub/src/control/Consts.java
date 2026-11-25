package control;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URISyntaxException;

public final class Consts {
    private Consts() {}

    private static final String DB_NAME = "FoodHub.accdb";
    public static final String DB_FILE_PATH = getDBPath();
    public static final String CONN_STR = "jdbc:ucanaccess://" + DB_FILE_PATH + ";COLUMNORDER=DISPLAY";

    private static String getDBPath() {
        String configured = System.getProperty("foodhub.db.path");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize().toString();
        }
        try {
            Path location = Path.of(Consts.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.resolveSibling(DB_NAME).toString();
            }
            Path source = location.resolveSibling("src").resolve(DB_NAME);
            if (Files.isRegularFile(source)) return source.toString();
            if (Files.isRegularFile(Path.of("src", DB_NAME))) return Path.of("src", DB_NAME).toAbsolutePath().toString();
            if (Files.isRegularFile(Path.of("FoodHub", "src", DB_NAME))) return Path.of("FoodHub", "src", DB_NAME).toAbsolutePath().toString();
            return location.resolve(DB_NAME).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate the database.", e);
        }
    }
}
