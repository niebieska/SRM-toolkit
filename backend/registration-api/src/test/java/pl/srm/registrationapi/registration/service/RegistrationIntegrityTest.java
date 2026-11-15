package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.dto.request.RegistrationEditRequest;
import pl.srm.registrationapi.registration.dto.request.PaymentRecordRequest;
import pl.srm.registrationapi.registration.dto.request.PaymentSettlementRequest;
import pl.srm.registrationapi.registration.dto.request.RefundRecordRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.management.RegistrationEditService;
import pl.srm.registrationapi.registration.service.management.RegistrationPaymentService;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
        "turnus.catalogue.url=http://127.0.0.1:9", "email.service.password=integration-only"
})
class RegistrationIntegrityTest {
    @Autowired RegistrationPersistenceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationManagementService managementService;
    @Autowired RegistrationEditService editService;
    @Autowired RegistrationPaymentService paymentService;
    @MockitoBean StatusNotificationService notificationService;
    @MockitoSpyBean pl.srm.registrationapi.registration.repository.RegistrationStatusHistoryRepository historyRepository;

    @BeforeEach
    void clearFixtures() {
        assertEquals("integrity_test", jdbc.queryForObject("SELECT DATABASE()", String.class),
                "Use only the disposable integrity_test database");
        jdbc.update("DELETE FROM registration_payment_audit");
        jdbc.update("DELETE FROM registration_payment_settlement_audit");
        jdbc.update("DELETE FROM registration_payment_settlement");
        jdbc.update("DELETE FROM registration_discount_audit");
        jdbc.update("DELETE FROM registration_payment");
        jdbc.update("DELETE FROM registration_discount");
        jdbc.update("DELETE FROM registration_edit_history");
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

    @Test
    void manualStaffRegistrationSourceIsStoredAndReturnedInDetails() {
        String code = service.saveStaffAccepted(context("MANUAL", "manual-person"), "{}", "account-id", "operator");

        assertEquals("BIURO", jdbc.queryForObject(
                "SELECT source FROM registration WHERE registration_code = ?", String.class, code));
        assertEquals("BIURO", managementService.getDetailByCode(code).source());
    }

    @Test
    void publicRegistrationMethodsUseFormularzSource() {
        String participant = service.saveParticipant(context("PUBLIC", "participant"), "{}");
        String staff = service.saveStaff(context("PUBLIC", "staff"), "{}");

        assertEquals("FORMULARZ", managementService.getDetailByCode(participant).source());
        assertEquals("FORMULARZ", managementService.getDetailByCode(staff).source());
    }

    @Test
    void repeatedPaymentRequestWithSameKeyCreatesOneRecordAndCorrectionWorksAfterStatusChange() {
        String code = service.saveParticipant(context("PAYMENT-IDEMPOTENCY", "payment-person"), "{}");
        jdbc.update("UPDATE registration SET status = 'ACCEPTED' WHERE registration_code = ?", code);
        var original = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 1),
                new java.math.BigDecimal("250.00"), "Przelew", "Zaliczka", null,
                "test-account", "operator.test", null);

        var first = paymentService.addPayment(code, "retry-key-1", original);
        var retry = paymentService.addPayment(code, "retry-key-1", original);

        assertEquals(first.payments().getFirst().id(), retry.payments().getFirst().id());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM registration_payment", Integer.class));
        var changedRetry = new PaymentRecordRequest(original.date(), new java.math.BigDecimal("300.00"),
                original.method(), original.label(), original.note(), original.actorAccountId(),
                original.actorUsername(), null);
        var conflict = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> paymentService.addPayment(code, "retry-key-1", changedRetry));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.getStatusCode());

        jdbc.update("UPDATE registration SET status = 'REJECTED' WHERE registration_code = ?", code);
        var correction = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 2),
                new java.math.BigDecimal("200.00"), "Przelew", "Zaliczka", "Skorygowana kwota",
                "test-account", "operator.test", "Kwota zgodna z potwierdzeniem.", "REJECTED", java.math.BigDecimal.ZERO);
        var updated = paymentService.editPayment(code, first.payments().getFirst().id(), correction);

        assertEquals(new java.math.BigDecimal("200.00"), updated.payments().getFirst().amount());
        assertEquals(1, updated.payments().getFirst().history().size());
        assertEquals("operator.test", updated.payments().getFirst().history().getFirst().actorUsername());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM registration_payment_audit", Integer.class));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> paymentService.addPayment(code, "retry-key-2", original)).getStatusCode());
    }

    @Test
    void acceptedRefundsUseCurrentMinimumInsteadOfHistoricalMaximum() {
        String code = service.saveParticipant(context("PAYMENT-REFUND-CURRENT", "refund-current-person"), "{}");
        jdbc.update("UPDATE registration SET status = 'ACCEPTED' WHERE registration_code = ?", code);
        var payment = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 1),
                new java.math.BigDecimal("1200.00"), "Przelew", "Wpłata", null,
                "test-account", "operator.test", null);
        paymentService.addPayment(code, "refund-current-payment", payment);

        var firstRefund = new RefundRecordRequest(java.time.LocalDate.of(2026, 11, 2),
                new java.math.BigDecimal("200.00"), "Przelew", "Zwrot wpłaty", null,
                "test-account", "operator.test", "ACCEPTED", new java.math.BigDecimal("1000.00"));
        paymentService.addRefund(code, "refund-current-1", firstRefund);
        var increasedDiscountRefund = new RefundRecordRequest(java.time.LocalDate.of(2026, 11, 3),
                new java.math.BigDecimal("300.00"), "Przelew", "Zwrot wpłaty", null,
                "test-account", "operator.test", "ACCEPTED", new java.math.BigDecimal("1000.00"));
        jdbc.update("""
                INSERT INTO registration_discount (registration_id, amount, description, changed_at, actor_account_id, actor_username)
                SELECT id, 300.00, 'Rabat', CURRENT_TIMESTAMP(6), 'test-account', 'operator.test'
                FROM registration WHERE registration_code = ?
                """, code);

        var result = paymentService.addRefund(code, "refund-current-2", increasedDiscountRefund);

        assertEquals(3, result.payments().size());
        assertEquals(new java.math.BigDecimal("700.00"), jdbc.queryForObject(
                "SELECT SUM(CASE WHEN entry_type = 'PAYMENT' THEN amount ELSE -amount END) "
                        + "FROM registration_payment WHERE registration_id = ?",
                java.math.BigDecimal.class, result.registrationId()));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> paymentService.addRefund(code, "refund-current-3",
                                new RefundRecordRequest(java.time.LocalDate.of(2026, 11, 4),
                                        new java.math.BigDecimal("1.00"), "Przelew", "Zwrot wpłaty", null,
                                        "test-account", "operator.test", "ACCEPTED", new java.math.BigDecimal("1000.00"))))
                        .getStatusCode());
    }

    @Test
    void editingAcceptedRefundUsesCurrentMinimumBalance() {
        String code = service.saveParticipant(context("PAYMENT-REFUND-EDIT-CURRENT", "refund-edit-person"), "{}");
        jdbc.update("UPDATE registration SET status = 'ACCEPTED' WHERE registration_code = ?", code);
        paymentService.addPayment(code, "refund-edit-current-payment", new PaymentRecordRequest(
                java.time.LocalDate.of(2026, 11, 1), new java.math.BigDecimal("1200.00"),
                "Przelew", "Wpłata", null, "test-account", "operator.test", null));
        var refund = paymentService.addRefund(code, "refund-edit-current-original", new RefundRecordRequest(
                java.time.LocalDate.of(2026, 11, 2), new java.math.BigDecimal("200.00"),
                "Przelew", "Zwrot wpłaty", null, "test-account", "operator.test",
                "ACCEPTED", new java.math.BigDecimal("1000.00")));
        jdbc.update("""
                INSERT INTO registration_discount (registration_id, amount, description, changed_at, actor_account_id, actor_username)
                SELECT id, 200.00, 'Rabat', CURRENT_TIMESTAMP(6), 'test-account', 'operator.test'
                FROM registration WHERE registration_code = ?
                """, code);
        long refundId = refund.payments().stream().filter(item -> "REFUND".equals(item.entryType()))
                .findFirst().orElseThrow().id();

        var corrected = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 3),
                new java.math.BigDecimal("400.00"), "Przelew", "Zwrot wpłaty", null,
                "test-account", "operator.test", "Kwota zgodna z potwierdzeniem.",
                "ACCEPTED", new java.math.BigDecimal("1000.00"));
        var updated = paymentService.editPayment(code, refundId, corrected);

        assertEquals(new java.math.BigDecimal("400.00"), updated.payments().stream()
                .filter(item -> item.id() == refundId).findFirst().orElseThrow().amount());
        assertEquals(1, updated.payments().stream().filter(item -> item.id() == refundId)
                .findFirst().orElseThrow().history().size());

        var overCurrentLimit = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 3),
                new java.math.BigDecimal("401.00"), "Przelew", "Zwrot wpłaty", null,
                "test-account", "operator.test", "Kolejna korekta.",
                "ACCEPTED", new java.math.BigDecimal("1000.00"));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> paymentService.editPayment(code, refundId, overCurrentLimit)).getStatusCode());
    }

    @Test
    void rejectedSettlementAndReacceptanceDoNotCarryOldRefundFloorIntoNewPayments() {
        String code = service.saveParticipant(context("PAYMENT-REFUND-REACCEPT", "refund-reaccept-person"), "{}");
        jdbc.update("UPDATE registration SET status = 'ACCEPTED' WHERE registration_code = ?", code);
        var payment = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 1),
                new java.math.BigDecimal("1200.00"), "Przelew", "Wpłata", null,
                "test-account", "operator.test", null);
        paymentService.addPayment(code, "refund-reaccept-payment", payment);
        paymentService.addRefund(code, "refund-reaccept-first", new RefundRecordRequest(
                java.time.LocalDate.of(2026, 11, 2), new java.math.BigDecimal("200.00"), "Przelew",
                "Zwrot wpłaty", null, "test-account", "operator.test", "ACCEPTED", new java.math.BigDecimal("1000.00")));

        jdbc.update("UPDATE registration SET status = 'REJECTED' WHERE registration_code = ?", code);
        paymentService.addRefund(code, "refund-reaccept-final", new RefundRecordRequest(
                java.time.LocalDate.of(2026, 11, 3), new java.math.BigDecimal("1000.00"), "Przelew",
                "Zwrot wpłaty", null, "test-account", "operator.test", "REJECTED", java.math.BigDecimal.ZERO));
        paymentService.settle(code, new PaymentSettlementRequest("Zwrot zakończony", "test-account", "operator.test"));

        jdbc.update("UPDATE registration SET status = 'ACCEPTED' WHERE registration_code = ?", code);
        var partialPayment = new PaymentRecordRequest(java.time.LocalDate.of(2026, 11, 4),
                new java.math.BigDecimal("500.00"), "Przelew", "Zaliczka", null,
                "test-account", "operator.test", null);
        var result = paymentService.addPayment(code, "refund-reaccept-partial", partialPayment);

        assertEquals(2, result.payments().stream().filter(entry -> "PAYMENT".equals(entry.entryType())).count());
        assertEquals(false, result.settlement().settled());
        assertEquals(2, result.settlement().history().size());
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
    void registrationDetailsReadCurrentStatusAndHistoryFromOneSnapshot() throws Exception {
        String code = service.saveParticipant(context("DETAIL-SNAPSHOT", "person"), "{}");
        var historyQueryStarted = new CountDownLatch(1);
        var allowHistoryQuery = new CountDownLatch(1);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        doAnswer(invocation -> {
            historyQueryStarted.countDown();
            if (!allowHistoryQuery.await(10, TimeUnit.SECONDS)) timedOut.set(true);
            return org.mockito.Mockito.mockingDetails(historyRepository)
                    .getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(historyRepository).findByRegistrationIdOrderByChangedAtDescIdDesc(any());

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<pl.srm.registrationapi.registration.dto.response.RegistrationDetailResponse> detailFuture =
                    executor.submit(() -> managementService.getDetailByCode(code));
            assertTrue(historyQueryStarted.await(10, TimeUnit.SECONDS));
            managementService.updateStatus(code,
                    new StatusUpdateRequest("ACCEPTED", null, "account-id", "operator"));
            allowHistoryQuery.countDown();

            var detail = detailFuture.get(10, TimeUnit.SECONDS);
            assertFalse(timedOut.get());
            assertEquals("NEW", detail.status());
            assertTrue(detail.statusHistory().isEmpty());
        } finally {
            allowHistoryQuery.countDown();
        }

        var latestDetail = managementService.getDetailByCode(code);
        assertEquals("ACCEPTED", latestDetail.status());
        assertEquals(1, latestDetail.statusHistory().size());
    }

    @Test
    void registrationEditPersistsPayloadVersionAndActorHistory() throws Exception {
        String code = service.saveParticipant(context("EDIT-PERSIST", "edit-person"), editablePayload());

        editService.update(code, new RegistrationEditRequest(0L,
                java.util.Map.of("person.firstName", "Alicja"), "account-id", "operator"));

        String payload = jdbc.queryForObject(
                "SELECT payload FROM registration WHERE registration_code = ?", String.class, code);
        assertTrue(payload.contains("\"firstName\":\"Alicja\""));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT version FROM registration WHERE registration_code = ?", Long.class, code));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM registration_edit_history h
                JOIN registration r ON r.id = h.registration_id
                WHERE r.registration_code = ? AND h.actor_account_id = 'account-id'
                  AND h.actor_username = 'operator' AND h.changed_fields = '["person.firstName"]'
                """, Integer.class, code));
    }

    @Test
    void failedEditHistoryInsertRollsBackPayloadVersionAndTimestamp() {
        String code = service.saveParticipant(context("EDIT-ROLLBACK", "rollback-person"), editablePayload());
        String originalPayload = jdbc.queryForObject(
                "SELECT payload FROM registration WHERE registration_code = ?", String.class, code);
        jdbc.execute("CREATE TRIGGER fail_edit_history_insert BEFORE INSERT ON registration_edit_history " +
                "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced edit history failure'");
        try {
            assertThrows(RuntimeException.class, () -> editService.update(code,
                    new RegistrationEditRequest(0L, java.util.Map.of("person.firstName", "Alicia"),
                            "account-id", "operator")));

            assertEquals(originalPayload, jdbc.queryForObject(
                    "SELECT payload FROM registration WHERE registration_code = ?", String.class, code));
            assertEquals(0L, jdbc.queryForObject(
                    "SELECT version FROM registration WHERE registration_code = ?", Long.class, code));
            assertNull(jdbc.queryForObject(
                    "SELECT updated_at FROM registration WHERE registration_code = ?", java.sql.Timestamp.class, code));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM registration_edit_history h
                    JOIN registration r ON r.id = h.registration_id WHERE r.registration_code = ?
                    """, Integer.class, code));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_edit_history_insert");
        }
    }

    private String editablePayload() {
        return """
                {"person":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"anna@example.org","phone":"+48123456789"}},
                 "address":{"street":"Leśna","houseNumber":"1","postalCode":"00-001","city":"Warszawa"},
                 "ice":{"firstName":"Jan","lastName":"Nowak","relation":"inna","relationOther":"mąż","phone":"+48123456789"},
                 "health":{"q1":{"answer":"nie","detail":null},"q2":{"answer":"nie","detail":null},"q3":{"answer":"nie","detail":null}}}
                """;
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
