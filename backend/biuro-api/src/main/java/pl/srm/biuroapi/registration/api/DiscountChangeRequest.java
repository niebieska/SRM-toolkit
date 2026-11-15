package pl.srm.biuroapi.registration.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DiscountChangeRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                                    @Size(max = 300) String description) {}
