package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationStatusHistory;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.repository.RegistrationStatusHistoryRepository;
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
    private final RegistrationStatusHistoryRepository historyRepository = mock(RegistrationStatusHistoryRepository.class);
    private final StatusNotificationService notificationService = mock(StatusNotificationService.class);
    private final RegistrationStatusService service = new RegistrationStatusService(repository, historyRepository, notificationService);

    @Test
    void acceptsWaitlistStatusAndClearsRejectionReason() {
        Registration registration = registration("REJECTED", "Brak miejsc");
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);

        Registration saved = service.updateStatus(
                "REG-P-ZAGLE26T1-1",
                request("WAITLIST", "ignorowane")
        );

        assertEquals("WAITLIST", saved.getStatus());
        assertNull(saved.getRejectionReason());
        assertNotNull(saved.getUpdatedAt());
        verify(repository).save(registration);
        var history = org.mockito.ArgumentCaptor.forClass(RegistrationStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertEquals("REJECTED", history.getValue().getPreviousStatus());
        assertEquals("WAITLIST", history.getValue().getNewStatus());
        assertEquals("operator-123", history.getValue().getActorAccountId());
        assertEquals("operator", history.getValue().getActorUsername());
        verify(notificationService).sendStatusUpdate(registration);
    }

    @Test
    void keepsTrimmedRejectionReasonForRejectedStatus() {
        Registration registration = registration("NEW", null);
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);

        Registration saved = service.updateStatus(
                "REG-P-ZAGLE26T1-1",
                request("REJECTED", "  Brak wymaganych dokumentow  ")
        );

        assertEquals("REJECTED", saved.getStatus());
        assertEquals("Brak wymaganych dokumentow", saved.getRejectionReason());
        var history = org.mockito.ArgumentCaptor.forClass(RegistrationStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertEquals("NEW", history.getValue().getPreviousStatus());
        assertEquals("Brak wymaganych dokumentow", history.getValue().getRejectionReason());
        verify(notificationService).sendStatusUpdate(registration);
    }

    @Test
    void doesNotAddHistoryWhenTheStatusIsUnchanged() {
        Registration registration = registration("ACCEPTED", null);
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);

        service.updateStatus("REG-P-ZAGLE26T1-1", request("ACCEPTED", null));

        verifyNoInteractions(historyRepository);
        verify(notificationService).sendStatusUpdate(registration);
    }

    @Test
    void rejectsUnsupportedStatusWithoutSavingOrNotifying() {
        Registration registration = registration("NEW", null);
        when(repository.findByRegistrationCode("REG-P-ZAGLE26T1-1")).thenReturn(Optional.of(registration));

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> service.updateStatus("REG-P-ZAGLE26T1-1", request("NEW", null))
        );

        assertEquals("INVALID_STATUS", exception.getCode());
        verify(repository).findByRegistrationCode("REG-P-ZAGLE26T1-1");
        verify(repository, never()).save(any());
        verifyNoInteractions(notificationService);
        verifyNoInteractions(historyRepository);
    }

    private StatusUpdateRequest request(String status, String rejectionReason) {
        return new StatusUpdateRequest(status, rejectionReason, "operator-123", "operator");
    }

    private Registration registration(String status, String rejectionReason) {
        Registration registration = new Registration(
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
        registration.setId(42L);
        return registration;
    }
}
