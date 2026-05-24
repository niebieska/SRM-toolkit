package pl.srm.registrationapi.registration.api;

import java.time.LocalDateTime;

public record RegistrationSummaryResponse(
        String registrationCode,
        String registrationType,
        String turnusCode,
        boolean minor,
        String status,
        String rejectionReason,
        LocalDateTime createdAt,
        String firstName,
        String lastName,
        Integer age
) {
}
