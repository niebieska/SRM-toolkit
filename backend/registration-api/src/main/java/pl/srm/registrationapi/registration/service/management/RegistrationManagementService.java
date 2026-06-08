package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.dto.response.RegistrationDetailResponse;
import pl.srm.registrationapi.registration.dto.response.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import pl.srm.registrationapi.registration.util.PeselHelper;

@Service
public class RegistrationManagementService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACCEPTED", "REJECTED");

    private final RegistrationRepository repository;
    private final ObjectMapper objectMapper;
    private final PeselHelper peselHelper;

    public RegistrationManagementService(RegistrationRepository repository,
                                         ObjectMapper objectMapper,
                                         PeselHelper peselHelper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.peselHelper = peselHelper;
    }

    public RegistrationSummaryResponse getByCode(String code) {
        return toSummary(findByCode(code));
    }

    public RegistrationDetailResponse getDetailByCode(String code) {
        Registration registration = findByCode(code);
        RegistrationSummaryResponse summary = toSummary(registration);
        return new RegistrationDetailResponse(
                summary.registrationCode(),
                summary.registrationType(),
                summary.turnusCode(),
                summary.minor(),
                summary.status(),
                summary.rejectionReason(),
                summary.createdAt(),
                summary.firstName(),
                summary.lastName(),
                summary.age(),
                registration.getPayload()
        );
    }

    public List<RegistrationSummaryResponse> getAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toSummary)
                .toList();
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
        String firstName = null;
        String lastName = null;
        Integer age = null;
        String gender =null;
        try {
            JsonNode root = objectMapper.readTree(registration.getPayload());
            JsonNode person = root.path("person");
            firstName = person.path("firstName").textValue();
            lastName = person.path("lastName").textValue();
            gender = person.path("gender").textValue();
            String pesel = person.path("pesel").textValue();
            if (pesel != null && !pesel.isBlank()) {
                age = peselHelper.calculateAge(pesel);
            }
        } catch (Exception ignored) {
            // leave fields null if payload cannot be parsed
        }
        return new RegistrationSummaryResponse(
                registration.getRegistrationCode(),
                registration.getRegistrationType(),
                registration.getTurnusCode(),
                registration.isMinor(),
                registration.getStatus(),
                registration.getRejectionReason(),
                registration.getCreatedAt(),
                firstName,
                lastName,
                age,
                gender
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public List<RegistrationSummaryResponse> getParticipants() {
        return repository.findByRegistrationType(RegistrationType.PARTICIPANT.name())
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public List<RegistrationSummaryResponse> getStaff() {
        return repository.findByRegistrationType(RegistrationType.STAFF.name())
                .stream()
                .map(this::toSummary)
                .toList();
    }
}
