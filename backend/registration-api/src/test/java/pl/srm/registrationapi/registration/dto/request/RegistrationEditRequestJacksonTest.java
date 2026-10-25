package pl.srm.registrationapi.registration.dto.request;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegistrationEditRequestJacksonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesEditChangesWithJackson3() throws Exception {
        String json = """
                {
                  "expectedVersion": 7,
                  "changes": {
                    "person.firstName": "Anna",
                    "health.q1.answer": "tak",
                    "health.q1.detail": null
                  },
                  "actorAccountId": "d4b3a65e-9c33-48ce-a34a-fbc71be7e197",
                  "actorUsername": "operator"
                }
                """;

        RegistrationEditRequest request = mapper.readValue(json, RegistrationEditRequest.class);

        assertEquals(7L, request.expectedVersion());
        assertEquals("Anna", request.changes().get("person.firstName"));
        assertEquals("tak", request.changes().get("health.q1.answer"));
        assertNull(request.changes().get("health.q1.detail"));
        assertEquals("d4b3a65e-9c33-48ce-a34a-fbc71be7e197", request.actorAccountId());
        assertEquals("operator", request.actorUsername());
    }
}
