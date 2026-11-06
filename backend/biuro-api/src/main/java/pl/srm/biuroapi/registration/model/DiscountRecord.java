package pl.srm.biuroapi.registration.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DiscountRecord(BigDecimal amount, String description, LocalDateTime changedAt,
                             String actorUsername) {}
