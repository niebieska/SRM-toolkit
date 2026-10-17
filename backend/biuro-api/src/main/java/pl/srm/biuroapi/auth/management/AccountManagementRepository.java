package pl.srm.biuroapi.auth.management;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class AccountManagementRepository {
    private record Row(String id, String username, String name, boolean active, boolean mustChange, long version) {}
    private static final String FIELDS = "id, username, display_name, active, password_change_required, version";
    private static final RowMapper<Row> ROW = (rs, n) -> new Row(rs.getString("id"), rs.getString("username"),
            rs.getString("display_name"), rs.getBoolean("active"), rs.getBoolean("password_change_required"), rs.getLong("version"));
    private final JdbcTemplate jdbc;
    public AccountManagementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockAdministration() {
        jdbc.queryForObject("SELECT id FROM biuro_account_admin_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }
    public List<AccountView> list() {
        var rows = jdbc.query("SELECT " + FIELDS + " FROM biuro_account ORDER BY username", ROW);
        Map<String, Set<AccountRole>> roles = new HashMap<>();
        jdbc.query("SELECT account_id, role FROM biuro_account_role", rs -> {
            roles.computeIfAbsent(rs.getString("account_id"), id -> new HashSet<>()).add(AccountRole.valueOf(rs.getString("role")));
        });
        Map<String, Set<String>> assignments = new HashMap<>();
        jdbc.query("SELECT account_id, turnus_code FROM biuro_account_turnus", rs -> {
            assignments.computeIfAbsent(rs.getString("account_id"), id -> new HashSet<>()).add(rs.getString("turnus_code"));
        });
        return rows.stream().map(row -> view(row, roles.getOrDefault(row.id(), Set.of()),
                assignments.getOrDefault(row.id(), Set.of()))).toList();
    }
    public AccountView get(String id, boolean lock) {
        Row row = jdbc.query("SELECT " + FIELDS + " FROM biuro_account WHERE id = ?" + (lock ? " FOR UPDATE" : ""), ROW, id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono konta."));
        var roles = new HashSet<>(jdbc.query("SELECT role FROM biuro_account_role WHERE account_id = ?",
                (rs, n) -> AccountRole.valueOf(rs.getString("role")), id));
        var codes = new HashSet<>(jdbc.queryForList("SELECT turnus_code FROM biuro_account_turnus WHERE account_id = ?", String.class, id));
        return view(row, roles, codes);
    }
    private AccountView view(Row row, Set<AccountRole> roles, Set<String> codes) {
        return new AccountView(row.id(), row.username(), row.name(), row.active(), row.mustChange(), row.version(), roles, codes);
    }
    public boolean turnusExists(String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM turnus WHERE turnus_code = ?)", Boolean.class, code));
    }
    public long activeAdministrators() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM biuro_account a JOIN biuro_account_role r ON r.account_id = a.id
                WHERE a.active = TRUE AND r.role = 'SYS_ADMIN'
                """, Long.class);
    }
    public void update(String id, String displayName, boolean active, Set<AccountRole> roles, Set<String> codes) {
        jdbc.update("UPDATE biuro_account SET display_name = ?, active = ?, version = version + 1 WHERE id = ?", displayName, active, id);
        jdbc.update("DELETE FROM biuro_account_role WHERE account_id = ?", id);
        for (var role : roles) jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, ?)", id, role.name());
        replaceAssignments(id, codes);
    }
    public void replaceAssignments(String id, Set<String> codes) {
        jdbc.update("DELETE FROM biuro_account_turnus WHERE account_id = ?", id);
        for (String code : codes) jdbc.update("INSERT INTO biuro_account_turnus (account_id, turnus_code) VALUES (?, ?)", id, code);
    }
    public void resetPassword(String id, String hash) {
        jdbc.update("UPDATE biuro_account SET password_hash = ?, password_change_required = TRUE, version = version + 1 WHERE id = ?", hash, id);
    }
}
