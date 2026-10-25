package pl.srm.biuroapi.registration.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RegistrationEditRequest(
        @NotNull Long expectedVersion,
        @NotEmpty @Size(max = 40) Map<String, Object> changes
) {}
