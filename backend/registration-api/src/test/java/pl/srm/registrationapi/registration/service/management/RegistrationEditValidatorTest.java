package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
