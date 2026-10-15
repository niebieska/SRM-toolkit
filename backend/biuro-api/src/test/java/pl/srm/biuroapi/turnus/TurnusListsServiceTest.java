package pl.srm.biuroapi.turnus;

import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.TurnusListPerson;
import pl.srm.biuroapi.registration.model.TurnusListPerson.AdditionalData;
import pl.srm.biuroapi.registration.model.TurnusListPerson.Qualification;
import pl.srm.biuroapi.season.SeasonStaffCatalogue;
import pl.srm.biuroapi.season.StaffCatalogueValidator;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TurnusListsServiceTest {
    final TurnusCatalogue turnuses = mock(TurnusCatalogue.class);
    final RegistrationApiClient registrations = mock(RegistrationApiClient.class);

    TurnusListsService service() {
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var seasons = new SeasonStaffCatalogue(new ObjectMapper(), new DefaultResourceLoader(),
                    new StaffCatalogueValidator(validation.getValidator()), "classpath:staff-seasons/");
            return new TurnusListsService(turnuses, registrations, seasons);
        }
    }
    TurnusDefinition turnus(TurnusDefinition.SeasonType season) {
        return new TurnusDefinition("T1", "Turnus", null, "Place", season, 2027,
                LocalDate.of(2027, 1, 17), LocalDate.of(2027, 1, 24), 14, 50, 10,
                true, true, true, 0, List.of());
    }
    TurnusListPerson person(LocalDate birthDate, String role, String subrole) {
        return new TurnusListPerson("R1", "STAFF", "Jan", "Nowak", null, null,
                birthDate, "male", null, null, null, role, subrole, birthDate == null, false, null);
    }

    @Test void agesUseTurnusStartAndPreserveMissingAndFutureBirthDates() {
        when(turnuses.get("T1")).thenReturn(turnus(TurnusDefinition.SeasonType.WINTER));
        when(registrations.fetchTurnusList("T1")).thenReturn(List.of(
                person(LocalDate.of(2009, 1, 17), "animator", null),
                person(LocalDate.of(2009, 1, 18), "animator", null),
                person(null, "animator", null), person(LocalDate.of(2028, 1, 1), "animator", null)));
        var result = service().get("T1");
        assertEquals(18, result.entries().get(0).age());
        assertEquals(17, result.entries().get(1).age());
        assertNull(result.entries().get(2).age());
        assertNull(result.entries().get(3).age());
        verify(registrations).fetchTurnusList("T1");
        verifyNoMoreInteractions(registrations);
    }

    @Test void seasonRoleLabelsAreResolvedWithoutLosingUnknownHistoricalValues() {
        when(turnuses.get("T1")).thenReturn(turnus(TurnusDefinition.SeasonType.SUMMER));
        when(registrations.fetchTurnusList("T1")).thenReturn(List.of(person(null, "ksiadz", "sternik"), person(null, "old_role", "old_function")));
        var result = service().get("T1");
        assertEquals("Ksiądz", result.entries().getFirst().roleLabel());
        assertEquals("Sternik", result.entries().getFirst().subroleLabel());
        assertEquals("old_role", result.entries().get(1).roleLabel());
        assertEquals("old_function", result.entries().get(1).subroleLabel());
    }

    @Test void unknownTurnusDoesNotFetchPersonalData() {
        when(turnuses.get("missing")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service().get("missing")).getStatusCode());
        verifyNoInteractions(registrations);
    }

    @Test void upstreamFailureIsNotAnEmptySuccessfulList() {
        when(turnuses.get("T1")).thenReturn(turnus(TurnusDefinition.SeasonType.WINTER));
        when(registrations.fetchTurnusList("T1")).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class, () -> service().get("T1")).getStatusCode());
    }

    @Test void qualificationLabelsUseTheCompositeSeasonMappingAndPreserveHistoricalCodesAndDetails() {
        when(turnuses.get("T1")).thenReturn(turnus(TurnusDefinition.SeasonType.SUMMER));
        var data = new AdditionalData(null, null, null, null, null, null,
                List.of(new Qualification("patent_zeglarski", null), new Qualification("inne", "Instruktor"),
                        new Qualification("historical", "Old qualification")), null);
        var staff = new TurnusListPerson("S1", "STAFF", "Jan", "Nowak", null, null, null,
                null, null, null, null, "ksiadz", "sternik", true, false, data);
        var participant = new TurnusListPerson("P1", "PARTICIPANT", "Anna", "Nowak", null, null, null,
                null, null, null, null, null, null, true, true, data);
        when(registrations.fetchTurnusList("T1")).thenReturn(List.of(staff, participant));
        var entries = service().get("T1").entries();
        assertEquals(List.of("Patent żeglarski", "Inne (jakie?): Instruktor", "historical: Old qualification"),
                entries.getFirst().qualificationLabels());
        assertTrue(entries.get(1).qualificationLabels().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> entries.getFirst().qualificationLabels().clear());
    }
}
