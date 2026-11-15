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
        BigDecimal fee = requireEligibleFee(code, false);
        return registrations.addPayment(code, request, idempotencyKey, fee, actor.id(), actor.username());
    }

    public PaymentLedgerEntry refund(String code, PaymentChangeRequest request, String idempotencyKey, AccountPrincipal actor) {
        RegistrationSummary summary = findRegistration(code);
        BigDecimal fee = requireEligibleFee(code, false);
        return registrations.addRefund(code, request, idempotencyKey, summary.status(), fee,
                actor.id(), actor.username());
    }

    public PaymentLedgerEntry settle(String code, String note, AccountPrincipal actor) {
        return registrations.settlePayment(code, note, actor.id(), actor.username());
    }

    public PaymentLedgerEntry edit(String code, long paymentId, PaymentChangeRequest request, AccountPrincipal actor) {
        RegistrationSummary summary = findRegistration(code);
        if (!"ACCEPTED".equals(summary.status()) && !"REJECTED".equals(summary.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Korekty wpłat są dostępne dla zaakceptowanych lub odrzuconych zgłoszeń.");
        BigDecimal fee = requireEligibleFee(code, false);
        return registrations.editPayment(code, paymentId, request, summary.status(), fee,
                actor.id(), actor.username());
    }

    public PaymentLedgerEntry discount(String code, DiscountChangeRequest request, AccountPrincipal actor) {
        BigDecimal fee = requireEligibleFee(code);
        return registrations.setDiscount(code, request, fee, actor.id(), actor.username());
    }

    private BigDecimal requireEligibleFee(String code) {
        return requireEligibleFee(code, true);
    }

    private BigDecimal requireEligibleFee(String code, boolean requireAccepted) {
        RegistrationSummary summary = findRegistration(code);
        if (requireAccepted && !"ACCEPTED".equals(summary.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wpłaty są dostępne tylko dla zaakceptowanych zgłoszeń.");
        return feeFor(summary);
    }

    private RegistrationSummary findRegistration(String code) {
        return registrations.fetchRegistrations().stream()
                .filter(item -> item.registrationCode().equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia."));
    }

    private BigDecimal feeFor(RegistrationSummary summary) {
        var turnus = turnuses.get(summary.turnusCode());
        BigDecimal fee;
        if ("PARTICIPANT".equals(summary.registrationType())) {
            fee = turnus.participantFee();
        } else if ("STAFF".equals(summary.registrationType())) {
            fee = staffFee(turnus, summary.role(), summary.subrole());
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
        else if ("STAFF".equals(summary.registrationType())) fee = staffFee(turnus, summary.role(), summary.subrole());
        else return null;
        if (fee == null || fee.signum() <= 0) return null;
        boolean accepted = "ACCEPTED".equals(summary.status());
        boolean rejectedWithPaymentHistory = "REJECTED".equals(summary.status()) && !ledger.payments().isEmpty();
        if (!accepted && !rejectedWithPaymentHistory) return null;
        var discount = ledger.discount();
        return new PaymentRegisterRow(summary.registrationId(), summary.registrationCode(),
                summary.registrationType(), summary.status(), summary.turnusCode(), turnus.turnusName(), summary.firstName(), summary.lastName(),
                summary.role(), summary.subrole(), fee, discount == null ? BigDecimal.ZERO : discount.amount(),
                discount == null ? null : discount.description(), discount == null ? null : discount.changedAt().toString(),
                discount == null ? null : discount.actorUsername(), ledger.payments(),
                ledger.settlement() != null && ledger.settlement().settled(),
                ledger.settlement() == null ? null : ledger.settlement().note(),
                ledger.settlement() == null || ledger.settlement().changedAt() == null ? null : ledger.settlement().changedAt().toString(),
                ledger.settlement() == null ? null : ledger.settlement().actorUsername(),
                ledger.settlement() == null ? java.util.List.of() : ledger.settlement().history());
    }

    private BigDecimal staffFee(TurnusDefinition turnus, String role, String subrole) {
        var exactOrRoleFee = turnus.staffFees().stream()
                .filter(item -> item.role().equals(role))
                .filter(item -> item.subrole() == null || Objects.equals(item.subrole(), subrole))
                .map(StaffFee::amount).findFirst();
        if (exactOrRoleFee.isPresent()) return exactOrRoleFee.get();
        if (turnus.seasonType() != TurnusDefinition.SeasonType.SUMMER) return BigDecimal.ZERO;

        boolean minorCandidate = "kandydat_na_animatora".equals(role) && subrole == null;
        boolean adultCandidate = "animator".equals(role) && "kandydat_na_animatora".equals(subrole);
        if (!minorCandidate && !adultCandidate) return BigDecimal.ZERO;
        String alternateRole = minorCandidate ? "animator" : "kandydat_na_animatora";
        String alternateSubrole = minorCandidate ? "kandydat_na_animatora" : null;
        return turnus.staffFees().stream()
                .filter(item -> item.role().equals(alternateRole))
                .filter(item -> Objects.equals(item.subrole(), alternateSubrole))
                .map(StaffFee::amount).findFirst().orElse(BigDecimal.ZERO);
    }
}
