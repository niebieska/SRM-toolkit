package pl.srm.registrationapi.registration.service.management;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.service.submission.RegistrationNotificationService;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class RegistrationStatusService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("ACCEPTED", "REJECTED");

    private final RegistrationRepository repository;
    private final RegistrationNotificationService notificationService;

    public RegistrationStatusService(RegistrationRepository repository,
                                     RegistrationNotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    public Registration updateStatus(String code, StatusUpdateRequest request) {
        Registration registration = repository.findByRegistrationCode(code)
                .orElseThrow(() -> new RegistrationException(
                        "REGISTRATION_NOT_FOUND",
                        "Nie znaleziono zgłoszenia o podanym kodzie."
                ));

        String status = request.status() == null ? "" : request.status().trim();

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new RegistrationException("INVALID_STATUS", "Nieprawidłowy status zgłoszenia.");
        }

        registration.setStatus(status);
        registration.setRejectionReason(
                "REJECTED".equals(status) ? trimToNull(request.rejectionReason()) : null
        );
        registration.setUpdatedAt(LocalDateTime.now());

        Registration saved = repository.save(registration);

        notificationService.sendStatusUpdate(saved);

        return saved;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
