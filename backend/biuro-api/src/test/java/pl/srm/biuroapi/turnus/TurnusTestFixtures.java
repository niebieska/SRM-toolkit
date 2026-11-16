package pl.srm.biuroapi.turnus;

import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit sample Turnusy for tests that need to exercise a populated catalogue. */
public final class TurnusTestFixtures {
    private TurnusTestFixtures() { }

    public static void resetLegacySummerTurnuses(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM turnus WHERE turnus_code IN ('ZAGLE26T1', 'ZAGLE26T2')");
        jdbc.update("""
                INSERT INTO turnus (turnus_code, turnus_name, turnus_description, turnus_location,
                  season_type, season_year, start_date, end_date, min_age, participant_capacity,
                  active, participant_registration_open, version, staff_capacity,
                  staff_registration_open, staff_requirements, participant_fee, staff_fees, fees_configured)
                VALUES
                  ('ZAGLE26T1', 'Żagle 2026 – Turnus I', 'Testowy turnus żeglarski.', 'Testowe miejsce.',
                   'SUMMER', 2026, '2026-08-01', '2026-08-11', 14, 60, TRUE, TRUE, 0, 10, TRUE, '[]', 0.00, '[]', FALSE),
                  ('ZAGLE26T2', 'Żagle 2026 – Turnus II', 'Testowy turnus żeglarski.', 'Testowe miejsce.',
                   'SUMMER', 2026, '2026-08-11', '2026-08-22', 16, 60, TRUE, TRUE, 0, 10, TRUE, '[]', 0.00, '[]', FALSE)
                """);
    }
}
