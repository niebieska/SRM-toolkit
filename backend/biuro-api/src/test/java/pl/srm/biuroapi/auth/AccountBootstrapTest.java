package pl.srm.biuroapi.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import pl.srm.biuroapi.auth.account.AccountBootstrap;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.account.AccountAudit;

import java.time.Clock;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "registration.api.password=test-service-password", "turnus.lookup.password=test-lookup-password",
        "spring.datasource.url=jdbc:h2:mem:account-bootstrap;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class AccountBootstrapTest {
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_BIURO_ACCOUNTS_DB_URL");
        if (url == null || url.isBlank()) return;
        if (!url.matches("jdbc:mariadb://[^/]+/biuro_accounts_test"))
            throw new IllegalArgumentException("Use only a disposable biuro_accounts_test schema.");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_PASSWORD"));
    }

    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountAudit audit;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM biuro_session");
        jdbc.update("DELETE FROM biuro_account_role");
        jdbc.update("DELETE FROM biuro_account");
        jdbc.update("UPDATE biuro_account_setup SET completed = FALSE WHERE id = 1");
    }
    AccountBootstrap bootstrap(boolean enabled, String username, String password) {
        return new AccountBootstrap(accounts, encoder, clock, manager, audit, enabled, username, "Administrator", password);
    }
    void run(AccountBootstrap runner) { runner.run(new DefaultApplicationArguments()); }

    @Test void disabledBootstrapDoesNotCreateAnAccount() {
        run(bootstrap(false, "", ""));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
    }

    @Test void invalidConfigurationLeavesSetupRetryableAndNoPartialAccount() {
        for (String password : new String[]{"", "short", "ą".repeat(40)})
            assertThrows(RuntimeException.class, () -> run(bootstrap(true, "admin", password)));
        assertThrows(RuntimeException.class, () -> run(bootstrap(true, "bad username", "temporary-password-123")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
        assertEquals(false, jdbc.queryForObject("SELECT completed FROM biuro_account_setup WHERE id=1", Boolean.class));
        run(bootstrap(true, "admin", "temporary-password-123"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
    }

    @Test void createsOnlyAdminOnceWithHashAndMandatoryPasswordChange() {
        run(bootstrap(true, " ADMIN ", "temporary-password-123"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM biuro_account", String.class);
        assertTrue(encoder.matches("temporary-password-123", hash));
        assertEquals("admin", jdbc.queryForObject("SELECT username FROM biuro_account", String.class));
        assertEquals(true, jdbc.queryForObject("SELECT password_change_required FROM biuro_account", Boolean.class));
        assertEquals("SYS_ADMIN", jdbc.queryForObject("SELECT role FROM biuro_account_role", String.class));
        run(bootstrap(true, "replacement-admin", "replacement-password-123"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM biuro_account", String.class));
        // Completed setup is never a password-reset/recreation backdoor, even if rows are removed.
        jdbc.update("DELETE FROM biuro_account_role");
        jdbc.update("DELETE FROM biuro_account");
        run(bootstrap(true, "replacement-admin", "replacement-password-123"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
    }

    @Test void concurrentBootstrapCreatesExactlyOneAdministrator() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        Callable<Void> action = () -> {
            gate.await(); run(bootstrap(true, "admin", "temporary-password-123")); return null;
        };
        try {
            var first = pool.submit(action); var second = pool.submit(action); gate.countDown();
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account_role", Integer.class));
    }
}
