package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class StatusNotificationServiceTest {
    private final EmailServiceClient emailClient = mock(EmailServiceClient.class);
    private final StatusNotificationService service = new StatusNotificationService(emailClient, new ObjectMapper());

    @Test
    void acceptedParticipantGetsCardMetadataAndMinorEmailGoesToGuardian() {
        assertCardMetadata("PARTICIPANT", false, true, List.of("participant@example.test"));
        assertCardMetadata("PARTICIPANT", true, true, List.of("guardian@example.test"));
    }

    @Test
    void staffAndGuardianReceiveMinorStaffStatusUpdates() {
        assertCardMetadata("STAFF", false, false, List.of("participant@example.test"));
        assertCardMetadata("STAFF", true, true,
                List.of("participant@example.test", "guardian@example.test"));
    }

    @Test
    void minorStaffWithSharedEmailAddressReceivesOnlyOneStatusUpdate() {
        Registration registration = new Registration("REG-1", "STAFF", "T1", "hash", true,
                "ACCEPTED", null, """
                {"person":{"firstName":"Jan","lastName":"Nowak","contact":{"email":"participant@example.test"}},
                 "guardian":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"participant@example.test"}}}
                """, null, null);

        service.sendStatusUpdate(registration);

        verify(emailClient, times(1)).sendEmail(eq("participant@example.test"), eq("status-update"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void minorCandidateAnimatorAndGuardianBothReceiveDepositInstructions() {
        Registration registration = new Registration("REG-1", "STAFF", "T1", "hash", true,
                "ACCEPTED", null, """
                {"person":{"firstName":"Jan","lastName":"Nowak","contact":{"email":"participant@example.test"}},
                 "guardian":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"guardian@example.test"}},
                 "role":"animator","subrole":"kandydat_na_animatora"}
                """, null, null);

        service.sendStatusUpdate(registration);

        ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailClient, times(2)).sendEmail(recipients.capture(),
                eq("status-update"), variables.capture());
        assertEquals(List.of("participant@example.test", "guardian@example.test"), recipients.getAllValues());
        assertEquals(List.of("true", "true"), variables.getAllValues().stream()
                .map(value -> value.get("requiresDeposit")).toList());
    }

    private void assertCardMetadata(String type, boolean minor, boolean expectedCard, List<String> expectedRecipients) {
        String payload = """
                {"person":{"firstName":"Jan","lastName":"Nowak","contact":{"email":"participant@example.test"}},
                 "guardian":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"guardian@example.test"}}}
                """;
        Registration registration = new Registration("REG-1", type, "T1", "hash", minor,
                "ACCEPTED", null, payload, null, null);

        clearInvocations(emailClient);
        service.sendStatusUpdate(registration);

        ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailClient, times(expectedRecipients.size())).sendEmail(recipients.capture(),
                eq("status-update"), variables.capture());
        assertEquals(expectedRecipients, recipients.getAllValues());
        for (int i = 0; i < variables.getAllValues().size(); i++) {
            Map<String, String> sentVariables = variables.getAllValues().get(i);
            assertEquals(type, sentVariables.get("registrationType"));
            assertEquals(Boolean.toString(minor), sentVariables.get("isMinor"));
            assertEquals(Boolean.toString(expectedCard), sentVariables.get("includeParticipantCard"));
            assertEquals("Jan Nowak", sentVariables.get("registeredName"));
        }
    }
}
