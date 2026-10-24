package pl.srm.registrationapi.registration.service.management;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationStatus;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.model.RegistrationStatusHistory;
import pl.srm.registrationapi.registration.repository.RegistrationStatusHistoryRepository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Service
public class RegistrationStatusService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of(
                    RegistrationStatus.ACCEPTED.name(),
                    RegistrationStatus.WAITLIST.name(),
                    RegistrationStatus.REJECTED.name()
            );

    private final RegistrationRepository repository;
    private final RegistrationStatusHistoryRepository historyRepository;
    private final StatusNotificationService notificationService;

    public RegistrationStatusService(RegistrationRepository repository,
                                     RegistrationStatusHistoryRepository historyRepository,
                                     StatusNotificationService notificationService) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Registration updateStatus(String code, StatusUpdateRequest request) {
        Registration registration = repository.findByRegistrationCode(code)
                .orElseThrow(() -> new RegistrationException(
                        "REGISTRATION_NOT_FOUND",
                        "Nie znaleziono zgłoszenia o podanym kodzie."
                ));

        String status = request.status() == null ? "" : request.status().trim().toUpperCase();

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new RegistrationException("INVALID_STATUS", "Nieprawidłowy status zgłoszenia.");
        }

        String previousStatus = registration.getStatus();
        registration.setStatus(status);
        registration.setRejectionReason(
                "REJECTED".equals(status) ? trimToNull(request.rejectionReason()) : null
        );
        registration.setUpdatedAt(LocalDateTime.now());

        Registration saved = repository.save(registration);

        if (!Objects.equals(previousStatus, status)) {
            historyRepository.save(new RegistrationStatusHistory(
                    saved.getId(), previousStatus, status, saved.getUpdatedAt(),
                    request.actorAccountId(), request.actorUsername(), saved.getRejectionReason()
            ));
        }

        notificationService.sendStatusUpdate(saved);

        return saved;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
