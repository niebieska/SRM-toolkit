package pl.srm.registrationapi.registration.dto.response;

import java.time.LocalDateTime;

public record RegistrationStatusHistoryEntryResponse(
        Long id,
        String previousStatus,
        String newStatus,
        LocalDateTime changedAt,
        String actorUsername,
        String rejectionReason
) {
}
