package pl.srm.biuroapi.registration.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.registration.api.PaymentChangeRequest;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.DiscountRecord;
import pl.srm.biuroapi.registration.model.PaymentLedgerEntry;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.turnus.StaffFee;
import pl.srm.biuroapi.turnus.TurnusCatalogue;
import pl.srm.biuroapi.turnus.TurnusDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRegisterServiceTest {
    @Test void winterCandidateFeeIsUsedForMatchingStaffRegistration() {
        var registrations = mock(RegistrationApiClient.class);
        var turnuses = mock(TurnusCatalogue.class);
        var service = new PaymentRegisterService(registrations, turnuses);
        var candidate = new RegistrationSummary(1L, "REG-CANDIDATE", "STAFF", "WINTER-1", true,
                "ACCEPTED", null, null, "Anna", "Kandydat", 17, null,
                "animator", "kandydat_na_animatora");
        var turnus = new TurnusDefinition("WINTER-1", "Zima", null, "Miejsce",
                TurnusDefinition.SeasonType.WINTER, 2027, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 8), 14, 50, 10, true, true, true, 0, List.of(),
                BigDecimal.ZERO, List.of(new StaffFee("animator", "kandydat_na_animatora",
                        new BigDecimal("750.00"))), true);

        when(registrations.fetchRegistrations()).thenReturn(List.of(candidate));
        when(registrations.fetchPaymentLedger()).thenReturn(List.of(
                new PaymentLedgerEntry(1L, "REG-CANDIDATE", List.of(), null)));
        when(turnuses.list()).thenReturn(List.of(turnus));

        var rows = service.list();

        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("750.00"), rows.get(0).fee());
    }

    @Test void summerCandidatePaymentUsesSharedFeeWhenOnlyLegacyMinorVariantIsStored() {
        var registrations = mock(RegistrationApiClient.class);
        var turnuses = mock(TurnusCatalogue.class);
        var service = new PaymentRegisterService(registrations, turnuses);
        var minorCandidate = new RegistrationSummary(1L, "REG-MINOR", "STAFF", "SUMMER-1", true,
                "ACCEPTED", null, null, "Anna", "Kandydat", 17, null,
                "kandydat_na_animatora", null);
        var adultCandidate = new RegistrationSummary(2L, "REG-ADULT", "STAFF", "SUMMER-1", false,
                "ACCEPTED", null, null, "Jan", "Kandydat", 22, null,
                "animator", "kandydat_na_animatora");
        var turnus = new TurnusDefinition("SUMMER-1", "Lato", null, "Miejsce",
                TurnusDefinition.SeasonType.SUMMER, 2027, LocalDate.of(2027, 7, 1),
                LocalDate.of(2027, 7, 8), 14, 50, 10, true, true, true, 0, List.of(),
                BigDecimal.ZERO, List.of(new StaffFee("kandydat_na_animatora", null,
                        new BigDecimal("750.00"))), true);

        when(registrations.fetchRegistrations()).thenReturn(List.of(minorCandidate, adultCandidate));
        when(registrations.fetchPaymentLedger()).thenReturn(List.of(
                new PaymentLedgerEntry(1L, "REG-MINOR", List.of(), null),
                new PaymentLedgerEntry(2L, "REG-ADULT", List.of(), null)));
        when(turnuses.list()).thenReturn(List.of(turnus));

        var rows = service.list();

        assertEquals(2, rows.size());
        assertEquals(new BigDecimal("750.00"), rows.get(0).fee());
        assertEquals(new BigDecimal("750.00"), rows.get(1).fee());
    }

    @Test void acceptedRefundUsesCurrentFeeAfterCurrentDiscount() {
        var registrations = mock(RegistrationApiClient.class);
        var turnuses = mock(TurnusCatalogue.class);
        var service = new PaymentRegisterService(registrations, turnuses);
        var accepted = new RegistrationSummary(1L, "REG-1", "PARTICIPANT", "T1", true,
                "ACCEPTED", null, null, "Anna", "Nowak", 15, null, null, null);
        var turnus = participantFeeTurnus(new BigDecimal("1000.00"));
        var ledger = new PaymentLedgerEntry(1L, "REG-1", List.of(),
                new DiscountRecord(new BigDecimal("200.00"), "Rabat", null, "operator"));
        var request = new PaymentChangeRequest(LocalDate.of(2026, 11, 3), new BigDecimal("300.00"),
                "Przelew", "Zwrot wpłaty", null, null);
        var actor = new AccountPrincipal("actor-id", "operator", "Operator", false, Set.of(AccountRole.OPERATOR), "session");

        when(registrations.fetchRegistrations()).thenReturn(List.of(accepted));
        when(registrations.fetchPaymentLedger()).thenReturn(List.of(ledger));
        when(turnuses.get("T1")).thenReturn(turnus);

        service.refund("REG-1", request, "refund-key", actor);

        verify(registrations).addRefund("REG-1", request, "refund-key", "ACCEPTED",
                new BigDecimal("800.00"), "actor-id", "operator");
    }

    @Test void editingAcceptedRefundAlsoUsesCurrentFeeAfterDiscount() {
        var registrations = mock(RegistrationApiClient.class);
        var turnuses = mock(TurnusCatalogue.class);
        var service = new PaymentRegisterService(registrations, turnuses);
        var accepted = new RegistrationSummary(1L, "REG-1", "PARTICIPANT", "T1", true,
                "ACCEPTED", null, null, "Anna", "Nowak", 15, null, null, null);
        var turnus = participantFeeTurnus(new BigDecimal("1000.00"));
        var ledger = new PaymentLedgerEntry(1L, "REG-1", List.of(),
                new DiscountRecord(new BigDecimal("300.00"), "Zmieniony rabat", null, "operator"));
        var request = new PaymentChangeRequest(LocalDate.of(2026, 11, 4), new BigDecimal("500.00"),
                "Przelew", "Zwrot wpłaty", null, "Korekta");
        var actor = new AccountPrincipal("actor-id", "operator", "Operator", false, Set.of(AccountRole.OPERATOR), "session");

        when(registrations.fetchRegistrations()).thenReturn(List.of(accepted));
        when(registrations.fetchPaymentLedger()).thenReturn(List.of(ledger));
        when(turnuses.get("T1")).thenReturn(turnus);

        service.edit("REG-1", 7L, request, actor);

        verify(registrations).editPayment("REG-1", 7L, request, "ACCEPTED",
                new BigDecimal("700.00"), "actor-id", "operator");
    }

    private TurnusDefinition participantFeeTurnus(BigDecimal fee) {
        return new TurnusDefinition("T1", "Turnus", null, "Miejsce",
                TurnusDefinition.SeasonType.WINTER, 2027, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 8), 14, 50, 10, true, true, true, 0, List.of(),
                fee, List.of(), true);
    }
}
