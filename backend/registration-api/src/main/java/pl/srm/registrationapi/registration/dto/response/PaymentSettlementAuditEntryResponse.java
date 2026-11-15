package pl.srm.registrationapi.registration.dto.response;

import java.time.LocalDateTime;

public record PaymentSettlementAuditEntryResponse(boolean settled, String note, LocalDateTime changedAt,
                                                   String actorUsername) {}
