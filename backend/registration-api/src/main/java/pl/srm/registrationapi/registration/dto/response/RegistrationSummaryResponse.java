package pl.srm.registrationapi.registration.dto.response;

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
        Integer age,
        String gender
) {
}
