package pl.srm.registrationapi.registration.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DiscountRecordRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @Size(max = 300) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal feeAmount,
        @NotNull @Size(min = 1, max = 36) String actorAccountId,
        @NotNull @Size(min = 1, max = 64) String actorUsername
) {}
