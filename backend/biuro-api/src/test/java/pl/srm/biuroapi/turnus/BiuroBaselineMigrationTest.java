package pl.srm.biuroapi.turnus;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_BIURO_DB_URL", matches = ".+/biuro_turnus_test")
class BiuroBaselineMigrationTest {
    @Test
    void cleanDatabaseUsesBaselineWithoutLegacyTurnusesAndMatchesIncrementalSchema() throws Exception {
        withDatabases((baselineUrl, incrementalUrl) -> {
            var baselineResult = flyway(baselineUrl).migrate();
            var incrementalResult = versionedFlyway(incrementalUrl).migrate();

            assertEquals(1, baselineResult.migrationsExecuted, "A clean database should use only B7");
            assertEquals(7, incrementalResult.migrationsExecuted, "The reference path should apply V1-V7");
            assertEquals(schemaSnapshot(incrementalUrl), schemaSnapshot(baselineUrl));
            assertEquals(0, scalar(baselineUrl, "SELECT COUNT(*) FROM turnus"),
                    "The clean baseline must not import legacy sample Turnusy from V2");
            assertEquals(1, scalar(baselineUrl, "SELECT COUNT(*) FROM biuro_account_setup WHERE id = 1 AND completed = FALSE"));
            assertEquals(1, scalar(baselineUrl, "SELECT COUNT(*) FROM biuro_account_admin_lock WHERE id = 1"));
            assertEquals(2, scalar(incrementalUrl, "SELECT COUNT(*) FROM turnus"),
                    "The reference migration path still retains its legacy seed behavior");
            assertEquals(0, flyway(baselineUrl).migrate().migrationsExecuted,
                    "Restarting Flyway must not reapply the baseline");
        });
    }

    private Flyway flyway(String url) {
        return configuration(url).load();
    }

    private Flyway versionedFlyway(String url) {
        var configuration = configuration(url);
        configuration.getConfigurationExtension(BaselineMigrationConfigurationExtension.class)
                .setBaselineMigrationPrefix("X");
        return configuration.load();
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configuration(String url) {
        return Flyway.configure().dataSource(url, System.getenv("TEST_BIURO_DB_USER"),
                System.getenv("TEST_BIURO_DB_PASSWORD"));
    }

    private long scalar(String url, String query) throws Exception {
        try (var connection = connect(url); var statement = connection.createStatement();
             var result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private List<String> schemaSnapshot(String url) throws Exception {
        List<String> snapshot = new ArrayList<>();
        try (var connection = connect(url); var statement = connection.createStatement()) {
            addRows(snapshot, statement, """
                    SELECT table_name, COALESCE(engine, '<NULL>')
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                    ORDER BY table_name
                    """);
            addRows(snapshot, statement, """
                    SELECT table_name, column_name, ordinal_position, column_type, is_nullable,
                           COALESCE(column_default, '<NULL>'), extra
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                    ORDER BY table_name, ordinal_position
                    """);
            addRows(snapshot, statement, """
                    SELECT table_name, index_name, non_unique, seq_in_index, column_name,
                           COALESCE(sub_part, 0), index_type
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                    ORDER BY table_name, index_name, seq_in_index
                    """);
            addRows(snapshot, statement, """
                    SELECT tc.table_name, tc.constraint_name, tc.constraint_type,
                           COALESCE(kcu.column_name, ''), COALESCE(kcu.referenced_table_name, ''),
                           COALESCE(kcu.referenced_column_name, ''), COALESCE(kcu.ordinal_position, 0)
                    FROM information_schema.table_constraints tc
                    LEFT JOIN information_schema.key_column_usage kcu
                      ON kcu.constraint_schema = tc.constraint_schema
                     AND kcu.table_name = tc.table_name AND kcu.constraint_name = tc.constraint_name
                    WHERE tc.constraint_schema = DATABASE()
                    ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position
                    """);
            addRows(snapshot, statement, """
                    SELECT table_name, constraint_name, check_clause
                    FROM information_schema.check_constraints
                    WHERE constraint_schema = DATABASE()
                    ORDER BY table_name, constraint_name
                    """);
        }
        return snapshot;
    }

    private void addRows(List<String> target, Statement statement, String query) throws Exception {
        try (ResultSet result = statement.executeQuery(query)) {
            while (result.next()) {
                List<String> values = new ArrayList<>();
                for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                    String value = result.getString(column);
                    values.add(value == null ? "<NULL>" : value);
                }
                target.add(String.join("\u001f", values));
            }
        }
    }

    private void withDatabases(DatabaseAction action) throws Exception {
        String original = System.getenv("TEST_BIURO_DB_URL");
        String base = original.substring(0, original.lastIndexOf('/') + 1);
        String baselineName = "biuro_baseline_clean_test";
        String incrementalName = "biuro_versioned_clean_test";
        try (var connection = connect(original); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + baselineName);
            try {
                sql.executeUpdate("CREATE DATABASE " + incrementalName);
                try {
                    action.run(base + baselineName, base + incrementalName);
                } finally {
                    sql.executeUpdate("DROP DATABASE " + incrementalName);
                }
            } finally {
                sql.executeUpdate("DROP DATABASE " + baselineName);
            }
        }
    }

    private Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, System.getenv("TEST_BIURO_DB_USER"),
                System.getenv("TEST_BIURO_DB_PASSWORD"));
    }

    @FunctionalInterface
    interface DatabaseAction {
        void run(String baselineUrl, String incrementalUrl) throws Exception;
    }
}
