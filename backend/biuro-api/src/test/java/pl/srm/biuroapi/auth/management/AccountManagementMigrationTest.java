package pl.srm.biuroapi.auth.management;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AccountManagementMigrationTest {
    @Test void v6PreservesExistingAccountsRolesSessionsAndCompletedBootstrap() {
        String url = System.getenv("TEST_BIURO_ACCOUNTS_MIGRATION_DB_URL");
        // Keep Flyway's physical connection alive: H2 2.4 check constraints can
        // retain the creating session. Match the application's pooled datasource.
        try (var source = new HikariDataSource()) {
            source.setMaximumPoolSize(2);
            source.setMinimumIdle(1);
            if (url == null || url.isBlank()) {
                source.setJdbcUrl("jdbc:h2:mem:account-upgrade-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1");
                source.setUsername("sa"); source.setPassword("");
            } else {
                if (!url.matches("jdbc:mariadb://[^/]+/biuro_accounts_migration_test"))
                    throw new IllegalArgumentException("Use a fresh disposable biuro_accounts_migration_test schema.");
                source.setJdbcUrl(url);
                source.setUsername(System.getenv("TEST_BIURO_ACCOUNTS_DB_USER"));
                source.setPassword(System.getenv("TEST_BIURO_ACCOUNTS_DB_PASSWORD"));
            }
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = new JdbcTemplate(source);
            String account = UUID.randomUUID().toString(), session = UUID.randomUUID().toString();
            String hash = new BCryptPasswordEncoder(4).encode("existing-password-123");
            jdbc.update("""
                    INSERT INTO biuro_account (id, username, display_name, password_hash, active,
                      password_change_required, created_at) VALUES (?, 'existing.admin', 'Existing', ?, TRUE, FALSE, ?)
                    """, account, hash, Timestamp.from(Instant.now()));
            jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, 'SYS_ADMIN')", account);
            jdbc.update("INSERT INTO biuro_session (id, account_id, expires_at) VALUES (?, ?, ?)",
                    session, account, Timestamp.from(Instant.now().plusSeconds(7200)));
            jdbc.update("UPDATE biuro_account_setup SET completed = TRUE WHERE id = 1");
            Flyway.configure().dataSource(source).load().migrate();
            assertEquals(0L, jdbc.queryForObject("SELECT version FROM biuro_account WHERE id = ?", Long.class, account));
            assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM biuro_account WHERE id = ?", String.class, account));
            assertEquals("SYS_ADMIN", jdbc.queryForObject("SELECT role FROM biuro_account_role WHERE account_id = ?", String.class, account));
            assertEquals(account, jdbc.queryForObject("SELECT account_id FROM biuro_session WHERE id = ?", String.class, session));
            assertTrue(jdbc.queryForObject("SELECT completed FROM biuro_account_setup WHERE id = 1", Boolean.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM turnus", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account_admin_lock", Integer.class));
            jdbc.update("INSERT INTO biuro_account_turnus (account_id, turnus_code) VALUES (?, 'ZAGLE26T1')", account);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account_turnus", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account_audit", Integer.class));
        }
    }
}
