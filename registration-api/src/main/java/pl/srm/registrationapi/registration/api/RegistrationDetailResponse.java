package pl.srm.registrationapi.registration.api;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

public record RegistrationDetailResponse(
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
        @JsonRawValue String payload
) {
}
