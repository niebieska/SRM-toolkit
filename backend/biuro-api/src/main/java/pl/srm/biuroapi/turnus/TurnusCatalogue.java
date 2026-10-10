package pl.srm.biuroapi.turnus;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class TurnusCatalogue {
    private final JdbcTemplate jdbc;
    private final RowMapper<TurnusDefinition> mapper = (rs, row) -> new TurnusDefinition(
            rs.getString("turnus_code"), rs.getString("turnus_name"), rs.getString("turnus_description"),
            rs.getString("turnus_location"), TurnusDefinition.SeasonType.valueOf(rs.getString("season_type")),
            rs.getInt("season_year"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
            rs.getInt("min_age"), rs.getInt("capacity"), rs.getBoolean("active"),
            rs.getBoolean("registration_open"), rs.getLong("version"));
    public TurnusCatalogue(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<TurnusDefinition> list() {
        return jdbc.query("SELECT * FROM turnus ORDER BY start_date, turnus_code", mapper);
    }
    public TurnusDefinition get(String code) {
        return jdbc.query("SELECT * FROM turnus WHERE turnus_code = ?", mapper, code).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono turnusu."));
    }
    @Transactional
    public TurnusDefinition create(TurnusRequest request) {
        try {
            jdbc.update("""
                    INSERT INTO turnus (turnus_code, turnus_name, turnus_description, turnus_location,
                      season_type, season_year, start_date, end_date, min_age, capacity, active, registration_open, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, request.turnusCode(), request.turnusName().trim(), request.turnusDescription(), request.turnusLocation().trim(),
                    request.seasonType().name(), request.seasonYear(), request.startDate(), request.endDate(), request.minAge(),
                    request.capacity(), request.active(), request.registrationOpen());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kod turnusu jest już używany.");
        }
        return get(request.turnusCode());
    }
    @Transactional
    public TurnusDefinition update(String code, TurnusRequest request) {
        if (!code.equals(request.turnusCode()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kod istniejącego turnusu jest niezmienny.");
        if (request.version() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj wersję edytowanego turnusu.");
        int changed = jdbc.update("""
                UPDATE turnus SET turnus_name = ?, turnus_description = ?, turnus_location = ?, season_type = ?,
                  season_year = ?, start_date = ?, end_date = ?, min_age = ?, capacity = ?, active = ?,
                  registration_open = ?, version = version + 1 WHERE turnus_code = ? AND version = ?
                """, request.turnusName().trim(), request.turnusDescription(), request.turnusLocation().trim(),
                request.seasonType().name(), request.seasonYear(), request.startDate(), request.endDate(), request.minAge(),
                request.capacity(), request.active(), request.registrationOpen(), code, request.version());
        if (changed == 0) {
            get(code); // Distinguish missing code from an edit conflict.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnus został zmieniony. Odśwież dane przed zapisem.");
        }
        return get(code);
    }
}
