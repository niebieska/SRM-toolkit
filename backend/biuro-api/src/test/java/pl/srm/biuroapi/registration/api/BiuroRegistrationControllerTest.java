package pl.srm.biuroapi.registration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.service.RegistrationStatisticsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BiuroRegistrationControllerTest {

    private final RegistrationApiClient registrationApiClient = mock(RegistrationApiClient.class);
    private final RegistrationStatisticsService statisticsService = mock(RegistrationStatisticsService.class);

    private final BiuroRegistrationController controller = new BiuroRegistrationController(registrationApiClient,statisticsService);

    @Test
    void shouldRejectUnsupportedStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateStatus("REG-1", new StatusUpdateRequest("NEW", null))
        );

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
    }

    @Test
    void shouldRequireRejectionReasonForRejectedStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateStatus("REG-1", new StatusUpdateRequest("REJECTED", " "))
        );

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatusCode().value());
    }
}
