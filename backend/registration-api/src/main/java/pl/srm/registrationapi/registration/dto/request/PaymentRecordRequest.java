package pl.srm.registrationapi.registration.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRecordRequest(
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 30) String method,
        @NotBlank @Size(max = 100) String label,
        @Size(max = 300) String note,
        @NotBlank @Size(max = 36) String actorAccountId,
        @NotBlank @Size(max = 64) String actorUsername,
        @Size(max = 300) String correctionReason
) {}
