package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.service.management.RegistrationStatusService;
import pl.srm.registrationapi.registration.service.management.StatusNotificationService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationStatusServiceTest {

    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final StatusNotificationService notificationService = mock(StatusNotificationService.class);
    private final RegistrationStatusService service = new RegistrationStatusService(repository, notificationService);

    @Test
    void acceptsWaitlistStatusAndClearsRejectionReason() {
        Registration registration = registration("REJECTED", "Brak miejsc");
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);

        Registration saved = service.updateStatus(
                "REG-P-ZAGLE26T1-1",
                new StatusUpdateRequest("WAITLIST", "ignorowane")
        );

        assertEquals("WAITLIST", saved.getStatus());
        assertNull(saved.getRejectionReason());
        assertNotNull(saved.getUpdatedAt());
        verify(repository).save(registration);
        verify(notificationService).sendStatusUpdate(registration);
    }

    @Test
    void keepsTrimmedRejectionReasonForRejectedStatus() {
        Registration registration = registration("NEW", null);
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);

        Registration saved = service.updateStatus(
                "REG-P-ZAGLE26T1-1",
                new StatusUpdateRequest("REJECTED", "  Brak wymaganych dokumentow  ")
        );

        assertEquals("REJECTED", saved.getStatus());
        assertEquals("Brak wymaganych dokumentow", saved.getRejectionReason());
        verify(notificationService).sendStatusUpdate(registration);
    }

    @Test
    void rejectsUnsupportedStatusWithoutSavingOrNotifying() {
        Registration registration = registration("NEW", null);
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> service.updateStatus("REG-P-ZAGLE26T1-1", new StatusUpdateRequest("NEW", null))
        );

        assertEquals("INVALID_STATUS", exception.getCode());
        verify(repository).findByRegistrationCode("REG-P-ZAGLE26T1-1");
        verify(repository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    private Registration registration(String status, String rejectionReason) {
        return new Registration(
                "REG-P-ZAGLE26T1-1",
                "PARTICIPANT",
                "ZAGLE26T1",
                "hash",
                false,
                status,
                rejectionReason,
                "{\"person\":{\"firstName\":\"Jan\",\"lastName\":\"Kowalski\",\"contact\":{\"email\":\"jan@example.com\"}}}",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null
        );
    }
}
