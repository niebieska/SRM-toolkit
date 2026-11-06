package pl.srm.registrationapi.registration.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentRecordResponse(
        Long id, LocalDate date, BigDecimal amount, String method, String label, String note,
        LocalDateTime createdAt, String actorUsername, List<PaymentAuditEntryResponse> history
) {}
