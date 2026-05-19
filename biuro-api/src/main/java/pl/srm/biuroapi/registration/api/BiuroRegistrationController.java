package pl.srm.biuroapi.registration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.RegistrationSummary;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/biuro/registrations")
public class BiuroRegistrationController {

    private final RegistrationApiClient registrationApiClient;

    public BiuroRegistrationController(RegistrationApiClient registrationApiClient) {
        this.registrationApiClient = registrationApiClient;
    }

    @GetMapping
    public List<RegistrationSummary> getRegistrations(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String registrationType,
                                                      @RequestParam(required = false) String turnusCode) {
        return registrationApiClient.fetchRegistrations().stream()
                .filter(registration -> matches(status, registration.status()))
                .filter(registration -> matches(registrationType, registration.registrationType()))
                .filter(registration -> matches(turnusCode, registration.turnusCode()))
                .toList();
    }

    @PatchMapping("/{code}/status")
    public RegistrationSummary updateStatus(@PathVariable String code,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        validateRequest(request);
        return registrationApiClient.updateStatus(code, request);
    }

    private void validateRequest(StatusUpdateRequest request) {
        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPTED".equals(status) && !"REJECTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status musi mieć wartość ACCEPTED albo REJECTED");
        }

        if ("REJECTED".equals(status) && (request.rejectionReason() == null || request.rejectionReason().trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Powód odrzucenia jest wymagany");
        }
    }

    private boolean matches(String filterValue, String value) {
        if (filterValue == null || filterValue.isBlank()) {
            return true;
        }
        return filterValue.trim().equalsIgnoreCase(value);
    }
}
