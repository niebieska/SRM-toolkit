package pl.srm.registrationapi.registration.api;

import com.fasterxml.jackson.databind.JsonNode;

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
        JsonNode payload
) {
}
