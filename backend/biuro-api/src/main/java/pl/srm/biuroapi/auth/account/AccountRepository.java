package pl.srm.biuroapi.auth.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Repository
public class AccountRepository {
    private static final RowMapper<AccountCredentials> CREDENTIALS = (rs, row) ->
            new AccountCredentials(rs.getString("id"), rs.getString("password_hash"), rs.getBoolean("active"));
    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<AccountCredentials> findByUsername(String username) {
        return jdbc.query("SELECT id, password_hash, active FROM biuro_account WHERE username = ?",
                CREDENTIALS, username).stream().findFirst();
    }

    public Optional<AccountCredentials> lockById(String id) {
        return jdbc.query("SELECT id, password_hash, active FROM biuro_account WHERE id = ? FOR UPDATE",
                CREDENTIALS, id).stream().findFirst();
    }

    public Optional<AccountCredentials> lockByUsername(String username) {
        return jdbc.query("SELECT id, password_hash, active FROM biuro_account WHERE username = ? FOR UPDATE",
                CREDENTIALS, username).stream().findFirst();
    }

    public Optional<AccountPrincipal> findSession(String accountId, String sessionId, Instant now) {
        return jdbc.query("""
                SELECT a.id, a.username, a.display_name, a.password_change_required
                FROM biuro_account a JOIN biuro_session s ON s.account_id = a.id
                WHERE a.id = ? AND s.id = ? AND a.active = TRUE AND s.expires_at > ?
                """, (rs, row) -> new AccountPrincipal(rs.getString("id"), rs.getString("username"),
                rs.getString("display_name"), rs.getBoolean("password_change_required"),
                roles(accountId), sessionId), accountId, sessionId, Timestamp.from(now)).stream().findFirst();
    }

    private Set<AccountRole> roles(String accountId) {
        return new HashSet<>(jdbc.query("SELECT role FROM biuro_account_role WHERE account_id = ?",
                (rs, row) -> AccountRole.valueOf(rs.getString("role")), accountId));
    }

    public void create(String id, String username, String name, String hash, Set<AccountRole> roles, Instant now) {
        jdbc.update("""
                INSERT INTO biuro_account (id, username, display_name, password_hash, active,
                    password_change_required, created_at) VALUES (?, ?, ?, ?, TRUE, TRUE, ?)
                """, id, AccountPolicy.username(username), name, hash, Timestamp.from(now));
        for (AccountRole role : roles)
            jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, ?)", id, role.name());
    }

    public void createSession(String sessionId, String accountId, Instant expiresAt) {
        jdbc.update("INSERT INTO biuro_session (id, account_id, expires_at) VALUES (?, ?, ?)",
                sessionId, accountId, Timestamp.from(expiresAt));
    }

    public void removeExpiredSessions(Instant now) {
        jdbc.update("DELETE FROM biuro_session WHERE expires_at <= ?", Timestamp.from(now));
    }

    public int revokeSession(String accountId, String sessionId) {
        return jdbc.update("DELETE FROM biuro_session WHERE account_id = ? AND id = ?", accountId, sessionId);
    }

    public void revokeAllSessions(String accountId) {
        jdbc.update("DELETE FROM biuro_session WHERE account_id = ?", accountId);
    }

    public void changePassword(String accountId, String hash) {
        jdbc.update("UPDATE biuro_account SET password_hash = ?, password_change_required = FALSE, version = version + 1 WHERE id = ?",
                hash, accountId);
        jdbc.update("DELETE FROM biuro_session WHERE account_id = ?", accountId);
    }

    public boolean lockSetupAndNeedsAccount() {
        boolean completed = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT completed FROM biuro_account_setup WHERE id = 1 FOR UPDATE", Boolean.class));
        return !completed && jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Long.class) == 0;
    }

    public void completeSetup() {
        jdbc.update("UPDATE biuro_account_setup SET completed = TRUE WHERE id = 1");
    }
}
