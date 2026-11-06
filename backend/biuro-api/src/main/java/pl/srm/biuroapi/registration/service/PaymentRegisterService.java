package pl.srm.biuroapi.registration.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.api.DiscountChangeRequest;
import pl.srm.biuroapi.registration.api.PaymentChangeRequest;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.PaymentLedgerEntry;
import pl.srm.biuroapi.registration.model.PaymentRegisterRow;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.turnus.TurnusCatalogue;
import pl.srm.biuroapi.turnus.TurnusDefinition;
import pl.srm.biuroapi.turnus.StaffFee;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentRegisterService {
    private final RegistrationApiClient registrations;
    private final TurnusCatalogue turnuses;

    public PaymentRegisterService(RegistrationApiClient registrations, TurnusCatalogue turnuses) {
        this.registrations = registrations;
        this.turnuses = turnuses;
    }

    public java.util.List<PaymentRegisterRow> list() {
        var summaryById = registrations.fetchRegistrations().stream()
                .filter(summary -> summary.registrationId() != null)
                .collect(Collectors.toMap(RegistrationSummary::registrationId, Function.identity(), (first, ignored) -> first));
        var turnusByCode = turnuses.list().stream()
                .collect(Collectors.toMap(TurnusDefinition::turnusCode, Function.identity()));
        return registrations.fetchPaymentLedger().stream()
                .map(entry -> row(entry, summaryById.get(entry.registrationId()), turnusByCode))
                .filter(Objects::nonNull)
                .toList();
    }

    public PaymentLedgerEntry add(String code, PaymentChangeRequest request, String idempotencyKey, AccountPrincipal actor) {
        requireEligibleFee(code, false);
        return registrations.addPayment(code, request, idempotencyKey, actor.id(), actor.username());
    }

    public PaymentLedgerEntry edit(String code, long paymentId, PaymentChangeRequest request, AccountPrincipal actor) {
        requireEligibleFee(code, false);
        return registrations.editPayment(code, paymentId, request, actor.id(), actor.username());
    }

    public PaymentLedgerEntry discount(String code, DiscountChangeRequest request, AccountPrincipal actor) {
        BigDecimal fee = requireEligibleFee(code);
        return registrations.setDiscount(code, request, fee, actor.id(), actor.username());
    }

    private BigDecimal requireEligibleFee(String code) {
        return requireEligibleFee(code, true);
    }

    private BigDecimal requireEligibleFee(String code, boolean requireAccepted) {
        RegistrationSummary summary = registrations.fetchRegistrations().stream()
                .filter(item -> item.registrationCode().equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia."));
        if (requireAccepted && !"ACCEPTED".equals(summary.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wpłaty są dostępne tylko dla zaakceptowanych zgłoszeń.");
        var turnus = turnuses.get(summary.turnusCode());
        BigDecimal fee;
        if ("PARTICIPANT".equals(summary.registrationType())) {
            fee = turnus.participantFee();
        } else if ("STAFF".equals(summary.registrationType())) {
            fee = turnus.staffFees().stream()
                    .filter(item -> item.role().equals(summary.role()))
                    .filter(item -> item.subrole() == null || Objects.equals(item.subrole(), summary.subrole()))
                    .map(StaffFee::amount).findFirst().orElse(BigDecimal.ZERO);
        } else {
            fee = BigDecimal.ZERO;
        }
        if (fee == null || fee.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To zgłoszenie nie ma przypisanej opłaty.");
        return fee;
    }

    private PaymentRegisterRow row(PaymentLedgerEntry ledger, RegistrationSummary summary,
                                   Map<String, TurnusDefinition> turnusByCode) {
        if (summary == null) return null;
        TurnusDefinition turnus = turnusByCode.get(summary.turnusCode());
        if (turnus == null) return null;
        BigDecimal fee;
        if ("PARTICIPANT".equals(summary.registrationType())) fee = turnus.participantFee();
        else if ("STAFF".equals(summary.registrationType())) fee = turnus.staffFees().stream()
                .filter(item -> item.role().equals(summary.role()))
                .filter(item -> item.subrole() == null || Objects.equals(item.subrole(), summary.subrole()))
                .map(StaffFee::amount).findFirst().orElse(BigDecimal.ZERO);
        else return null;
        if (fee == null || fee.signum() <= 0) return null;
        if (!"ACCEPTED".equals(summary.status()) && ledger.payments().isEmpty() && ledger.discount() == null) return null;
        var discount = ledger.discount();
        return new PaymentRegisterRow(summary.registrationId(), summary.registrationCode(),
                summary.registrationType(), summary.status(), summary.turnusCode(), turnus.turnusName(), summary.firstName(), summary.lastName(),
                summary.role(), summary.subrole(), fee, discount == null ? BigDecimal.ZERO : discount.amount(),
                discount == null ? null : discount.description(), discount == null ? null : discount.changedAt().toString(),
                discount == null ? null : discount.actorUsername(), ledger.payments());
    }
}
