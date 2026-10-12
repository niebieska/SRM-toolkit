package pl.srm.registrationapi.registration.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

@RestController
public class AcceptedCountsController {
    private final RegistrationRepository repository;
    public AcceptedCountsController(RegistrationRepository repository) { this.repository = repository; }

    // Covered by the existing service-authenticated management endpoint boundary.
    @GetMapping("/api/registrations/accepted-counts")
    public AcceptedCounts counts(@RequestParam String turnusCode) {
        if (turnusCode.isBlank())
            throw new pl.srm.registrationapi.registration.exception.RegistrationException(
                    "INVALID_REQUEST", "Podaj kod turnusu.");
        return new AcceptedCounts(
                repository.countByTurnusCodeAndRegistrationTypeAndStatus(turnusCode, "PARTICIPANT", "ACCEPTED"),
                repository.countByTurnusCodeAndRegistrationTypeAndStatus(turnusCode, "STAFF", "ACCEPTED"));
    }
    public record AcceptedCounts(long participants, long staff) {}
}
