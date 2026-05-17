package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.api.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.api.StatusUpdateRequest;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationManagementServiceTest {

    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final RegistrationManagementService service = new RegistrationManagementService(repository);

    @Test
    void updatesStatusAndTimestamp() {
        Registration registration = new Registration(
                "REG-P-ZAGLE26T1-1",
                "PARTICIPANT",
                "ZAGLE26T1",
                "hash",
                true,
                "NEW",
                null,
                "{}",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null
        );
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);

        RegistrationSummaryResponse response = service.updateStatus(
                "REG-P-ZAGLE26T1-1",
                new StatusUpdateRequest("REJECTED", "Brak miejsc")
        );

        assertEquals("REJECTED", response.status());
        assertEquals("Brak miejsc", response.rejectionReason());
        assertNotNull(registration.getUpdatedAt());
        verify(repository).save(registration);
    }

    @Test
    void rejectsUnknownStatus() {
        Registration registration = new Registration(
                "REG-P-ZAGLE26T1-1",
                "PARTICIPANT",
                "ZAGLE26T1",
                "hash",
                false,
                "NEW",
                null,
                "{}",
                LocalDateTime.now(),
                null
        );
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> service.updateStatus("REG-P-ZAGLE26T1-1", new StatusUpdateRequest("PENDING", null))
        );

        assertEquals("INVALID_STATUS", exception.getCode());
    }
}
