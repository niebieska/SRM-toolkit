package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.service.management.TurnusListService;
import pl.srm.registrationapi.registration.util.PeselHelper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TurnusListServiceTest {
    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final TurnusListService service = new TurnusListService(repository, new ObjectMapper(), new PeselHelper());

    private Registration registration(String type, String payload) {
        return new Registration("R1", type, "T1", "hash", false, "ACCEPTED", null, payload, LocalDateTime.now(), null);
    }

    @Test void queriesOnlyAcceptedInSelectedTurnusAndReturnsExplicitFields() {
        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("STAFF", """
                {"person":{"firstName":" Jan ","lastName":"Kowalski","pesel":"44051401458",
                  "gender":"male","contact":{"phone":"123456789","email":"private@example.org"}},
                 "address":{"street":"Polna","houseNumber":"2","postalCode":"00-001","city":"Warszawa"},
                 "guardian":{"contact":{"phone":"987654321"}},"ice":{"phone":"111222333"},
                 "role":"ksiadz","subrole":"sternik","health":{"q1":{"detail":"not in list"}}}
                """)));
        var person = service.accepted("T1").getFirst();
        assertEquals("Jan", person.firstName());
        assertEquals(LocalDate.of(1944, 5, 14), person.birthDate());
        assertEquals("Warszawa", person.address().city());
        assertEquals("123456789", person.personPhone());
        assertEquals("987654321", person.guardianPhone());
        assertEquals("111222333", person.icePhone());
        assertEquals("ksiadz", person.role());
        assertEquals("sternik", person.subrole());
        assertFalse(person.incompleteData());
        verify(repository).findByTurnusCodeAndStatus("T1", "ACCEPTED");
        verifyNoMoreInteractions(repository);
    }

    @Test void missingHistoricalDataDoesNotInventBirthDateOrStaffRole() {
        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("PARTICIPANT", """
                {"person":{"firstName":"Anna","lastName":"Nowak","pesel":"44051401459"},
                 "role":"ksiadz","subrole":"sternik"}
                """)));
        var person = service.accepted("T1").getFirst();
        assertNull(person.birthDate());
        assertNull(person.guardianPhone());
        assertNull(person.role());
        assertNull(person.subrole());
        assertTrue(person.incompleteData());
    }

    @Test void malformedPayloadPreservesThePersonRowAndReportsIncompleteData() {
        for (String payload : List.of("broken", "[]", "null", "{}")) {
            when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("STAFF", payload)));
            var person = service.accepted("T1").getFirst();
            assertEquals("R1", person.registrationCode());
            assertNull(person.birthDate());
            assertTrue(person.incompleteData());
        }
    }

    @Test void emptyTurnusIsAnEmptyList() {
        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of());
        assertTrue(service.accepted("T1").isEmpty());
    }

    @Test void preservesStoredMinorStatusEvenWhenPayloadIsMissingOrContradictory() {
        for (String payload : List.of("broken", "{\"person\":{\"isAdult\":true}}")) {
            var registration = registration("PARTICIPANT", payload);
            registration.setMinor(true);
            when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration));
            assertTrue(service.accepted("T1").getFirst().minor());
        }
    }
}
