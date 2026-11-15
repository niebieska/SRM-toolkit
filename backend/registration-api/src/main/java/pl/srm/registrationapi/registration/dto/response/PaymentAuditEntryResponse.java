package pl.srm.registrationapi.registration.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentAuditEntryResponse(
        LocalDateTime changedAt, String actorUsername, String reason,
        LocalDate previousDate, BigDecimal previousAmount, String previousMethod,
        String previousLabel, String previousNote,
        LocalDate newDate, BigDecimal newAmount, String newMethod,
        String newLabel, String newNote
) {}
