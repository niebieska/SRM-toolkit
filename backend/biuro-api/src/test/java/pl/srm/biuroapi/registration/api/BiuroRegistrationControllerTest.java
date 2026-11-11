package pl.srm.biuroapi.registration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
    void registrationEditForwardsAuthenticatedActorToRegistrationApi() {
        RegistrationEditRequest request = new RegistrationEditRequest(2L, Map.of("person.firstName", "Alicja"));
        RegistrationDetail expected = new RegistrationDetail("REG-1", "PARTICIPANT", "T1", false,
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
        return new TurnusDefinition(code, code, null, "Miejsce", TurnusDefinition.SeasonType.WINTER,
                2027, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 7), 14, 50, 10,
                active, true, true, 0, List.of());
    }

    private RegistrationSummary registration(String code, String turnusCode, String type) {
        return new RegistrationSummary(code, type, turnusCode, false, "NEW", null,
                "2026-11-01T12:00:00", "Test", "Osoba", 14, "female", null, null);
    }
}
