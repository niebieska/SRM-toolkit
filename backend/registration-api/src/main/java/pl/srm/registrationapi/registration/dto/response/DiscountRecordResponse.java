package pl.srm.registrationapi.registration.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DiscountRecordResponse(BigDecimal amount, String description, LocalDateTime changedAt,
                                     String actorUsername) {}
