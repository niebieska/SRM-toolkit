package pl.srm.biuroapi.registration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.RegistrationDetail;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.TurnusRegistrationStats;
import pl.srm.biuroapi.registration.service.RegistrationStatisticsService;
import pl.srm.biuroapi.turnus.TurnusCatalogue;
import pl.srm.biuroapi.turnus.TurnusDefinition;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/biuro/registrations")
public class BiuroRegistrationController {

    private final RegistrationApiClient registrationApiClient;
    private final RegistrationStatisticsService statisticsService;
    private final TurnusCatalogue turnusCatalogue;

    public BiuroRegistrationController(RegistrationApiClient registrationApiClient,
                                       RegistrationStatisticsService statisticsService,
                                       TurnusCatalogue turnusCatalogue) {
        this.registrationApiClient = registrationApiClient;
        this.statisticsService = statisticsService;
        this.turnusCatalogue = turnusCatalogue;
    }

    @GetMapping
    public List<RegistrationSummary> getRegistrations(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String registrationType,
                                                      @RequestParam(required = false) String turnusCode) {
        Set<String> activeTurnusCodes = turnusCatalogue.list().stream()
                .filter(TurnusDefinition::active)
                .map(TurnusDefinition::turnusCode)
                .collect(Collectors.toSet());

        return registrationApiClient.fetchRegistrations().stream()
                .filter(registration -> activeTurnusCodes.contains(registration.turnusCode()))
                .filter(registration -> matches(status, registration.status()))
                .filter(registration -> matches(registrationType, registration.registrationType()))
                .filter(registration -> matches(turnusCode, registration.turnusCode()))
                .toList();
    }

    @GetMapping("/stats/{turnusCode}")
    public TurnusRegistrationStats getStatsByTurnus(@PathVariable String turnusCode) {
        return statisticsService.calculateForTurnus(
                turnusCode,
                registrationApiClient.fetchRegistrations()
        );
    }

    @GetMapping("/{code}")
    public RegistrationDetail getRegistration(@PathVariable String code) {
        return registrationApiClient.fetchRegistration(code);
    }

    @PatchMapping("/{code}/status")
    public RegistrationSummary updateStatus(@PathVariable String code,
                                            @Valid @RequestBody StatusUpdateRequest request,
                                            @AuthenticationPrincipal AccountPrincipal actor) {
        validateRequest(request);
        if ("ACCEPTED".equalsIgnoreCase(request.status().trim())) {
            RegistrationDetail registration = registrationApiClient.fetchRegistration(code);
            TurnusDefinition turnus = turnusCatalogue.get(registration.turnusCode());
            if (!"ACCEPTED".equalsIgnoreCase(registration.status())) {
                RegistrationApiClient.AcceptedCounts accepted = registrationApiClient.fetchAcceptedCounts(registration.turnusCode());
                long current = "STAFF".equalsIgnoreCase(registration.registrationType())
                        ? accepted.staff() : accepted.participants();
                int capacity = "STAFF".equalsIgnoreCase(registration.registrationType())
                        ? turnus.staffCapacity() : turnus.participantCapacity();
                requireAvailablePlace(registration.registrationType(), current, capacity, true);
            }
        }
        return registrationApiClient.updateStatus(code, request, actor.id(), actor.username());
    }

    @PatchMapping("/{code}/data")
    public RegistrationDetail updateRegistrationData(@PathVariable String code,
                                                     @Valid @RequestBody RegistrationEditRequest request,
                                                     @AuthenticationPrincipal AccountPrincipal actor) {
        return registrationApiClient.updateRegistrationData(code, request, actor.id(), actor.username());
    }

    @PostMapping("/staff/manual")
    public RegistrationApiClient.RegistrationCreated createManualStaffRegistration(
            @RequestBody JsonNode payload,
            @AuthenticationPrincipal AccountPrincipal actor) {
        String turnusCode = payload.path("turnusCode").asText("");
        TurnusDefinition turnus = turnusCatalogue.get(turnusCode);
        RegistrationApiClient.AcceptedCounts accepted = registrationApiClient.fetchAcceptedCounts(turnusCode);
        requireAvailablePlace("STAFF", accepted.staff(), turnus.staffCapacity(), false);
        return registrationApiClient.createManualStaffRegistration(payload.toString(), actor.id(), actor.username());
    }



    private void requireAvailablePlace(String registrationType, long accepted, int capacity,
                                       boolean acceptance) {
        if (accepted < capacity) return;
        if (acceptance) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Turnus zapełniony.");
        }
        String label = "STAFF".equalsIgnoreCase(registrationType) ? "kadry" : "uczestników";
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Brak wolnych miejsc dla " + label + " (" + accepted + "/" + capacity
                        + "). Nie można dodać kolejnej osoby do turnusu.");
    }

    private void validateRequest(StatusUpdateRequest request) {
        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPTED".equals(status) && !"WAITLIST".equals(status) && !"REJECTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status musi mieć wartość ACCEPTED, WAITLIST albo REJECTED");
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
