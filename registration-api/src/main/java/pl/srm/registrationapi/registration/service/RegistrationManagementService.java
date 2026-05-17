package pl.srm.registrationapi.registration.service;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.api.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.api.StatusUpdateRequest;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class RegistrationManagementService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACCEPTED", "REJECTED");

    private final RegistrationRepository repository;

    public RegistrationManagementService(RegistrationRepository repository) {
        this.repository = repository;
    }

    public RegistrationSummaryResponse getByCode(String code) {
        return toSummary(findByCode(code));
    }

    public RegistrationSummaryResponse updateStatus(String code, StatusUpdateRequest request) {
        Registration registration = findByCode(code);
        String status = request.status() == null ? "" : request.status().trim();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new RegistrationException("INVALID_STATUS", "Nieprawidłowy status zgłoszenia.");
        }

        registration.setStatus(status);
        registration.setRejectionReason("REJECTED".equals(status) ? trimToNull(request.rejectionReason()) : null);
        registration.setUpdatedAt(LocalDateTime.now());
        return toSummary(repository.save(registration));
    }

    private Registration findByCode(String code) {
        return repository.findByRegistrationCode(code)
                .orElseThrow(() -> new RegistrationException("REGISTRATION_NOT_FOUND", "Nie znaleziono zgłoszenia o podanym kodzie."));
    }

    private RegistrationSummaryResponse toSummary(Registration registration) {
        return new RegistrationSummaryResponse(
                registration.getRegistrationCode(),
                registration.getRegistrationType(),
                registration.getTurnusCode(),
                registration.isMinor(),
                registration.getStatus(),
                registration.getRejectionReason(),
                registration.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
