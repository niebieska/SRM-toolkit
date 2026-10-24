package pl.srm.biuroapi.registration.model;

import java.util.List;

public record RegistrationEditHistoryEntry(
        Long id,
        String changedAt,
        String actorUsername,
        List<String> changedFields
) {}
