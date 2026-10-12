package pl.srm.biuroapi.registration.service;

import org.junit.jupiter.api.Test;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.turnus.TurnusCatalogue;
import pl.srm.biuroapi.turnus.TurnusDefinition;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationStatisticsServiceTest {
    final TurnusCatalogue catalogue = mock(TurnusCatalogue.class);
    final RegistrationStatisticsService service = new RegistrationStatisticsService(catalogue);
    RegistrationSummary row(String code, String type, String status, String gender) {
        return new RegistrationSummary("REG", type, code, false, status, null, null, "Name", "Surname", 20, gender);
    }
    void definition() {
        when(catalogue.get("T1")).thenReturn(new TurnusDefinition("T1", "Turnus", null, "Place",
                TurnusDefinition.SeasonType.SUMMER, 2026, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10),
                14, 2, 1, true, true, true, 0));
    }
    @Test void totalsIncludeUnknownGenderAndKeepTypesAndTurnusySeparate() {
        definition();
        var stats = service.calculateForTurnus("T1", List.of(
                row("T1", "PARTICIPANT", "ACCEPTED", "female"),
                row("T1", "PARTICIPANT", "ACCEPTED", null),
                row("T1", "PARTICIPANT", "NEW", "male"),
                row("T1", "PARTICIPANT", "WAITLIST", "male"),
                row("T1", "PARTICIPANT", "REJECTED", null),
                row("T1", "STAFF", "ACCEPTED", "male"),
                row("T1", "STAFF", "ACCEPTED", "unknown"),
                row("T2", "PARTICIPANT", "ACCEPTED", "male")));
        assertEquals(2, stats.participants().accepted());
        assertEquals(0, stats.participants().availablePlaces());
        assertEquals(1, stats.participants().acceptedFemale());
        assertEquals(1, stats.participants().newRegistrations());
        assertEquals(1, stats.participants().waitlist());
        assertEquals(1, stats.participants().rejected());
        assertEquals(2, stats.staff().accepted());
        assertEquals(1, stats.staff().capacity());
        assertEquals(0, stats.staff().availablePlaces());
        assertEquals(1, stats.staff().overCapacity());
    }
    @Test void emptyTurnusStillShowsBothCatalogueCapacities() {
        definition();
        var stats = service.calculateForTurnus("T1", List.of());
        assertEquals(2, stats.participants().availablePlaces());
        assertEquals(1, stats.staff().availablePlaces());
        assertEquals(0, stats.staff().overCapacity());
    }
    @Test void statusChangeFreesPlaceWithoutChangingCapacity() {
        definition();
        assertEquals(0, service.calculateForTurnus("T1", List.of(row("T1", "STAFF", "ACCEPTED", null))).staff().availablePlaces());
        var after = service.calculateForTurnus("T1", List.of(row("T1", "STAFF", "WAITLIST", null))).staff();
        assertEquals(1, after.availablePlaces());
        assertEquals(1, after.waitlist());
        assertEquals(1, after.capacity());
    }
}
