package pl.srm.biuroapi.registration.model;

import java.math.BigDecimal;
import java.util.List;

public record PaymentRegisterRow(Long registrationId, String registrationCode, String registrationType, String status,
        String turnusCode, String turnusName, String firstName, String lastName, String role, String subrole,
        BigDecimal fee, BigDecimal discount, String discountDescription, String discountChangedAt,
        String discountActor, List<PaymentRecord> payments, boolean settled, String settlementNote,
        String settledAt, String settlementActor, List<PaymentSettlementAuditEntry> settlementHistory) {}
