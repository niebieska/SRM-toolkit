package pl.srm.biuroapi.turnus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** A fixed fee for an exact seasonal staff role and optional subrole. */
public record StaffFee(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,49}") String role,
        @Pattern(regexp = "[a-z][a-z0-9_]{0,49}") String subrole,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {
    public record Selection(String role, String subrole) {}
    public Selection selection() { return new Selection(role, subrole); }
}
