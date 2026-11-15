package pl.srm.biuroapi.registration.model;

import java.time.LocalDateTime;

public record PaymentSettlementAuditEntry(boolean settled, String note, LocalDateTime changedAt,
                                          String actorUsername) {}
