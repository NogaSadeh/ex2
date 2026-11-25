package control;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class Database {
    private Database() {}

    static Connection open() throws SQLException {
        if (!Files.isRegularFile(Path.of(Consts.DB_FILE_PATH))) {
            throw new SQLException("Database file not found: " + Consts.DB_FILE_PATH);
        }
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Add UCanAccess and its dependencies to the Java classpath.", e);
        }
        return DriverManager.getConnection(Consts.CONN_STR);
    }

    static <T> T transaction(Transaction<T> action) {
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                T result = action.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database change failed. Reload the data before trying again. " + e.getMessage(), e);
        }
    }

    interface Transaction<T> { T run(Connection conn) throws SQLException; }
}
