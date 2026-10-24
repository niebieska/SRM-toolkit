package pl.srm.registrationapi.registration.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record RegistrationEditHistoryEntryResponse(
        Long id,
        LocalDateTime changedAt,
        String actorUsername,
        List<String> changedFields
) {}
