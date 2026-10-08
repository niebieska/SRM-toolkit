package pl.srm.registrationapi.registration.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+/integrity_test")
class RegistrationMigrationTest {
    @Test
    void upgradeSeedsHighestSuffixPerGroupAndSurvivesReconnect() throws Exception {
        withDatabase(url -> {
            flyway(url, "1").migrate();
            try (var connection = connect(url); var sql = connection.createStatement()) {
                sql.executeUpdate(insert("REG-P-A-4", "PARTICIPANT", "one"));
                sql.executeUpdate(insert("REG-P-A-9", "PARTICIPANT", "two"));
                sql.executeUpdate(insert("REG-S-A-7", "STAFF", "three"));
            }
            flyway(url, null).migrate();
            try (var connection = connect(url); var sql = connection.createStatement()) {
                var result = sql.executeQuery("SELECT registration_type, last_number FROM registration_counter ORDER BY registration_type");
                assertTrue(result.next()); assertEquals("PARTICIPANT", result.getString(1)); assertEquals(9, result.getLong(2));
                assertTrue(result.next()); assertEquals("STAFF", result.getString(1)); assertEquals(7, result.getLong(2));
                assertFalse(result.next());
            }
            // A new Flyway/client instance must not reset seeded counters on restart.
            assertEquals(0, flyway(url, null).migrate().migrationsExecuted);
        });
    }

    @Test
    void upgradeRefusesExistingDuplicatesWithoutDeletingThem() throws Exception {
        withDatabase(url -> {
            flyway(url, "1").migrate();
            try (var connection = connect(url); var sql = connection.createStatement()) {
                sql.executeUpdate(insert("REG-P-A-1", "PARTICIPANT", "same"));
                sql.executeUpdate(insert("REG-S-A-2", "STAFF", "same"));
            }
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway(url, null).migrate());
            try (var connection = connect(url); var sql = connection.createStatement()) {
                var result = sql.executeQuery("SELECT COUNT(*) FROM registration");
                assertTrue(result.next()); assertEquals(2, result.getInt(1));
            }
        });
    }

    @Test
    void baselineAdoptsLegacySchemaWithoutHistoryAndAppliesV2() throws Exception {
        withDatabase(url -> {
            try (var connection = connect(url); var sql = connection.createStatement()) {
                try (var stream = getClass().getResourceAsStream("/db/migration/V1__init.sql")) {
                    sql.executeUpdate(new String(java.util.Objects.requireNonNull(stream).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
                }
                sql.executeUpdate(insert("REG-P-A-8", "PARTICIPANT", "legacy"));
            }
            // Default configuration must refuse an unknown, non-empty schema.
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway(url, null).migrate());
            var adopted = Flyway.configure().dataSource(url, System.getenv("TEST_DB_USER"),
                    System.getenv("TEST_DB_PASSWORD")).baselineOnMigrate(true).baselineVersion("1").load();
            assertEquals(1, adopted.migrate().migrationsExecuted);
            try (var connection = connect(url); var sql = connection.createStatement()) {
                var result = sql.executeQuery("SELECT last_number FROM registration_counter");
                assertTrue(result.next()); assertEquals(8, result.getLong(1));
                result = sql.executeQuery("SELECT registration_code FROM registration");
                assertTrue(result.next()); assertEquals("REG-P-A-8", result.getString(1));
                assertFalse(result.next());
            }
            assertEquals(0, flyway(url, null).migrate().migrationsExecuted);
        });
    }

    private String insert(String code, String type, String person) {
        return "INSERT INTO registration (registration_code, registration_type, turnus_code, pesel_hash, payload, created_at) "
                + "VALUES ('" + code + "', '" + type + "', 'A', '" + person + "', '{}', NOW())";
    }

    private Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url, System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
        if (target != null) config.target(target);
        return config.load();
    }

    private java.sql.Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
    }

    private void withDatabase(DatabaseAction action) throws Exception {
        String original = System.getenv("TEST_DB_URL");
        String name = "integrity_migration_test";
        try (var connection = connect(original); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + name);
            try { action.run(original.substring(0, original.lastIndexOf('/') + 1) + name); }
            finally { sql.executeUpdate("DROP DATABASE " + name); }
        }
    }

    @FunctionalInterface
    interface DatabaseAction { void run(String url) throws Exception; }
}
