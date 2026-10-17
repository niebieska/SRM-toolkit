package pl.srm.biuroapi.auth.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class AccountAudit {
    public enum Event {
        BOOTSTRAP_ADMIN, ACCOUNT_CREATED, ACCOUNT_UPDATED, PASSWORD_RESET,
        PASSWORD_CHANGED, LOGIN_SUCCEEDED, LOGIN_FAILED, LOGOUT
    }
    public record Entry(long id, Instant occurredAt, String actorAccountId, String actorUsername,
                        String targetAccountId, Event event, Map<String, Object> details) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    public AccountAudit(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.clock = clock;
    }
    public void record(Event event, AccountPrincipal actor, String targetId, Map<String, ?> details) {
        // Callers supply an explicit allowlist of non-secret changes, never request objects.
        jdbc.update("""
                INSERT INTO biuro_account_audit (occurred_at, actor_account_id, actor_username,
                    target_account_id, event_type, details) VALUES (?, ?, ?, ?, ?, ?)
                """, Timestamp.from(clock.instant()), actor == null ? null : actor.id(),
                actor == null ? null : actor.username(), targetId, event.name(), mapper.writeValueAsString(details));
    }
    public List<Entry> forAccount(String id, int limit) {
        return jdbc.query("""
                SELECT * FROM biuro_account_audit WHERE target_account_id = ? ORDER BY id DESC LIMIT ?
                """, (rs, row) -> new Entry(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("actor_account_id"), rs.getString("actor_username"), rs.getString("target_account_id"),
                Event.valueOf(rs.getString("event_type")), mapper.readValue(rs.getString("details"),
                new TypeReference<Map<String, Object>>() {})), id, limit);
    }
}
