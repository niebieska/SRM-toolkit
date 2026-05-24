package pl.srm.registrationapi.registration.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.service.PeselUtils;
import pl.srm.registrationapi.registration.util.PeselHasher;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationParserTest {

    private final RegistrationParser parser = new RegistrationParser(new ObjectMapper(), new PeselUtils(), new PeselHasher());

    @Test
    void parsesNestedParticipantPayloadIntoContext() throws Exception {
        String payload = """
                {
                  "turnusCode": "ZAGLE26T1",
                  "person": { "pesel": "10210112312", "isAdult": false },
                  "guardian": { "firstName": "Anna" },
                  "consents": { "dataProcessing": true }
                }
                """;

        RegistrationContext context = parser.parse(payload);

        assertEquals("ZAGLE26T1", context.turnusCode());
        assertEquals("10210112312", context.pesel());
        assertEquals(64, context.key().length());
        assertTrue(context.isMinor());
        assertTrue(context.hasGuardian());
        assertTrue(context.hasConsent1());
    }
}
