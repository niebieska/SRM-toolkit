package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrationEditValidatorTest {
    private final RegistrationEditValidator validator = new RegistrationEditValidator(new RegistrationEditPolicy());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsNonBooleanSameAddressValues() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> validator.validateValue("address.sameAddress", mapper.valueToTree("true")));

        assertEquals("INVALID_REQUEST", error.getCode());
    }

    @Test
    void rejectsOverlongTextAccordingToFieldPolicy() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> validator.validateValue("person.firstName", mapper.valueToTree("A".repeat(101))));

        assertEquals("INVALID_REQUEST", error.getCode());
    }

    @Test
    void permitsNullForOptionalDetails() {
        validator.validateValue("health.q1.detail", mapper.getNodeFactory().nullNode());
    }
}
