package pl.srm.biuroapi.registration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.service.RegistrationStatisticsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import java.util.Set;

class BiuroRegistrationControllerTest {

    private final RegistrationApiClient registrationApiClient = mock(RegistrationApiClient.class);
    private final RegistrationStatisticsService statisticsService = mock(RegistrationStatisticsService.class);

    private final BiuroRegistrationController controller = new BiuroRegistrationController(registrationApiClient,statisticsService);

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

    private AccountPrincipal actor() {
        return new AccountPrincipal("account-id", "operator", "Operator", false,
                Set.of(AccountRole.OPERATOR), "session-id");
    }
}
