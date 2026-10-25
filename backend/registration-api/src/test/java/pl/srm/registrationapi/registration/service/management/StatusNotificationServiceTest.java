package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class StatusNotificationServiceTest {
    private final EmailServiceClient emailClient = mock(EmailServiceClient.class);
    private final StatusNotificationService service = new StatusNotificationService(emailClient, new ObjectMapper());

    @Test
    void acceptedParticipantGetsCardMetadataAndMinorEmailGoesToGuardian() {
        assertCardMetadata("PARTICIPANT", false, true, "participant@example.test");
        assertCardMetadata("PARTICIPANT", true, true, "guardian@example.test");
    }

    @Test
    void onlyMinorStaffGetsParticipantCardMetadata() {
        assertCardMetadata("STAFF", false, false, "participant@example.test");
        assertCardMetadata("STAFF", true, true, "guardian@example.test");
    }

    private void assertCardMetadata(String type, boolean minor, boolean expectedCard, String expectedRecipient) {
        String payload = """
                {"person":{"firstName":"Jan","lastName":"Nowak","contact":{"email":"participant@example.test"}},
                 "guardian":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"guardian@example.test"}}}
                """;
        Registration registration = new Registration("REG-1", type, "T1", "hash", minor,
                "ACCEPTED", null, payload, null, null);

        service.sendStatusUpdate(registration);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailClient).sendEmail(eq(expectedRecipient), eq("status-update"), variables.capture());
        assertEquals(type, variables.getValue().get("registrationType"));
        assertEquals(Boolean.toString(minor), variables.getValue().get("isMinor"));
        assertEquals(Boolean.toString(expectedCard), variables.getValue().get("includeParticipantCard"));
    }
}
