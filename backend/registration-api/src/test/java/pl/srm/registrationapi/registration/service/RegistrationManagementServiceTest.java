package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.dto.response.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import org.springframework.data.domain.Sort;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.util.PeselHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationManagementServiceTest {

    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final RegistrationManagementService service = new RegistrationManagementService(
            repository, new ObjectMapper(), new PeselHelper());

    @Test
    void returnsAllRegistrationsOrderedByCreatedAtDesc() {
        Registration newer = new Registration(
                "REG-S-ZAGLE26T1-2",
                "STAFF",
                "ZAGLE26T1",
                "hash2",
                false,
                "NEW",
                null,
                "{}",
                LocalDateTime.of(2026, 5, 2, 10, 0),
                null
        );
        Registration older = new Registration(
                "REG-P-ZAGLE26T1-1",
                "PARTICIPANT",
                "ZAGLE26T1",
                "hash1",
                true,
                "NEW",
                null,
                "{}",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null
        );
        when(repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(newer, older));

        List<RegistrationSummaryResponse> registrations = service.getAll();

        assertEquals(2, registrations.size());
        assertEquals("REG-S-ZAGLE26T1-2", registrations.getFirst().registrationCode());
        assertEquals("REG-P-ZAGLE26T1-1", registrations.get(1).registrationCode());
    }

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
