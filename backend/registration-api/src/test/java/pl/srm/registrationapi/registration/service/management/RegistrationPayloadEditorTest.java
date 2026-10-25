package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationPayloadEditorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RegistrationEditPolicy policy = new RegistrationEditPolicy();
    private final RegistrationEditValidator validator = new RegistrationEditValidator(policy);
    private final RegistrationPayloadEditor editor = new RegistrationPayloadEditor(mapper, policy, validator);

    @Test
    void appliesAllowedChangeAndReturnsSortedChangedFields() throws Exception {
        Registration registration = adultRegistration(adultPayload());

        var result = editor.apply(registration, Map.of(
                "person.lastName", "Kowalska",
                "person.firstName", "Alicja"));
        JsonNode updated = mapper.readTree(result.payloadJson());

        assertEquals("Alicja", updated.at("/person/firstName").asText());
        assertEquals("Kowalska", updated.at("/person/lastName").asText());
        assertEquals(java.util.List.of("person.firstName", "person.lastName"), result.changedFields());
    }

    @Test
    void noOpLeavesPayloadAndAuditFieldsUnchanged() {
        String originalPayload = adultPayload();
        Registration registration = adultRegistration(originalPayload);

        var result = editor.apply(registration, Map.of("person.firstName", "Anna"));

        assertFalse(result.hasChanges());
        assertEquals(originalPayload, result.payloadJson());
    }

    @Test
    void changingHealthAnswerToNoClearsItsDetails() throws Exception {
        Registration registration = adultRegistration(adultPayload());

        var result = editor.apply(registration, Map.of("health.q1.answer", "nie"));
        JsonNode updated = mapper.readTree(result.payloadJson());

        assertEquals("nie", updated.at("/health/q1/answer").asText());
        assertTrue(updated.at("/health/q1/detail").isNull());
        assertEquals(java.util.List.of("health.q1.answer", "health.q1.detail"), result.changedFields());
    }

    @Test
    void changingEmergencyRelationClearsItsOtherDescription() throws Exception {
        Registration registration = adultRegistration(adultPayload());

        var result = editor.apply(registration, Map.of("ice.relation", "matka"));
        JsonNode updated = mapper.readTree(result.payloadJson());

        assertEquals("matka", updated.at("/ice/relation").asText());
        assertTrue(updated.at("/ice/relationOther").isNull());
        assertEquals(java.util.List.of("ice.relation", "ice.relationOther"), result.changedFields());
    }

    @Test
    void rejectsProtectedFieldWithoutMutatingStoredPayload() {
        Registration registration = adultRegistration(adultPayload());
        String originalPayload = registration.getPayload();

        RegistrationException error = assertThrows(RegistrationException.class,
                () -> editor.apply(registration, Map.of("person.pesel", "12345678901")));

        assertEquals("INVALID_REQUEST", error.getCode());
        assertEquals(originalPayload, registration.getPayload());
    }

    @Test
    void rejectsGuardianAddressChangeWhenMarkedSameAsParticipantAddress() {
        String originalPayload = minorPayload();
        Registration registration = new Registration("REG-MINOR", "PARTICIPANT", "T1", "hash", true,
                "NEW", null, originalPayload, null, null);

        RegistrationException error = assertThrows(RegistrationException.class,
                () -> editor.apply(registration, Map.of("address.guardianAddress.city", "Kraków")));

        assertEquals("INVALID_REQUEST", error.getCode());
        assertEquals(originalPayload, registration.getPayload());
    }

    private Registration adultRegistration(String payload) {
        return new Registration("REG-ADULT", "PARTICIPANT", "T1", "hash", false, "NEW", null,
                payload, null, null);
    }

    private String adultPayload() {
        return """
                {"person":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"anna@example.org","phone":"+48123456789"}},
                 "address":{"street":"Leśna","houseNumber":"1","postalCode":"00-001","city":"Warszawa"},
                 "ice":{"firstName":"Jan","lastName":"Nowak","relation":"inna","relationOther":"mąż","phone":"+48123456789"},
                 "health":{"q1":{"answer":"tak","detail":"Astma"},"q2":{"answer":"nie","detail":null},"q3":{"answer":"nie","detail":null}}}
                """;
    }

    private String minorPayload() {
        return """
                {"person":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"anna@example.org","phone":"+48123456789"}},
                 "address":{"street":"Leśna","houseNumber":"1","postalCode":"00-001","city":"Warszawa","sameAddress":true,
                   "guardianAddress":{"street":"Leśna","houseNumber":"1","postalCode":"00-001","city":"Warszawa"}},
                 "guardian":{"firstName":"Jan","lastName":"Nowak","relation":"matka","names":"Jan Nowak","contact":{"email":"jan@example.org","phone":"+48123456789"}},
                 "health":{"q1":{"answer":"nie","detail":null},"q2":{"answer":"nie","detail":null},"q3":{"answer":"nie","detail":null}}}
                """;
    }
}
