package pl.srm.registrationapi.registration.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PaymentSettlementResponse(boolean settled, String note, LocalDateTime changedAt, String actorUsername,
                                        List<PaymentSettlementAuditEntryResponse> history) {}
