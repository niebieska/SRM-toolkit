package pl.srm.biuroapi.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pl.srm.biuroapi.auth.config.JwtConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class OperatorTestTokens {
    private static final String HASH = new BCryptPasswordEncoder(4).encode("test-operator-password");
    private OperatorTestTokens() {}

    public static String create(JdbcTemplate jdbc, JwtConfig jwt, String username) {
        var ids = jdbc.queryForList("SELECT id FROM biuro_account WHERE username = ?", String.class, username);
        String id = ids.isEmpty() ? UUID.randomUUID().toString() : ids.getFirst();
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO biuro_account (id, username, display_name, password_hash, active,
                    password_change_required, created_at) VALUES (?, ?, 'Test operator', ?, TRUE, FALSE, ?)
                    """, id, username, HASH, Timestamp.from(Instant.now()));
            jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, 'OPERATOR')", id);
        }
        String sessionId = UUID.randomUUID().toString();
        var now = Instant.now();
        jdbc.update("INSERT INTO biuro_session (id, account_id, expires_at) VALUES (?, ?, ?)",
                sessionId, id, Timestamp.from(jwt.expiresAt(now)));
        return jwt.generateToken(id, sessionId, now);
    }
}
