package pl.srm.registrationapi.registration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RegistrationEditRequest(
        @NotNull Long expectedVersion,
        @NotEmpty @Size(max = 40) Map<String, Object> changes,
        @NotBlank @Size(max = 36) String actorAccountId,
        @NotBlank @Size(max = 64) String actorUsername
) {
}
