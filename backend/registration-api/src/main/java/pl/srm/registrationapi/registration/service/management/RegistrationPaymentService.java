package pl.srm.registrationapi.registration.service.management;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.registrationapi.registration.dto.request.DiscountRecordRequest;
import pl.srm.registrationapi.registration.dto.request.PaymentRecordRequest;
import pl.srm.registrationapi.registration.dto.response.DiscountRecordResponse;
import pl.srm.registrationapi.registration.dto.response.PaymentAuditEntryResponse;
import pl.srm.registrationapi.registration.dto.response.PaymentLedgerEntryResponse;
import pl.srm.registrationapi.registration.dto.response.PaymentRecordResponse;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegistrationPaymentService {
    private static final Set<String> METHODS = Set.of("Przelew", "Gotówka");
    private final JdbcTemplate jdbc;

    public RegistrationPaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PaymentLedgerEntryResponse> getLedger() {
        var paymentRows = jdbc.query("""
                SELECT p.id, p.registration_id, p.payment_date, p.amount, p.method, p.label, p.note,
                       p.created_at, p.actor_username
                FROM registration_payment p JOIN registration r ON r.id = p.registration_id
                ORDER BY r.id, p.payment_date, p.id
                """, (rs, row) -> new PaymentRow(rs.getLong("id"), rs.getLong("registration_id"),
                rs.getDate("payment_date").toLocalDate(), rs.getBigDecimal("amount"), rs.getString("method"),
                rs.getString("label"), rs.getString("note"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("actor_username")));
        var auditByPayment = new HashMap<Long, List<PaymentAuditEntryResponse>>();
        jdbc.query("""
                SELECT payment_id, changed_at, actor_username, reason, previous_payment_date, previous_amount,
                       previous_method, previous_label, previous_note, new_payment_date, new_amount,
                       new_method, new_label, new_note
                FROM registration_payment_audit ORDER BY payment_id, changed_at, id
                """, rs -> { auditByPayment.computeIfAbsent(rs.getLong("payment_id"), ignored -> new ArrayList<>())
                .add(new PaymentAuditEntryResponse(rs.getTimestamp("changed_at").toLocalDateTime(),
                        rs.getString("actor_username"), rs.getString("reason"),
                        rs.getDate("previous_payment_date").toLocalDate(), rs.getBigDecimal("previous_amount"),
                        rs.getString("previous_method"), rs.getString("previous_label"), rs.getString("previous_note"),
                        rs.getDate("new_payment_date").toLocalDate(), rs.getBigDecimal("new_amount"),
                        rs.getString("new_method"), rs.getString("new_label"), rs.getString("new_note"))); });

        var groupedPayments = new HashMap<Long, List<PaymentRecordResponse>>();
        for (var row : paymentRows) groupedPayments.computeIfAbsent(row.registrationId(), ignored -> new ArrayList<>())
                .add(new PaymentRecordResponse(row.id(), row.date(), row.amount(), row.method(), row.label(), row.note(),
                        row.createdAt(), row.actorUsername(), List.copyOf(auditByPayment.getOrDefault(row.id(), List.of()))));

        var discounts = new HashMap<Long, DiscountRecordResponse>();
        jdbc.query("SELECT registration_id, amount, description, changed_at, actor_username FROM registration_discount",
                rs -> { discounts.put(rs.getLong("registration_id"), new DiscountRecordResponse(
                        rs.getBigDecimal("amount"), rs.getString("description"),
                        rs.getTimestamp("changed_at").toLocalDateTime(), rs.getString("actor_username"))); });

        return jdbc.query("SELECT id, registration_code FROM registration ORDER BY id", (rs, row) -> {
            long registrationId = rs.getLong("id");
            return new PaymentLedgerEntryResponse(registrationId, rs.getString("registration_code"),
                    List.copyOf(groupedPayments.getOrDefault(registrationId, List.of())), discounts.get(registrationId));
        });
    }

    @Transactional
    public PaymentLedgerEntryResponse addPayment(String code, String idempotencyKey, PaymentRecordRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64)
            throw badRequest("Nieprawidłowy klucz operacji wpłaty.");
        var registration = lockRegistration(code);
        validatePayment(request.date(), request.amount(), request.method(), request.label(), request.note());
        if (request.correctionReason() != null && !request.correctionReason().isBlank())
            throw badRequest("Powód korekty można podać wyłącznie przy edycji wpłaty.");
        var existing = jdbc.query("""
                SELECT id, payment_date, amount, method, label, note FROM registration_payment
                WHERE registration_id = ? AND idempotency_key = ?
                """, (rs, row) -> new ExistingPayment(rs.getLong("id"), rs.getDate("payment_date").toLocalDate(),
                rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("label"), rs.getString("note")),
                registration.id(), idempotencyKey).stream().findFirst().orElse(null);
        if (existing != null) {
            if (!existing.matches(request))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Klucz wpłaty został już użyty dla innych danych.");
            return getLedgerEntry(registration.id(), registration.code());
        }
        requireAccepted(registration);
        KeyHolder key = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO registration_payment (registration_id, payment_date, amount, method, label, note,
                      idempotency_key, created_at, actor_account_id, actor_username) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, registration.id()); statement.setObject(2, request.date());
            statement.setBigDecimal(3, request.amount()); statement.setString(4, request.method());
            statement.setString(5, request.label().trim()); statement.setString(6, trimToNull(request.note()));
            statement.setString(7, idempotencyKey); statement.setTimestamp(8, Timestamp.valueOf(now));
            statement.setString(9, request.actorAccountId()); statement.setString(10, request.actorUsername().trim());
            return statement;
        }, key);
        return getLedgerEntry(registration.id(), registration.code());
    }

    @Transactional
    public PaymentLedgerEntryResponse editPayment(String code, long paymentId, PaymentRecordRequest request) {
        var registration = lockRegistration(code);
        validatePayment(request.date(), request.amount(), request.method(), request.label(), request.note());
        String reason = trimToNull(request.correctionReason());
        if (reason == null) throw badRequest("Podaj powód korekty wpłaty.");
        var previous = jdbc.query("""
                SELECT payment_date, amount, method, label, note FROM registration_payment
                WHERE id = ? AND registration_id = ? FOR UPDATE
                """, (rs, row) -> new PaymentValues(rs.getDate("payment_date").toLocalDate(),
                rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("label"), rs.getString("note")),
                paymentId, registration.id()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono wpłaty."));
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO registration_payment_audit (payment_id, changed_at, actor_account_id, actor_username,
                  reason, previous_payment_date, previous_amount, previous_method, previous_label, previous_note,
                  new_payment_date, new_amount, new_method, new_label, new_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, paymentId, Timestamp.valueOf(now), request.actorAccountId(), request.actorUsername().trim(), reason,
                previous.date(), previous.amount(), previous.method(), previous.label(), previous.note(), request.date(),
                request.amount(), request.method(), request.label().trim(), trimToNull(request.note()));
        jdbc.update("UPDATE registration_payment SET payment_date = ?, amount = ?, method = ?, label = ?, note = ? WHERE id = ?",
                request.date(), request.amount(), request.method(), request.label().trim(), trimToNull(request.note()), paymentId);
        return getLedgerEntry(registration.id(), registration.code());
    }

    @Transactional
    public PaymentLedgerEntryResponse setDiscount(String code, DiscountRecordRequest request) {
        var registration = lockRegistration(code);
        requireAccepted(registration);
        BigDecimal amount = request.amount().setScale(2);
        BigDecimal fee = request.feeAmount().setScale(2);
        if (amount.compareTo(fee) > 0) throw badRequest("Rabat nie może być wyższy niż opłata za turnus.");
        String description = trimToNull(request.description());
        var existing = jdbc.query("SELECT amount, description FROM registration_discount WHERE registration_id = ? FOR UPDATE",
                (rs, row) -> new DiscountValues(rs.getBigDecimal("amount"), rs.getString("description")),
                registration.id()).stream().findFirst().orElse(null);
        if (existing != null && existing.amount().compareTo(amount) == 0
                && java.util.Objects.equals(existing.description(), description))
            return getLedgerEntry(registration.id(), registration.code());
        if (description == null) throw badRequest("Podaj opis rabatu.");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO registration_discount_audit (registration_id, previous_amount, previous_description,
                  new_amount, new_description, changed_at, actor_account_id, actor_username)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, registration.id(), existing == null ? null : existing.amount(),
                existing == null ? null : existing.description(), amount, description, Timestamp.valueOf(now),
                request.actorAccountId(), request.actorUsername().trim());
        if (existing == null) {
            jdbc.update("""
                    INSERT INTO registration_discount (registration_id, amount, description, changed_at,
                      actor_account_id, actor_username) VALUES (?, ?, ?, ?, ?, ?)
                    """, registration.id(), amount, description, Timestamp.valueOf(now),
                    request.actorAccountId(), request.actorUsername().trim());
        } else {
            jdbc.update("""
                    UPDATE registration_discount SET amount = ?, description = ?, changed_at = ?,
                      actor_account_id = ?, actor_username = ? WHERE registration_id = ?
                    """, amount, description, Timestamp.valueOf(now), request.actorAccountId(),
                    request.actorUsername().trim(), registration.id());
        }
        return getLedgerEntry(registration.id(), registration.code());
    }

    private RegistrationState lockRegistration(String code) {
        var rows = jdbc.query("SELECT id, registration_code, status FROM registration WHERE registration_code = ? FOR UPDATE",
                (rs, row) -> new RegistrationState(rs.getLong("id"), rs.getString("registration_code"), rs.getString("status")), code);
        var registration = rows.stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia."));
        return registration;
    }

    private void requireAccepted(RegistrationState registration) {
        if (!"ACCEPTED".equals(registration.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nowe wpłaty i rabaty można zapisywać tylko dla zaakceptowanych zgłoszeń.");
    }

    private void validatePayment(LocalDate date, BigDecimal amount, String method, String label, String note) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || Math.max(0, amount.precision() - amount.scale()) > 8)
            throw badRequest("Kwota wpłaty musi być większa od zera i mieć maksymalnie dwa miejsca po przecinku.");
        if (!METHODS.contains(method)) throw badRequest("Nieprawidłowa metoda płatności.");
        if (label == null || label.isBlank()) throw badRequest("Opis wpłaty jest wymagany.");
        if (note != null && note.length() > 300) throw badRequest("Notatka może mieć maksymalnie 300 znaków.");
    }

    private PaymentLedgerEntryResponse getLedgerEntry(long registrationId, String code) {
        var audits = new HashMap<Long, List<PaymentAuditEntryResponse>>();
        jdbc.query("""
                SELECT a.payment_id, a.changed_at, a.actor_username, a.reason, a.previous_payment_date,
                       a.previous_amount, a.previous_method, a.previous_label, a.previous_note, a.new_payment_date,
                       a.new_amount, a.new_method, a.new_label, a.new_note
                FROM registration_payment_audit a JOIN registration_payment p ON p.id = a.payment_id
                WHERE p.registration_id = ? ORDER BY a.changed_at, a.id
                """, rs -> { audits.computeIfAbsent(rs.getLong("payment_id"), ignored -> new ArrayList<>())
                .add(new PaymentAuditEntryResponse(rs.getTimestamp("changed_at").toLocalDateTime(),
                        rs.getString("actor_username"), rs.getString("reason"),
                        rs.getDate("previous_payment_date").toLocalDate(), rs.getBigDecimal("previous_amount"),
                        rs.getString("previous_method"), rs.getString("previous_label"), rs.getString("previous_note"),
                        rs.getDate("new_payment_date").toLocalDate(), rs.getBigDecimal("new_amount"),
                        rs.getString("new_method"), rs.getString("new_label"), rs.getString("new_note"))); }, registrationId);
        var payments = jdbc.query("""
                SELECT id, payment_date, amount, method, label, note, created_at, actor_username
                FROM registration_payment WHERE registration_id = ? ORDER BY payment_date, id
                """, (rs, row) -> {
            long id = rs.getLong("id");
            return new PaymentRecordResponse(id, rs.getDate("payment_date").toLocalDate(), rs.getBigDecimal("amount"),
                    rs.getString("method"), rs.getString("label"), rs.getString("note"),
                    rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("actor_username"),
                    List.copyOf(audits.getOrDefault(id, List.of())));
        }, registrationId);
        var discounts = jdbc.query("""
                SELECT amount, description, changed_at, actor_username FROM registration_discount WHERE registration_id = ?
                """, (rs, row) -> new DiscountRecordResponse(rs.getBigDecimal("amount"), rs.getString("description"),
                rs.getTimestamp("changed_at").toLocalDateTime(), rs.getString("actor_username")), registrationId);
        return new PaymentLedgerEntryResponse(registrationId, code, payments,
                discounts.isEmpty() ? null : discounts.getFirst());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private record PaymentRow(long id, long registrationId, LocalDate date, BigDecimal amount, String method,
                              String label, String note, LocalDateTime createdAt, String actorUsername) {}
    private record PaymentValues(LocalDate date, BigDecimal amount, String method, String label, String note) {}
    private record DiscountValues(BigDecimal amount, String description) {}
    private record ExistingPayment(long id, LocalDate date, BigDecimal amount, String method, String label, String note) {
        boolean matches(PaymentRecordRequest request) {
            return date.equals(request.date()) && amount.compareTo(request.amount()) == 0
                    && method.equals(request.method()) && label.equals(request.label().trim())
                    && java.util.Objects.equals(note, trim(request.note()));
        }
        private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    }
    private record RegistrationState(long id, String code, String status) {}
}
