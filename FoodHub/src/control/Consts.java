package control;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class Consts {
    private Consts() {}

    private static final String DB_NAME = "FoodHub.accdb";
    public static final String DB_FILE_PATH = getDBPath();
    public static final String CONN_STR = "jdbc:ucanaccess://" + DB_FILE_PATH + ";COLUMNORDER=DISPLAY";

    private static String getDBPath() {
        try {
            String path = Consts.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            if (decoded.contains(".jar") || decoded.contains(".exe")) {
                decoded = decoded.substring(0, decoded.lastIndexOf('/'));
                return decoded + "/" + DB_NAME;
            } else {
                // הרצה מתוך Eclipse: עלייה מתיקיית bin לתיקיית השורש של הפרויקט
                File projectRoot = new File(decoded).getParentFile();
                return new File(projectRoot, DB_NAME).getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return DB_NAME;
        }
    }
}