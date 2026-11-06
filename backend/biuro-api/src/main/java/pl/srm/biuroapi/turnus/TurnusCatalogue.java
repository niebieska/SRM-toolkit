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
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TurnusCatalogue {
    private final JdbcTemplate jdbc;
    private final RegistrationApiClient registrations;
    private final StaffRequirements requirements;
    private final StaffFees fees;
    private final RowMapper<TurnusDefinition> mapper;
    public TurnusCatalogue(JdbcTemplate jdbc, RegistrationApiClient registrations, StaffRequirements requirements,
            StaffFees fees) {
        this.jdbc = jdbc;
        this.registrations = registrations;
        this.requirements = requirements;
        this.fees = fees;
        mapper = (rs, row) -> new TurnusDefinition(
            rs.getString("turnus_code"), rs.getString("turnus_name"), rs.getString("turnus_description"),
            rs.getString("turnus_location"), TurnusDefinition.SeasonType.valueOf(rs.getString("season_type")),
            rs.getInt("season_year"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
            rs.getInt("min_age"), rs.getInt("participant_capacity"), rs.getInt("staff_capacity"), rs.getBoolean("active"),
            rs.getBoolean("participant_registration_open"),
            rs.getBoolean("staff_registration_open"), rs.getLong("version"), requirements.read(rs.getString("staff_requirements")),
            rs.getBigDecimal("participant_fee"), fees.read(rs.getString("staff_fees")), rs.getBoolean("fees_configured"));
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
        var staffFees = fees.validate(request.staffFees(), request.seasonType());
        var participantFee = request.participantFee() == null ? java.math.BigDecimal.ZERO : request.participantFee();
        validateParticipantFee(participantFee);
        try {
            jdbc.update("""
                    INSERT INTO turnus (turnus_code, turnus_name, turnus_description, turnus_location,
                      season_type, season_year, start_date, end_date, min_age, participant_capacity, staff_capacity, active, participant_registration_open, staff_registration_open, version, staff_requirements, participant_fee, staff_fees, fees_configured)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, TRUE)
                    """, request.turnusCode(), request.turnusName().trim(), request.turnusDescription(), request.turnusLocation().trim(),
                    request.seasonType().name(), request.seasonYear(), request.startDate(), request.endDate(), request.minAge(),
                    request.participantCapacity(), request.staffCapacity(), request.active(), request.participantRegistrationOpen(), request.staffRegistrationOpen(),
                    requirements.write(staffing), participantFee, fees.write(staffFees));
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
        if (current.feesConfigured() && request.seasonType() != current.seasonType())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nie można zmienić sezonu turnusu po ustaleniu opłat.");
        var staffing = requirements.validate(request.staffRequirements() == null
                ? current.staffRequirements() : request.staffRequirements(), request.seasonType());
        var participantFee = request.participantFee() == null ? current.participantFee() : request.participantFee();
        validateParticipantFee(participantFee);
        var staffFees = fees.validate(request.staffFees() == null ? current.staffFees() : request.staffFees(), request.seasonType());
        if (current.feesConfigured() && (participantFee.compareTo(current.participantFee()) != 0
                || !sameStaffFees(staffFees, current.staffFees())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Opłaty są ustalane przy tworzeniu turnusu. Późniejszą obniżkę przypisz jako rabat do zgłoszenia.");
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
                  participant_registration_open = ?, staff_registration_open = ?, staff_requirements = ?, participant_fee = ?, staff_fees = ?, fees_configured = ?, version = version + 1 WHERE turnus_code = ? AND version = ?
                """, request.turnusName().trim(), request.turnusDescription(), request.turnusLocation().trim(),
                request.seasonType().name(), request.seasonYear(), request.startDate(), request.endDate(), request.minAge(),
                request.participantCapacity(), request.staffCapacity(), request.active(), request.participantRegistrationOpen(), request.staffRegistrationOpen(),
                requirements.write(staffing), participantFee, fees.write(staffFees),
                current.feesConfigured() || request.participantFee() != null || request.staffFees() != null,
                code, request.version());
        if (changed == 0) {
            get(code); // Distinguish missing code from an edit conflict.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turnus został zmieniony. Odśwież dane przed zapisem.");
        }
        return get(code);
    }

    private boolean sameStaffFees(List<StaffFee> left, List<StaffFee> right) {
        Map<StaffFee.Selection, java.math.BigDecimal> leftBySelection = left.stream()
                .collect(Collectors.toMap(StaffFee::selection, StaffFee::amount));
        Map<StaffFee.Selection, java.math.BigDecimal> rightBySelection = right.stream()
                .collect(Collectors.toMap(StaffFee::selection, StaffFee::amount));
        return leftBySelection.size() == rightBySelection.size()
                && leftBySelection.entrySet().stream().allMatch(entry -> {
                    var rightAmount = rightBySelection.get(entry.getKey());
                    return rightAmount != null && entry.getValue().compareTo(rightAmount) == 0;
                });
    }

    private void validateParticipantFee(java.math.BigDecimal amount) {
        if (amount.signum() < 0 || amount.scale() > 2 || Math.max(0, amount.precision() - amount.scale()) > 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Opłata uczestnika musi być nieujemna i mieć maksymalnie dwa miejsca po przecinku.");
    }
}
