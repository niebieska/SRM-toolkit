package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.dto.response.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.model.RegistrationStatusHistory;
import pl.srm.registrationapi.registration.repository.RegistrationStatusHistoryRepository;
import pl.srm.registrationapi.registration.repository.RegistrationEditHistoryRepository;
import org.springframework.data.domain.Sort;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.management.RegistrationStatusService;
import pl.srm.registrationapi.registration.service.management.StatusNotificationService;
import pl.srm.registrationapi.registration.util.PeselHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationManagementServiceTest {

    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final RegistrationStatusHistoryRepository historyRepository = mock(RegistrationStatusHistoryRepository.class);
    private final RegistrationEditHistoryRepository editHistoryRepository = mock(RegistrationEditHistoryRepository.class);
    private final RegistrationStatusService statusService = mock(RegistrationStatusService.class);
    private final StatusNotificationService notificationService = mock(StatusNotificationService.class);

    private final RegistrationManagementService service = new RegistrationManagementService(
            repository,
            historyRepository,
            editHistoryRepository,
            new ObjectMapper(),
            new PeselHelper(),
            statusService,
            notificationService
    );
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
    void delegatesStatusUpdateToStatusService() {
        Registration registration = new Registration(
                "REG-P-ZAGLE26T1-1",
                "PARTICIPANT",
                "ZAGLE26T1",
                "hash",
                true,
                "REJECTED",
                "Brak miejsc",
                "{}",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                LocalDateTime.now()
        );

        StatusUpdateRequest request = new StatusUpdateRequest("REJECTED", "Brak miejsc", "actor-id", "operator");

        when(statusService.updateStatus("REG-P-ZAGLE26T1-1", request))
                .thenReturn(registration);

        RegistrationSummaryResponse response = service.updateStatus(
                "REG-P-ZAGLE26T1-1",
                request
        );

        assertEquals("REJECTED", response.status());
        assertEquals("Brak miejsc", response.rejectionReason());

        var order = inOrder(statusService, notificationService);
        order.verify(statusService).updateStatus("REG-P-ZAGLE26T1-1", request);
        order.verify(notificationService).sendStatusUpdate(registration);
        verify(repository, never()).save(any());
    }
    @Test
    void rejectsUnknownStatus() {
        StatusUpdateRequest request = new StatusUpdateRequest("PENDING", null, "actor-id", "operator");

        when(statusService.updateStatus("REG-P-ZAGLE26T1-1", request))
                .thenThrow(new RegistrationException(
                        "INVALID_STATUS",
                        "Nieprawidłowy status zgłoszenia."
                ));

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> service.updateStatus("REG-P-ZAGLE26T1-1", request)
        );

        assertEquals("INVALID_STATUS", exception.getCode());
        verify(statusService).updateStatus("REG-P-ZAGLE26T1-1", request);
    }

    @Test
    void includesStatusHistoryInRegistrationDetailResponse() {
        Registration registration = new Registration("REG-P-T1-1", "PARTICIPANT", "T1", "hash", false,
                "ACCEPTED", null, "{}", LocalDateTime.of(2027, 1, 1, 10, 0), null);
        registration.setId(42L);
        var entry = new RegistrationStatusHistory(42L, "NEW", "ACCEPTED",
                LocalDateTime.of(2027, 1, 2, 10, 0), "actor-id", "operator", null);
        when(repository.findByRegistrationCode("REG-P-T1-1")).thenReturn(Optional.of(registration));
        when(historyRepository.findByRegistrationIdOrderByChangedAtDescIdDesc(42L)).thenReturn(List.of(entry));

        var detail = service.getDetailByCode("REG-P-T1-1");

        assertEquals(1, detail.statusHistory().size());
        assertEquals("NEW", detail.statusHistory().getFirst().previousStatus());
        assertEquals("ACCEPTED", detail.statusHistory().getFirst().newStatus());
        assertEquals("operator", detail.statusHistory().getFirst().actorUsername());
        verify(historyRepository).findByRegistrationIdOrderByChangedAtDescIdDesc(42L);
    }
    @Test
    void staffSummariesExposeNormalizedRoleAndFunctionWithoutChangingParticipantSummaries() {
        var staff = new Registration("S1", "STAFF", "T1", "hash", false, "ACCEPTED", null,
                """
                {"role":" animator ","subrole":" kandydat_na_animatora ","person":{"firstName":"Anna"}}
                """,
                LocalDateTime.of(2027, 1, 1, 12, 0), null);
        var participant = new Registration("P1", "PARTICIPANT", "T1", "other", false, "ACCEPTED", null,
                "{\"role\":\"animator\",\"subrole\":\"animator\"}", LocalDateTime.of(2027, 1, 1, 12, 0), null);
        when(repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(staff, participant));
        var summaries = service.getAll();
        assertEquals("animator", summaries.getFirst().role());
        assertEquals("kandydat_na_animatora", summaries.getFirst().subrole());
        assertEquals("Anna", summaries.getFirst().firstName());
        assertNull(summaries.get(1).role());
        assertNull(summaries.get(1).subrole());
    }
    @Test
    void emptyStaffFunctionBecomesNullAndMissingLegacyRoleDoesNotBreakList() {
        var staff = new Registration("S1", "STAFF", "T1", "hash", false, "ACCEPTED", null,
                "{\"role\":\"ksiadz\",\"subrole\":\"  \"}", LocalDateTime.of(2027, 1, 1, 12, 0), null);
        var missing = new Registration("S2", "STAFF", "T1", "other", false, "ACCEPTED", null,
                "{}", LocalDateTime.of(2027, 1, 1, 12, 0), null);
        when(repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(staff, missing));
        var summaries = service.getAll();
        assertEquals("ksiadz", summaries.getFirst().role());
        assertNull(summaries.getFirst().subrole());
        assertNull(summaries.get(1).role());
    }
}
