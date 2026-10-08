package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Run against a disposable MariaDB with TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD.
// Never point these variables at an application database: fixtures are cleared per test.
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USER}",
        "spring.datasource.password=${TEST_DB_PASSWORD}", "registration.service.password=integration-only",
        "spring.jpa.open-in-view=false"
})
class RegistrationIntegrityTest {
    @Autowired RegistrationPersistenceService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearFixtures() {
        assertEquals("integrity_test", jdbc.queryForObject("SELECT DATABASE()", String.class),
                "Use only the disposable integrity_test database");
        jdbc.update("DELETE FROM registration");
        jdbc.update("DELETE FROM registration_counter");
    }

    private RegistrationContext context(String turnus, String person) {
        return new RegistrationContext(turnus, "unused", person, false, false, true);
    }

    @Test
    void concurrentFirstUseAllocatesDistinctNumbers() throws Exception {
        var codes = race(16, i -> service.saveParticipant(context("RACE", "person-" + i), "{}"));
        assertEquals(16, new HashSet<>(codes).size());
        for (int n = 1; n <= 16; n++) assertTrue(codes.contains("REG-P-RACE-" + n));
        assertEquals(16L, jdbc.queryForObject("SELECT last_number FROM registration_counter", Long.class));
    }

    @Test
    void concurrentDuplicateAcrossTypesHasOneWinner() throws Exception {
        var outcomes = race(12, i -> {
            try {
                return i % 2 == 0 ? service.saveParticipant(context("DUP", "same-person"), "{}")
                        : service.saveStaff(context("DUP", "same-person"), "{}");
            } catch (RegistrationException ex) {
                assertEquals("ALREADY_REGISTERED", ex.getCode());
                return "duplicate";
            }
        });
        assertEquals(11, outcomes.stream().filter("duplicate"::equals).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM registration", Integer.class));
        assertEquals(1L, jdbc.queryForObject("SELECT SUM(last_number) FROM registration_counter", Long.class));
    }

    @Test
    void independentGroupsAllowSamePersonOnDifferentTurnusy() {
        assertEquals("REG-P-A-1", service.saveParticipant(context("A", "one"), "{}"));
        assertEquals("REG-S-A-1", service.saveStaff(context("A", "two"), "{}"));
        assertEquals("REG-P-B-1", service.saveParticipant(context("B", "one"), "{}"));
        assertEquals("REG-P-A-2", service.saveParticipant(context("A", "three"), "{}"));
    }

    @Test
    void rejectionAndDeletionDoNotReuseIssuedNumbers() {
        service.saveParticipant(context("A", "one"), "{}");
        jdbc.update("UPDATE registration SET status = 'REJECTED'");
        assertThrows(RegistrationException.class, () -> service.saveStaff(context("A", "one"), "{}"));
        assertEquals("REG-P-A-2", service.saveParticipant(context("A", "two"), "{}"));
        jdbc.update("DELETE FROM registration");
        assertEquals("REG-P-A-3", service.saveParticipant(context("A", "three"), "{}"));
    }

    @Test
    void failedInsertRollsBackAllocationAndDoesNotBecomeDuplicateError() {
        // NOT NULL violation is unrelated to the person uniqueness constraint.
        assertFalse(assertThrows(RuntimeException.class, () -> service.saveParticipant(context("A", "one"), null)) instanceof RegistrationException);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM registration_counter", Integer.class));
        assertEquals("REG-P-A-1", service.saveParticipant(context("A", "two"), "{}"));
        assertFalse(assertThrows(RuntimeException.class, () -> service.saveParticipant(context("A", "three"), null)) instanceof RegistrationException);
        assertEquals("REG-P-A-2", service.saveParticipant(context("A", "four"), "{}"));
    }

    @Test
    void databaseEnforcesUniquenessEvenWithoutApplicationChecks() {
        service.saveParticipant(context("A", "one"), "{}");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO registration (registration_code, registration_type, turnus_code, pesel_hash,
                    payload, created_at) VALUES ('REG-S-A-99', 'STAFF', 'A', 'one', '{}', NOW())
                """));
    }

    private List<String> race(int count, java.util.function.IntFunction<String> action) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(executor.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return action.apply(index); }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
}
