package pl.srm.biuroapi.registration.model;

import java.util.Map;
import java.util.List;

public record RegistrationDetail(
        String registrationCode,
        String registrationType,
        String turnusCode,
        boolean minor,
        String status,
        String rejectionReason,
        String createdAt,
        String firstName,
        String lastName,
        Integer age,
        Map<String, Object> payload,
        List<RegistrationStatusHistoryEntry> statusHistory,
        Long version,
        List<RegistrationEditHistoryEntry> editHistory
) {
}
