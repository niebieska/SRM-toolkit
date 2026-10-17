package pl.srm.biuroapi.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.auth.service.AuthService;
import pl.srm.biuroapi.BiuroApiApplication;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "registration.api.password=test-service-password", "turnus.lookup.password=test-lookup-password",
        "spring.datasource.url=jdbc:h2:mem:login-concurrency;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="},
        classes = {BiuroApiApplication.class, LoginConcurrencyTest.ConcurrencyConfig.class})
class LoginConcurrencyTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_BIURO_ACCOUNTS_DB_URL");
        if (url == null || url.isBlank()) return;
        if (!url.matches("jdbc:mariadb://[^/]+/biuro_accounts_test"))
            throw new IllegalArgumentException("Use only a disposable biuro_accounts_test schema.");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_PASSWORD"));
    }

    @Autowired AuthService auth;
    @Autowired PausingAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PausingEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    String id;
    static final String PASSWORD = "original-password-123";

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM biuro_session");
        jdbc.update("DELETE FROM biuro_account_role");
        jdbc.update("DELETE FROM biuro_account");
        id = UUID.randomUUID().toString();
        accounts.create(id, "operator", "Operator", encoder.encode(PASSWORD), Set.of(AccountRole.OPERATOR), Instant.now());
    }

    @Test void passwordVerificationDoesNotHoldTransactionOrBlockAccountUpdates() throws Exception {
        runWhileVerifying(() -> jdbc.update("UPDATE biuro_account SET display_name = 'Changed' WHERE id = ?", id), false);
    }

    @Test void disableDuringPasswordVerificationPreventsSessionCreation() throws Exception {
        runWhileVerifying(() -> jdbc.update("UPDATE biuro_account SET active = FALSE WHERE id = ?", id), true);
    }

    @Test void passwordResetDuringVerificationPreventsOldPasswordSession() throws Exception {
        String replacementHash = encoder.encode("replacement-password-456");
        runWhileVerifying(() -> jdbc.update("UPDATE biuro_account SET password_hash = ? WHERE id = ?", replacementHash, id), true);
        assertNotNull(auth.login("operator", "replacement-password-456").token());
    }

    @Test void simultaneousUserLoginsDoNotDeadlockOnExpiredSessionCleanup() throws Exception {
        accounts.create(UUID.randomUUID().toString(), "other.operator", "Other operator",
                encoder.encode(PASSWORD), Set.of(AccountRole.OPERATOR), Instant.now());
        accounts.pauseCleanup(new CyclicBarrier(2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> auth.login("operator", PASSWORD));
            var second = pool.submit(() -> auth.login("other.operator", PASSWORD));
            assertNotNull(first.get(15, TimeUnit.SECONDS).token());
            assertNotNull(second.get(15, TimeUnit.SECONDS).token());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
        } finally {
            accounts.pauseCleanup(null);
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test void failedReplacementSessionRollsBackPasswordAndPreviousSessionDeletion() {
        auth.login("operator", PASSWORD);
        String sessionId = jdbc.queryForObject("SELECT id FROM biuro_session", String.class);
        var principal = accounts.findSession(id, sessionId, Instant.now()).orElseThrow();
        accounts.failNextSession();
        assertThrows(DataAccessResourceFailureException.class,
                () -> auth.changePassword(principal, PASSWORD, "replacement-password-456"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM biuro_account WHERE id = ?", String.class, id);
        assertTrue(encoder.matches(PASSWORD, hash));
        assertTrue(accounts.findSession(id, sessionId, Instant.now()).isPresent());
        assertTrue(jdbc.queryForObject("SELECT password_change_required FROM biuro_account WHERE id = ?", Boolean.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
    }

    private void runWhileVerifying(Runnable change, boolean rejectLogin) throws Exception {
        Gate gate = encoder.pauseNextMatch();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var login = pool.submit(() -> auth.login("operator", PASSWORD));
            assertTrue(gate.entered.await(10, TimeUnit.SECONDS));
            assertFalse(gate.transactionActive, "BCrypt verification must run outside a database transaction");
            var update = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> change.run()));
            // The update must commit while verification is still paused, not after it finishes.
            update.get(5, TimeUnit.SECONDS);
            gate.release.countDown();
            if (rejectLogin) {
                var failure = assertThrows(ExecutionException.class, () -> login.get(10, TimeUnit.SECONDS));
                var response = assertInstanceOf(ResponseStatusException.class, failure.getCause());
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
            } else {
                assertEquals("Changed", login.get(10, TimeUnit.SECONDS).user().displayName());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
            }
        } finally {
            gate.release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    static class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean transactionActive;
    }

    static class PausingEncoder implements PasswordEncoder {
        final PasswordEncoder delegate = new BCryptPasswordEncoder(4);
        final AtomicReference<Gate> next = new AtomicReference<>();
        Gate pauseNextMatch() { Gate gate = new Gate(); next.set(gate); return gate; }
        @Override public String encode(CharSequence password) { return delegate.encode(password); }
        @Override public boolean matches(CharSequence password, String hash) {
            boolean result = delegate.matches(password, hash);
            Gate gate = next.getAndSet(null);
            if (gate != null) {
                gate.transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
                gate.entered.countDown();
                try {
                    if (!gate.release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Verification gate timed out");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }
            return result;
        }
    }

    static class PausingAccountRepository extends AccountRepository {
        private final AtomicReference<CyclicBarrier> cleanupBarrier = new AtomicReference<>();
        private final AtomicBoolean rejectNextSession = new AtomicBoolean();
        PausingAccountRepository(JdbcTemplate jdbc) { super(jdbc); }
        public void pauseCleanup(CyclicBarrier barrier) { cleanupBarrier.set(barrier); }
        public void failNextSession() { rejectNextSession.set(true); }
        @Override public void createSession(String sessionId, String accountId, Instant expiresAt) {
            if (rejectNextSession.getAndSet(false))
                throw new DataAccessResourceFailureException("Synthetic session storage failure");
            super.createSession(sessionId, accountId, expiresAt);
        }
        @Override public void removeExpiredSessions(Instant now) {
            super.removeExpiredSessions(now);
            CyclicBarrier barrier = cleanupBarrier.get();
            if (barrier != null) {
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                catch (BrokenBarrierException | TimeoutException ex) { throw new IllegalStateException(ex); }
            }
        }
    }

    @TestConfiguration
    static class ConcurrencyConfig {
        @Bean @Primary PausingEncoder pausingEncoder() { return new PausingEncoder(); }
        @Bean @Primary PausingAccountRepository pausingAccounts(JdbcTemplate jdbc) { return new PausingAccountRepository(jdbc); }
    }
}
