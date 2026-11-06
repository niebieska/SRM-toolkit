package pl.srm.biuroapi.turnus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

public record TurnusRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}") String turnusCode,
        @NotBlank @Size(max = 200) String turnusName,
        @Size(max = 4000) String turnusDescription,
        @NotBlank @Size(max = 500) String turnusLocation,
        @NotNull TurnusDefinition.SeasonType seasonType,
        @NotNull @Min(2000) @Max(9999) Integer seasonYear,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate,
        @NotNull @Min(0) @Max(120) Integer minAge,
        @NotNull @Min(1) Integer participantCapacity,
        @NotNull @Min(1) Integer staffCapacity,
        @NotNull Boolean active, @NotNull Boolean participantRegistrationOpen,
        @NotNull Boolean staffRegistrationOpen,
        @PositiveOrZero Long version, List<@NotNull @Valid StaffRequirement> staffRequirements,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal participantFee,
        List<@NotNull @Valid StaffFee> staffFees) {
    public TurnusRequest(String turnusCode, String turnusName, String turnusDescription, String turnusLocation,
            TurnusDefinition.SeasonType seasonType, Integer seasonYear, LocalDate startDate, LocalDate endDate,
            Integer minAge, Integer participantCapacity, Integer staffCapacity, Boolean active,
            Boolean participantRegistrationOpen, Boolean staffRegistrationOpen, Long version,
            List<StaffRequirement> staffRequirements) {
        this(turnusCode, turnusName, turnusDescription, turnusLocation, seasonType, seasonYear, startDate, endDate,
                minAge, participantCapacity, staffCapacity, active, participantRegistrationOpen,
                staffRegistrationOpen, version, staffRequirements, null, null);
    }
    @AssertTrue(message = "Data końca nie może poprzedzać daty początku.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
