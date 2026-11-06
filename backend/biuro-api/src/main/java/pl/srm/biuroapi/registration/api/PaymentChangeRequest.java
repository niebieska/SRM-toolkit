package pl.srm.biuroapi.registration.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentChangeRequest(@NotNull LocalDate date,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 30) String method, @NotBlank @Size(max = 100) String label,
        @Size(max = 300) String note, @Size(max = 300) String reason) {}
