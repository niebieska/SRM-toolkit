package pl.srm.biuroapi.turnus;

import pl.srm.biuroapi.registration.client.RegistrationApiClient;

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
    private final RegistrationApiClient registrations;
    private final StaffRequirements requirements;
    private final RowMapper<TurnusDefinition> mapper;
    public TurnusCatalogue(JdbcTemplate jdbc, RegistrationApiClient registrations, StaffRequirements requirements) {
        this.jdbc = jdbc;
        this.registrations = registrations;
        this.requirements = requirements;
        mapper = (rs, row) -> new TurnusDefinition(
            rs.getString("turnus_code"), rs.getString("turnus_name"), rs.getString("turnus_description"),
            rs.getString("turnus_location"), TurnusDefinition.SeasonType.valueOf(rs.getString("season_type")),
            rs.getInt("season_year"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
            rs.getInt("min_age"), rs.getInt("participant_capacity"), rs.getInt("staff_capacity"), rs.getBoolean("active"),
            rs.getBoolean("participant_registration_open"),
            rs.getBoolean("staff_registration_open"), rs.getLong("version"), requirements.read(rs.getString("staff_requirements")));
    }
    public List<TurnusDefinition> list() {
        return jdbc.query("SELECT * FROM turnus ORDER BY start_date, turnus_code", mapper);
    }
    public TurnusDefinition get(String code) {
        return read(code, false);
    }
    private TurnusDefinition read(String code, boolean lockForUpdate) {
        String sql = "SELECT * FROM turnus WHERE turnus_code = ?" + (lockForUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, mapper, code).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono turnusu."));
    }
    @Transactional
    public TurnusDefinition create(TurnusRequest request) {
        var staffing = requirements.validate(request.staffRequirements(), request.seasonType());
        try {
            jdbc.update("""
                    INSERT INTO turnus (turnus_code, turnus_name, turnus_description, turnus_location,
                      season_type, season_year, start_date, end_date, min_age, participant_capacity, staff_capacity, active, participant_registration_open, staff_registration_open, version, staff_requirements)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                    """, request.turnusCode(), request.turnusName().trim(), request.turnusDescription(), request.turnusLocation().trim(),
                    request.seasonType().name(), request.seasonYear(), request.startDate(), request.endDate(), request.minAge(),
                    request.participantCapacity(), request.staffCapacity(), request.active(), request.participantRegistrationOpen(), request.staffRegistrationOpen(), requirements.write(staffing));
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
        TurnusDefinition current = read(code, true);
        if (current.version() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnus został zmieniony. Odśwież dane przed zapisem.");
        var staffing = requirements.validate(request.staffRequirements() == null
                ? current.staffRequirements() : request.staffRequirements(), request.seasonType());
        boolean reducingParticipants = request.participantCapacity() < current.participantCapacity();
        boolean reducingStaff = request.staffCapacity() < current.staffCapacity();
        if (reducingParticipants || reducingStaff) {
            var counts = registrations.fetchAcceptedCounts(code);
            if (reducingParticipants && request.participantCapacity() < counts.participants())
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Liczba miejsc dla uczestników nie może być niższa niż liczba przyjętych: " + counts.participants() + ".");
            if (reducingStaff && request.staffCapacity() < counts.staff())
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Liczba miejsc dla kadry nie może być niższa niż liczba przyjętych: " + counts.staff() + ".");
        }
        int changed = jdbc.update("""
                UPDATE turnus SET turnus_name = ?, turnus_description = ?, turnus_location = ?, season_type = ?,
                  season_year = ?, start_date = ?, end_date = ?, min_age = ?, participant_capacity = ?, staff_capacity = ?, active = ?,
                  participant_registration_open = ?, staff_registration_open = ?, staff_requirements = ?, version = version + 1 WHERE turnus_code = ? AND version = ?
                """, request.turnusName().trim(), request.turnusDescription(), request.turnusLocation().trim(),
                request.seasonType().name(), request.seasonYear(), request.startDate(), request.endDate(), request.minAge(),
                request.participantCapacity(), request.staffCapacity(), request.active(), request.participantRegistrationOpen(), request.staffRegistrationOpen(), requirements.write(staffing), code, request.version());
        if (changed == 0) {
            get(code); // Distinguish missing code from an edit conflict.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnus został zmieniony. Odśwież dane przed zapisem.");
        }
        return get(code);
    }
}
