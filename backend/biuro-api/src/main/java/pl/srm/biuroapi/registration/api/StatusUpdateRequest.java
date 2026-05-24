package pl.srm.biuroapi.registration.api;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
        @NotBlank String status,
        String rejectionReason
) {
}
