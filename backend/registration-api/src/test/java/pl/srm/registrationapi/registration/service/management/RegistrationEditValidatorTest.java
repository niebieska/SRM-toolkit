package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrationEditValidatorTest {
    private final RegistrationEditValidator validator = new RegistrationEditValidator(new RegistrationEditPolicy());
    private final ObjectMapper mapper = new ObjectMapper();
    private final Registration registration = new Registration();

    RegistrationEditValidatorTest() {
        registration.setRegistrationType("PARTICIPANT");
    }

    @Test
    void rejectsNonBooleanSameAddressValues() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> validator.validateValue(registration, "address.sameAddress", mapper.valueToTree("true")));

        assertEquals("INVALID_REQUEST", error.getCode());
    }

    @Test
    void rejectsOverlongTextAccordingToFieldPolicy() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> validator.validateValue(registration, "person.firstName", mapper.valueToTree("A".repeat(101))));

        assertEquals("INVALID_REQUEST", error.getCode());
    }

    @Test
    void permitsNullForOptionalDetails() {
        validator.validateValue(registration, "health.q1.detail", mapper.getNodeFactory().nullNode());
    }

    @Test
    void permitsBlankEmailForBiuroStaffButKeepsItRequiredForFormularzStaff() throws Exception {
        Registration biuroStaff = staffRegistration(RegistrationSource.BIURO);
        Registration formStaff = staffRegistration(RegistrationSource.FORMULARZ);

        assertDoesNotThrow(() -> validator.validateResult(biuroStaff,
                (ObjectNode) mapper.readTree(staffPayload()), Set.of()));
        assertThrows(RegistrationException.class,
                () -> validator.validateResult(formStaff, (ObjectNode) mapper.readTree(staffPayload()), Set.of()));
    }

    private Registration staffRegistration(RegistrationSource source) {
        return new Registration("REG-S-T1-1", "STAFF", "T1", "hash", false, "ACCEPTED", null,
                staffPayload(), null, null, source);
    }

    private String staffPayload() {
        return """
                {"person":{"firstName":"Anna","lastName":"Nowak","motherName":"Ewa",
                  "fatherName":"Jan","birthSurname":"Kowalska",
                  "contact":{"email":"","phone":"+48123456789"}},
                 "address":{"street":"Polna","houseNumber":"1","postalCode":"00-001","city":"Warszawa"},
                 "ice":{"firstName":"Ewa","lastName":"Nowak","phone":"+48123456789","relation":"inna","relationOther":"żona"},
                 "health":{"q1":{"answer":"nie","detail":""},"q2":{"answer":"nie","detail":""}}}
                """;
    }
}
