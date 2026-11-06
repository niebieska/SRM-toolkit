package pl.srm.biuroapi.registration.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentRecord(Long id, LocalDate date, BigDecimal amount, String method, String label, String note,
                            LocalDateTime createdAt, String actorUsername, List<PaymentAuditEntry> history) {}
