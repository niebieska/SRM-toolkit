package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.management.StatusNotificationService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// Run against a disposable MariaDB with TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD.
// Never point these variables at an application database: fixtures are cleared per test.
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+/integrity_test")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USER}",
        "spring.datasource.password=${TEST_DB_PASSWORD}", "registration.service.password=integration-only",
        "spring.jpa.open-in-view=false", "turnus.catalogue.password=integration-only",
        "turnus.catalogue.url=http://127.0.0.1:9"
})
class RegistrationIntegrityTest {
    @Autowired RegistrationPersistenceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationManagementService managementService;
    @MockitoBean StatusNotificationService notificationService;

    @BeforeEach
    void clearFixtures() {
        assertEquals("integrity_test", jdbc.queryForObject("SELECT DATABASE()", String.class),
                "Use only the disposable integrity_test database");
        jdbc.update("DELETE FROM registration_status_history");
        jdbc.update("DELETE FROM registration");
        jdbc.update("DELETE FROM registration_counter");
    }

    private RegistrationContext context(String turnus, String person) {
        return new RegistrationContext(turnus, "unused", person, false, false, true);
    }

    @Test
    void acceptedCountsUsePersistedStatusesAndSeparateTypesAndTurnusy() {
        service.saveParticipant(context("A", "one"), "{}");
        service.saveParticipant(context("A", "two"), "{}");
        service.saveStaff(context("A", "three"), "{}");
        service.saveStaff(context("B", "four"), "{}");
        jdbc.update("UPDATE registration SET status = 'ACCEPTED'");
        jdbc.update("UPDATE registration SET status = 'WAITLIST' WHERE pesel_hash = 'two'");
        assertEquals(1L, repository.countByTurnusCodeAndRegistrationTypeAndStatus("A", "PARTICIPANT", "ACCEPTED"));
        assertEquals(1L, repository.countByTurnusCodeAndRegistrationTypeAndStatus("A", "STAFF", "ACCEPTED"));
        jdbc.update("UPDATE registration SET status = 'REJECTED' WHERE pesel_hash = 'three'");
        assertEquals(0L, repository.countByTurnusCodeAndRegistrationTypeAndStatus("A", "STAFF", "ACCEPTED"));
    }
    @Autowired pl.srm.registrationapi.registration.repository.RegistrationRepository repository;

    @Test
    void turnusListsQueryIncludesBothTypesButExcludesOtherTurnusyAndStatuses() {
        service.saveParticipant(context("A", "accepted-participant"), "{}");
        service.saveStaff(context("A", "accepted-staff"), "{}");
        service.saveParticipant(context("B", "other-turnus"), "{}");
        service.saveParticipant(context("A", "new-person"), "{}");
        service.saveParticipant(context("A", "waitlist-person"), "{}");
        service.saveParticipant(context("A", "rejected-person"), "{}");
        jdbc.update("UPDATE registration SET status = 'ACCEPTED' WHERE pesel_hash IN ('accepted-participant', 'accepted-staff', 'other-turnus')");
        jdbc.update("UPDATE registration SET status = 'WAITLIST' WHERE pesel_hash = 'waitlist-person'");
        jdbc.update("UPDATE registration SET status = 'REJECTED' WHERE pesel_hash = 'rejected-person'");
        var rows = repository.findByTurnusCodeAndStatus("A", "ACCEPTED");
        assertEquals(2, rows.size());
        assertEquals(Set.of("PARTICIPANT", "STAFF"), rows.stream()
                .map(Registration::getRegistrationType).collect(Collectors.toSet()));
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
    void failedHistoryInsertRollsBackStatusAndDoesNotNotify() {
        String code = service.saveParticipant(context("STATUS-ROLLBACK", "person"), "{}");
        jdbc.execute("CREATE TRIGGER fail_status_history_insert BEFORE INSERT ON registration_status_history " +
                "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced history failure'");
        try {
            assertThrows(RuntimeException.class, () -> managementService.updateStatus(code,
                    new StatusUpdateRequest("REJECTED", "test reason", "account-id", "operator")));

            assertEquals("NEW", jdbc.queryForObject(
                    "SELECT status FROM registration WHERE registration_code = ?", String.class, code));
            assertNull(jdbc.queryForObject(
                    "SELECT rejection_reason FROM registration WHERE registration_code = ?", String.class, code));
            assertNull(jdbc.queryForObject(
                    "SELECT updated_at FROM registration WHERE registration_code = ?", java.sql.Timestamp.class, code));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM registration_status_history WHERE registration_id = " +
                            "(SELECT id FROM registration WHERE registration_code = ?)", Integer.class, code));
            verifyNoInteractions(notificationService);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_status_history_insert");
        }
    }

    @Test
    void notificationRunsAfterStatusAndHistoryAreCommitted() {
        String code = service.saveParticipant(context("STATUS-COMMIT", "person"), "{}");
        AtomicReference<String> statusObservedByNotification = new AtomicReference<>();
        AtomicReference<Integer> historyCountObservedByNotification = new AtomicReference<>();
        doAnswer(invocation -> {
            Registration updated = invocation.getArgument(0);
            statusObservedByNotification.set(jdbc.queryForObject(
                    "SELECT status FROM registration WHERE registration_code = ?", String.class,
                    updated.getRegistrationCode()));
            historyCountObservedByNotification.set(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM registration_status_history WHERE registration_id = ?",
                    Integer.class, updated.getId()));
            return null;
        }).when(notificationService).sendStatusUpdate(any(Registration.class));

        managementService.updateStatus(code,
                new StatusUpdateRequest("ACCEPTED", null, "account-id", "operator"));

        assertEquals("ACCEPTED", statusObservedByNotification.get());
        assertEquals(1, historyCountObservedByNotification.get());
        verify(notificationService).sendStatusUpdate(any(Registration.class));
    }

    @Test
    void concurrentStatusUpdatesRecordAContinuousTransitionChain() throws Exception {
        String code = service.saveParticipant(context("STATUS-RACE", "person"), "{}");
        race(2, i -> {
            String status = i == 0 ? "ACCEPTED" : "REJECTED";
            managementService.updateStatus(code,
                    new StatusUpdateRequest(status, "race test", "account-id", "operator"));
            return status;
        });

        Long registrationId = jdbc.queryForObject(
                "SELECT id FROM registration WHERE registration_code = ?", Long.class, code);
        List<java.util.Map<String, Object>> history = jdbc.queryForList(
                "SELECT previous_status, new_status FROM registration_status_history " +
                        "WHERE registration_id = ? ORDER BY id", registrationId);
        assertEquals(2, history.size());
        assertEquals("NEW", history.get(0).get("previous_status"));
        assertEquals(history.get(0).get("new_status"), history.get(1).get("previous_status"));
        assertEquals(jdbc.queryForObject(
                "SELECT status FROM registration WHERE id = ?", String.class, registrationId),
                history.get(1).get("new_status"));
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
