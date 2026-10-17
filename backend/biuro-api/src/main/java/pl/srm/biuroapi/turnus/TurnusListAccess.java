package pl.srm.biuroapi.turnus;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.time.LocalDate;
import java.util.List;

@Service
public class TurnusListAccess {
    public record Summary(String turnusCode, String turnusName, String turnusLocation,
                          LocalDate startDate, LocalDate endDate) {}
    public record ViewerLists(Summary turnus, List<TurnusListsService.ListEntry> entries) {}
    private static final String FIELDS = "t.turnus_code, t.turnus_name, t.turnus_location, t.start_date, t.end_date";
    private static final RowMapper<Summary> SUMMARY = (rs, n) -> new Summary(rs.getString("turnus_code"),
            rs.getString("turnus_name"), rs.getString("turnus_location"),
            rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate());
    private final JdbcTemplate jdbc;
    private final TurnusListsService lists;
    public TurnusListAccess(JdbcTemplate jdbc, TurnusListsService lists) { this.jdbc = jdbc; this.lists = lists; }

    public List<Summary> allSummaries() {
        return jdbc.query("SELECT " + FIELDS + " FROM turnus t ORDER BY t.start_date, t.turnus_code", SUMMARY);
    }
    public List<Summary> available(AccountPrincipal principal) {
        requireListRole(principal);
        if (principal.roles().contains(AccountRole.OPERATOR)) return allSummaries();
        return jdbc.query("SELECT " + FIELDS + " FROM turnus t JOIN biuro_account_turnus a ON a.turnus_code = t.turnus_code "
                + "WHERE a.account_id = ? ORDER BY t.start_date, t.turnus_code", SUMMARY, principal.id());
    }
    public ViewerLists lists(AccountPrincipal principal, String code) {
        requireListRole(principal);
        boolean assigned = principal.roles().contains(AccountRole.OPERATOR) || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM biuro_account_turnus WHERE account_id = ? AND turnus_code = ?)",
                Boolean.class, principal.id(), code));
        if (!assigned) throw denied();
        var result = lists.get(code);
        var t = result.turnus();
        return new ViewerLists(new Summary(t.turnusCode(), t.turnusName(), t.turnusLocation(), t.startDate(), t.endDate()), result.entries());
    }
    private void requireListRole(AccountPrincipal principal) {
        if (principal.passwordChangeRequired() || (!principal.roles().contains(AccountRole.OPERATOR)
                && !principal.roles().contains(AccountRole.VIEWER))) throw denied();
    }
    private ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu do list tego turnusu."); }
}
