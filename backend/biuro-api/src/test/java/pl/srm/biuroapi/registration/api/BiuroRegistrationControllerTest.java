package pl.srm.biuroapi.registration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.registration.model.RegistrationDetail;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import java.util.List;
import java.util.Map;
import pl.srm.biuroapi.registration.api.RegistrationEditRequest;
import pl.srm.biuroapi.registration.service.RegistrationStatisticsService;
import pl.srm.biuroapi.turnus.TurnusCatalogue;
import pl.srm.biuroapi.turnus.TurnusDefinition;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Set;

class BiuroRegistrationControllerTest {

    private final RegistrationApiClient registrationApiClient = mock(RegistrationApiClient.class);
    private final RegistrationStatisticsService statisticsService = mock(RegistrationStatisticsService.class);
    private final TurnusCatalogue turnusCatalogue = mock(TurnusCatalogue.class);

    private final BiuroRegistrationController controller = new BiuroRegistrationController(
            registrationApiClient, statisticsService, turnusCatalogue);

    @Test
    void registrationListIncludesOnlyRegistrationsFromActiveTurnuses() {
        RegistrationSummary activeRegistration = registration("REG-P-ACTIVE-1", "ACTIVE", "PARTICIPANT");
        RegistrationSummary inactiveRegistration = registration("REG-P-INACTIVE-1", "INACTIVE", "PARTICIPANT");
        when(turnusCatalogue.list()).thenReturn(List.of(turnus("ACTIVE", true), turnus("INACTIVE", false)));
        when(registrationApiClient.fetchRegistrations()).thenReturn(List.of(activeRegistration, inactiveRegistration));

        assertEquals(List.of(activeRegistration), controller.getRegistrations(null, null, null));
    }

    @Test
    void explicitFilterForInactiveTurnusReturnsNoRegistrations() {
        when(turnusCatalogue.list()).thenReturn(List.of(turnus("INACTIVE", false)));
        when(registrationApiClient.fetchRegistrations()).thenReturn(
                List.of(registration("REG-P-INACTIVE-1", "INACTIVE", "PARTICIPANT")));

        assertEquals(List.of(), controller.getRegistrations(null, null, "INACTIVE"));
    }

    @Test
    void shouldRejectUnsupportedStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
            () -> controller.updateStatus("REG-1", new StatusUpdateRequest("NEW", null), actor())
        );

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
    }

    @Test
    void shouldRequireRejectionReasonForRejectedStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
            () -> controller.updateStatus("REG-1", new StatusUpdateRequest("REJECTED", " "), actor())
        );

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
    }

    @Test
    void shouldAllowWaitlistStatus() {
        StatusUpdateRequest request = new StatusUpdateRequest("WAITLIST", null);

        controller.updateStatus("REG-1", request, actor());

        verify(registrationApiClient).updateStatus("REG-1", request, "account-id", "operator");
    }

    @Test
    void shouldRejectParticipantAcceptanceWhenParticipantCapacityIsFull() {
        StatusUpdateRequest request = new StatusUpdateRequest("ACCEPTED", null);
        mockAcceptanceCapacity("PARTICIPANT", 50, 9, 50, 10);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus("REG-1", request, actor()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), exception.getStatusCode().value());
        assertEquals("Turnus zapełniony.", exception.getReason());
        verify(registrationApiClient).fetchAcceptedCounts("T1");
        verify(registrationApiClient, never())
                .updateStatus("REG-1", request, "account-id", "operator");
    }

    @Test
    void shouldRejectStaffAcceptanceWhenStaffCapacityIsFull() {
        StatusUpdateRequest request = new StatusUpdateRequest("ACCEPTED", null);
        mockAcceptanceCapacity("STAFF", 49, 10, 50, 10);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus("REG-1", request, actor()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), exception.getStatusCode().value());
        assertEquals("Turnus zapełniony.", exception.getReason());
        verify(registrationApiClient).fetchAcceptedCounts("T1");
        verify(registrationApiClient, never())
                .updateStatus("REG-1", request, "account-id", "operator");
    }

    @Test
    void shouldAllowParticipantAcceptanceWhenParticipantPlaceIsAvailable() {
        assertAcceptanceAllowed("PARTICIPANT", 49, 10, 50, 10);
    }

    @Test
    void shouldAllowStaffAcceptanceWhenStaffPlaceIsAvailable() {
        assertAcceptanceAllowed("STAFF", 50, 9, 50, 10);
    }

    @Test
    void concurrentAcceptancesCannotBothUseTheLastPlace() throws Exception {
        StatusUpdateRequest request = new StatusUpdateRequest("ACCEPTED", null);
        AtomicInteger accepted = new AtomicInteger();
        for (String code : List.of("REG-1", "REG-2")) {
            when(registrationApiClient.fetchRegistration(code)).thenReturn(
                    new RegistrationDetail(code, "PARTICIPANT", "FORMULARZ", "T1", false,
                            "NEW", null, null, "Test", "Osoba", 18, Map.of(), List.of(), 1L, List.of()));
            when(registrationApiClient.updateStatus(eq(code), eq(request), eq("account-id"), eq("operator")))
                    .thenAnswer(invocation -> {
                        accepted.incrementAndGet();
                        return new RegistrationSummary(code, "PARTICIPANT", "T1", false, "ACCEPTED", null,
                                "2026-11-01T12:00:00", "Test", "Osoba", 18, "female", null, null);
                    });
        }
        when(turnusCatalogue.get("T1")).thenReturn(turnus("T1", true, 1, 10));

        AtomicInteger countCalls = new AtomicInteger();
        CountDownLatch secondCountCall = new CountDownLatch(1);
        when(registrationApiClient.fetchAcceptedCounts("T1")).thenAnswer(invocation -> {
            if (countCalls.getAndIncrement() == 0) {
                secondCountCall.await(300, TimeUnit.MILLISECONDS);
            } else {
                secondCountCall.countDown();
            }
            return new RegistrationApiClient.AcceptedCounts((long) accepted.get(), 0L);
        });
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptAcceptance(start, request, "REG-1"));
            var second = executor.submit(() -> attemptAcceptance(start, request, "REG-2"));
            start.countDown();

            List<String> outcomes = List.of(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("accepted"::equals).count());
            assertEquals(1, outcomes.stream().filter("full"::equals).count());
            assertEquals(1, accepted.get());
        }
    }

    @Test
    void concurrentManualStaffRequestsCannotBothUseTheLastPlace() throws Exception {
        var payload = new ObjectMapper().readTree("{\"turnusCode\":\"T1\"}");
        when(turnusCatalogue.get("T1")).thenReturn(turnus("T1", true, 10, 1));
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger countCalls = new AtomicInteger();
        CountDownLatch secondCountCall = new CountDownLatch(1);
        when(registrationApiClient.fetchAcceptedCounts("T1")).thenAnswer(invocation -> {
            if (countCalls.getAndIncrement() == 0) {
                secondCountCall.await(300, TimeUnit.MILLISECONDS);
            } else {
                secondCountCall.countDown();
            }
            return new RegistrationApiClient.AcceptedCounts(0L, (long) accepted.get());
        });
        when(registrationApiClient.createManualStaffRegistration(payload.toString(), "account-id", "operator"))
                .thenAnswer(invocation -> {
                    int sequence = accepted.incrementAndGet();
                    return new RegistrationApiClient.RegistrationCreated("REG-S-T1-" + sequence);
                });

        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptManualStaffRegistration(start, payload));
            var second = executor.submit(() -> attemptManualStaffRegistration(start, payload));
            start.countDown();

            List<String> outcomes = List.of(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("created"::equals).count());
            assertEquals(1, outcomes.stream().filter("full"::equals).count());
            assertEquals(1, accepted.get());
        }
    }

    private String attemptAcceptance(CountDownLatch start, StatusUpdateRequest request, String code) throws Exception {
        start.await();
        try {
            controller.updateStatus(code, request, actor());
            return "accepted";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) return "full";
            throw exception;
        }
    }

    private String attemptManualStaffRegistration(CountDownLatch start, tools.jackson.databind.JsonNode payload)
            throws Exception {
        start.await();
        try {
            controller.createManualStaffRegistration(payload, actor());
            return "created";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) return "full";
            throw exception;
        }
    }

    @Test
    void shouldRejectManualStaffRegistrationWhenStaffCapacityIsFull() throws Exception {
        String payloadJson = "{\"turnusCode\":\"T1\"}";
        var payload = new ObjectMapper().readTree(payloadJson);
        when(turnusCatalogue.get("T1")).thenReturn(turnus("T1", true));
        when(registrationApiClient.fetchAcceptedCounts("T1"))
                .thenReturn(new RegistrationApiClient.AcceptedCounts(20L, 10L));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.createManualStaffRegistration(payload, actor()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), exception.getStatusCode().value());
        assertEquals("Brak wolnych miejsc dla kadry (10/10). Nie można dodać kolejnej osoby do turnusu.",
                exception.getReason());
        verify(registrationApiClient, never())
                .createManualStaffRegistration(payloadJson, "account-id", "operator");
    }

    @Test
    void shouldAllowManualStaffRegistrationWhenStaffPlaceIsAvailable() throws Exception {
        String payloadJson = "{\"turnusCode\":\"T1\"}";
        var payload = new ObjectMapper().readTree(payloadJson);
        var expected = new RegistrationApiClient.RegistrationCreated("REG-S-T1-1");
        when(turnusCatalogue.get("T1")).thenReturn(turnus("T1", true));
        when(registrationApiClient.fetchAcceptedCounts("T1"))
                .thenReturn(new RegistrationApiClient.AcceptedCounts(50L, 9L));
        when(registrationApiClient.createManualStaffRegistration(payloadJson, "account-id", "operator"))
                .thenReturn(expected);

        assertEquals(expected, controller.createManualStaffRegistration(payload, actor()));
        verify(registrationApiClient).createManualStaffRegistration(payloadJson, "account-id", "operator");
    }

    @Test
    void registrationEditForwardsAuthenticatedActorToRegistrationApi() {
        RegistrationEditRequest request = new RegistrationEditRequest(2L, Map.of("person.firstName", "Alicja"));
        RegistrationDetail expected = new RegistrationDetail("REG-1", "PARTICIPANT", "FORMULARZ", "T1", false,
                "NEW", null, null, null, null, null, Map.of(), List.of(), 3L, List.of());
        when(registrationApiClient.updateRegistrationData("REG-1", request, "account-id", "operator"))
                .thenReturn(expected);

        assertEquals(expected, controller.updateRegistrationData("REG-1", request, actor()));
        verify(registrationApiClient).updateRegistrationData("REG-1", request, "account-id", "operator");
    }

    private AccountPrincipal actor() {
        return new AccountPrincipal("account-id", "operator", "Operator", false,
                Set.of(AccountRole.OPERATOR), "session-id");
    }

    private TurnusDefinition turnus(String code, boolean active) {
        return turnus(code, active, 50, 10);
    }

    private TurnusDefinition turnus(String code, boolean active, int participantCapacity, int staffCapacity) {
        return new TurnusDefinition(code, code, null, "Miejsce", TurnusDefinition.SeasonType.WINTER,
                2027, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 7), 14, participantCapacity, staffCapacity,
                active, true, true, 0, List.of());
    }

    private void mockAcceptanceCapacity(String type, long participants, long staff,
                                        int participantCapacity, int staffCapacity) {
        when(registrationApiClient.fetchRegistration("REG-1")).thenReturn(
                new RegistrationDetail("REG-1", type, "FORMULARZ", "T1", false,
                        "NEW", null, null, "Test", "Osoba", 18, Map.of(), List.of(), 1L, List.of()));
        when(turnusCatalogue.get("T1")).thenReturn(turnus("T1", true, participantCapacity, staffCapacity));
        when(registrationApiClient.fetchAcceptedCounts("T1"))
                .thenReturn(new RegistrationApiClient.AcceptedCounts(participants, staff));
    }

    private void assertAcceptanceAllowed(String type, long participants, long staff,
                                         int participantCapacity, int staffCapacity) {
        StatusUpdateRequest request = new StatusUpdateRequest("ACCEPTED", null);
        RegistrationSummary accepted = new RegistrationSummary("REG-1", type, "T1", false, "ACCEPTED", null,
                "2026-11-01T12:00:00", "Test", "Osoba", 18, "female", null, null);
        mockAcceptanceCapacity(type, participants, staff, participantCapacity, staffCapacity);
        when(registrationApiClient.updateStatus("REG-1", request, "account-id", "operator")).thenReturn(accepted);

        assertEquals(accepted, controller.updateStatus("REG-1", request, actor()));
        verify(registrationApiClient).fetchAcceptedCounts("T1");
        verify(registrationApiClient).updateStatus("REG-1", request, "account-id", "operator");
    }

    private RegistrationSummary registration(String code, String turnusCode, String type) {
        return new RegistrationSummary(code, type, turnusCode, false, "NEW", null,
                "2026-11-01T12:00:00", "Test", "Osoba", 14, "female", null, null);
    }
}
