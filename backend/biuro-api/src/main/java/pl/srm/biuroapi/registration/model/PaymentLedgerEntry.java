package pl.srm.biuroapi.registration.model;

import java.util.List;

public record PaymentLedgerEntry(Long registrationId, String registrationCode,
                                 List<PaymentRecord> payments, DiscountRecord discount, PaymentSettlement settlement) {
    public PaymentLedgerEntry(Long registrationId, String registrationCode, List<PaymentRecord> payments, DiscountRecord discount) {
        this(registrationId, registrationCode, payments, discount, null);
    }
}
