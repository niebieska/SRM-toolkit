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

    @Test void additionalColumnsSelectTheCorrectContactAndOnlyDeclaredQualifications() {
        String payload = """
                {"person":{"contact":{"email":"person@example.org"}},
                 "guardian":{"firstName":"Maria","lastName":"Nowak","relation":"matka",
                   "contact":{"email":"guardian@example.org"}},
                 "ice":{"firstName":"Jan","lastName":"Nowak","relation":"inna","relationOther":"Brat"},
                 "certificates":{"patent_zeglarski":true,"inne":true,"not_selected":false},
                 "certificateDetails":{"inne":"Instruktor"},"consents":{"imageUsage":false}}
                """;
        var staff = registration("STAFF", payload);
        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(staff));
        var adult = service.accepted("T1").getFirst().additionalData();
        assertEquals("person@example.org", adult.email());
        assertEquals("Jan", adult.contactFirstName());
        assertEquals("inna", adult.contactRelation());
        assertEquals("Brat", adult.contactRelationOther());
        assertEquals("guardian@example.org", adult.guardianEmail());
        assertEquals(Boolean.FALSE, adult.imageConsent());
        assertEquals(2, adult.qualifications().size());
        assertEquals("Instruktor", adult.qualifications().get(1).detail());
        staff.setMinor(true);
        var minor = service.accepted("T1").getFirst().additionalData();
        assertEquals("Maria", minor.contactFirstName());
        assertEquals("matka", minor.contactRelation());
        assertNull(minor.contactRelationOther());
        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("PARTICIPANT", payload)));
        assertTrue(service.accepted("T1").getFirst().additionalData().qualifications().isEmpty());
    }

    @Test void missingHistoricalAdditionalDataDoesNotInventConsentOrContact() {
        for (String payload : List.of("{}", "{\"consents\":{\"imageUsage\":\"false\"}}")) {
            when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("STAFF", payload)));
            var extra = service.accepted("T1").getFirst().additionalData();
            assertNull(extra.imageConsent());
            assertNull(extra.contactFirstName());
            assertNull(extra.email());
            assertTrue(extra.qualifications().isEmpty());
        }
    }

    @Test void exposesOnlyHealthDetailsAndAddsFamilyAndOrganizationalDataForStaff() {
        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("STAFF", """
                {"person":{"motherName":"Maria","fatherName":"Jan","birthSurname":"Kowalska"},
                 "health":{"q1":{"answer":"tak","detail":"Astma"},
                   "q2":{"answer":"nie","detail":""},
                   "additionalInfo":{"value":"Późniejszy przyjazd"}}}
                """)));

        var additional = service.accepted("T1").getFirst().additionalData();

        assertEquals("Astma", additional.healthDetails().chronicConditionDetails());
        assertNull(additional.healthDetails().dietaryDetails());
        assertNull(additional.healthDetails().specialNeedsDetails());
        assertEquals("Maria", additional.staffDetails().motherName());
        assertEquals("Jan", additional.staffDetails().fatherName());
        assertEquals("Kowalska", additional.staffDetails().birthSurname());
        assertEquals("Późniejszy przyjazd", additional.staffDetails().organizationalInfo());

        when(repository.findByTurnusCodeAndStatus("T1", "ACCEPTED")).thenReturn(List.of(registration("PARTICIPANT", """
                {"health":{"q1":{"answer":"tak","detail":"Alergia"},
                  "q2":{"answer":"tak","detail":"Orzechy"},
                  "q3":{"answer":"tak","detail":"Potrzeba spokojnego miejsca"}}}
                """)));
        additional = service.accepted("T1").getFirst().additionalData();
        assertEquals("Alergia", additional.healthDetails().chronicConditionDetails());
        assertEquals("Orzechy", additional.healthDetails().dietaryDetails());
        assertEquals("Potrzeba spokojnego miejsca", additional.healthDetails().specialNeedsDetails());
        assertNull(additional.staffDetails());
    }
}
