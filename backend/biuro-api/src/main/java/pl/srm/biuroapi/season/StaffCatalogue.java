package pl.srm.biuroapi.season;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record StaffCatalogue(
        @NotBlank @Pattern(regexp = "SUMMER|WINTER") String seasonType,
        @NotNull @Size(min = 1) List<@NotNull @Valid Role> roles,
        @NotNull Map<@NotBlank String, @NotNull List<@NotNull @Valid Certificate>> certificatesByRole) {
    public StaffCatalogue {
        if (roles != null) roles = List.copyOf(roles);
        if (certificatesByRole != null) certificatesByRole = certificatesByRole.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }
    public record Role(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,49}") String value,
            @NotBlank String label, @NotNull Boolean adultOnly, @NotNull Boolean minorOnly,
            @NotNull Boolean subroleRequired, @NotNull List<@NotNull @Valid Function> subroles) {
        public Role { if (subroles != null) subroles = List.copyOf(subroles); }
        @JsonIgnore @AssertTrue public boolean isAgeRuleValid() { return !Boolean.TRUE.equals(adultOnly) || !Boolean.TRUE.equals(minorOnly); }
        @JsonIgnore @AssertTrue public boolean isRequiredFunctionAvailable() { return !Boolean.TRUE.equals(subroleRequired) || (subroles != null && !subroles.isEmpty()); }
    }
    public record Function(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,49}") String value,
            @NotBlank String label, @NotNull Boolean adultOnly, @NotNull Boolean minorOnly) {
        @JsonIgnore @AssertTrue public boolean isAgeRuleValid() { return !Boolean.TRUE.equals(adultOnly) || !Boolean.TRUE.equals(minorOnly); }
    }
    public record Certificate(@NotBlank String id, @NotBlank String label, @NotNull Boolean hasDetails) {}
}
