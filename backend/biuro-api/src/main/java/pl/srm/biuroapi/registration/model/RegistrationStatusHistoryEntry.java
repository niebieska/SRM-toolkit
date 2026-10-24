package pl.srm.biuroapi.registration.model;

import java.time.LocalDateTime;

public record RegistrationStatusHistoryEntry(
        Long id,
        String previousStatus,
        String newStatus,
        LocalDateTime changedAt,
        String actorUsername,
        String rejectionReason
) {
}
