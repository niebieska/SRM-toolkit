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
import pl.srm.biuroapi.registration.service.PaymentRegisterService;
import pl.srm.biuroapi.registration.model.PaymentLedgerEntry;
import pl.srm.biuroapi.registration.model.PaymentRegisterRow;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/biuro/registrations")
public class BiuroRegistrationController {

    private final RegistrationApiClient registrationApiClient;
    private final RegistrationStatisticsService statisticsService;
    private final PaymentRegisterService paymentRegisterService;

    public BiuroRegistrationController(RegistrationApiClient registrationApiClient,
                                       RegistrationStatisticsService statisticsService,
                                       PaymentRegisterService paymentRegisterService) {
        this.registrationApiClient = registrationApiClient;
        this.statisticsService = statisticsService;
        this.paymentRegisterService = paymentRegisterService;
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

    @GetMapping("/payments")
    public List<PaymentRegisterRow> getPayments() { return paymentRegisterService.list(); }

    @PostMapping("/{code}/payments")
    public PaymentLedgerEntry addPayment(@PathVariable String code,
            @Valid @RequestBody PaymentChangeRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return paymentRegisterService.add(code, request, idempotencyKey, actor);
    }

    @PutMapping("/{code}/payments/{paymentId}")
    public PaymentLedgerEntry editPayment(@PathVariable String code, @PathVariable long paymentId,
            @Valid @RequestBody PaymentChangeRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return paymentRegisterService.edit(code, paymentId, request, actor);
    }

    @PutMapping("/{code}/discount")
    public PaymentLedgerEntry setDiscount(@PathVariable String code,
            @Valid @RequestBody DiscountChangeRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return paymentRegisterService.discount(code, request, actor);
    }

    @PatchMapping("/{code}/status")
    public RegistrationSummary updateStatus(@PathVariable String code,
                                            @Valid @RequestBody StatusUpdateRequest request,
                                            @AuthenticationPrincipal AccountPrincipal actor) {
        validateRequest(request);
        return registrationApiClient.updateStatus(code, request, actor.id(), actor.username());
    }

    @PatchMapping("/{code}/data")
    public RegistrationDetail updateRegistrationData(@PathVariable String code,
                                                     @Valid @RequestBody RegistrationEditRequest request,
                                                     @AuthenticationPrincipal AccountPrincipal actor) {
        return registrationApiClient.updateRegistrationData(code, request, actor.id(), actor.username());
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
