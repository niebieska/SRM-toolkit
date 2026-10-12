package pl.srm.biuroapi.turnus;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

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
        @PositiveOrZero Long version) {
    @AssertTrue(message = "Data końca nie może poprzedzać daty początku.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
