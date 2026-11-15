package pl.srm.biuroapi.registration.model;

import java.time.LocalDateTime;
import java.util.List;

public record PaymentSettlement(boolean settled, String note, LocalDateTime changedAt, String actorUsername,
                                List<PaymentSettlementAuditEntry> history) {}
