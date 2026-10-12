package pl.srm.registrationapi.registration.service.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegistrationNotificationServiceTest {
    static final String PAYLOAD = """
            {"person":{"firstName":"Jan","lastName":"Nowak","contact":{"email":"person@example.test"}},
             "guardian":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"guardian@example.test"}},
             "role":"WYCHOWAWCA","subrole":"ZASTEPOWY"}
            """;
    final EmailServiceClient client = mock(EmailServiceClient.class);
    final RegistrationNotificationService notifications = new RegistrationNotificationService(client, new ObjectMapper());

    Turnus turnus(String code, String description) {
        return new Turnus(code, "Narty z Jezusem — nowa nazwa", description, "Gliczarów Górny",
                SeasonType.WINTER, 2027, LocalDate.of(2027, 1, 7), LocalDate.of(2027, 1, 14),
                14, 50, 10, true, true, true);
    }
    RegistrationContext context(boolean minor) {
        return new RegistrationContext("NEW2027", "test", "hash", minor, true, true);
    }
    void assertMetadata(Map<String, String> variables, String code, String description) {
        assertEquals(code, variables.get("turnusCode"));
        assertEquals("Narty z Jezusem — nowa nazwa", variables.get("turnusName"));
        assertEquals("07.01.2027", variables.get("turnusStartDate"));
        assertEquals("14.01.2027", variables.get("turnusEndDate"));
        assertEquals(description, variables.get("turnusDescription"));
        assertEquals("REG-1", variables.get("registrationCode"));
        assertEquals("NEW", variables.get("status"));
        assertEquals("Jan Nowak", variables.get("registeredName"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void participantMetadataAndRecipientComeFromValidatedTurnusAndMinorRule(boolean minor) {
        notifications.sendParticipantRegistrationConfirmation(PAYLOAD, context(minor), "REG-1", turnus("NEW2027", "Opis"));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(client).sendEmail(eq(minor ? "guardian@example.test" : "person@example.test"),
                eq("registration-confirmation-participant"), variables.capture());
        assertMetadata(variables.getValue(), "NEW2027", "Opis");
        assertEquals(minor ? "Anna Nowak" : "Jan Nowak", variables.getValue().get("recipientName"));
        assertEquals("PARTICIPANT", variables.getValue().get("registrationType"));
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void staffAndGuardianReceiveSameCatalogueMetadata(boolean minor) {
        notifications.sendStaffRegistrationConfirmation(PAYLOAD, context(minor), "REG-1", turnus("NEW2027", "Opis"));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        var recipients = ArgumentCaptor.forClass(String.class);
        verify(client, times(minor ? 2 : 1)).sendEmail(recipients.capture(),
                eq("registration-confirmation-staff"), variables.capture());
        assertEquals("person@example.test", recipients.getAllValues().getFirst());
        if (minor) assertEquals("guardian@example.test", recipients.getAllValues().get(1));
        for (var value : variables.getAllValues()) {
            assertMetadata(value, "NEW2027", "Opis");
            assertEquals("STAFF", value.get("registrationType"));
            assertEquals("WYCHOWAWCA - ZASTEPOWY", value.get("staffRole"));
        }
        assertEquals("Jan Nowak", variables.getAllValues().getFirst().get("recipientName"));
        if (minor) assertEquals("Anna Nowak", variables.getAllValues().get(1).get("recipientName"));
        verifyNoMoreInteractions(client);
    }

    @Test void nullDescriptionIsEmptyAndKnownCodeDoesNotUseFormerHardcodedValues() {
        var data = new RegistrationContext("ZAGLE26T2", "test", "hash", false, true, true);
        notifications.sendParticipantRegistrationConfirmation(PAYLOAD, data, "REG-1", turnus("ZAGLE26T2", null));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(client).sendEmail(anyString(), anyString(), variables.capture());
        assertMetadata(variables.getValue(), "ZAGLE26T2", "");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void emailFailureDoesNotFailAlreadySavedParticipantOrStaffRegistration(boolean staff) {
        var parser = mock(pl.srm.registrationapi.registration.parser.RegistrationParser.class);
        var provider = mock(pl.srm.registrationapi.turnus.service.TurnusProvider.class);
        var validator = mock(pl.srm.registrationapi.registration.validator.TurnusValidator.class);
        var validation = mock(RegistrationValidationService.class);
        var persistence = mock(RegistrationPersistenceService.class);
        var data = context(false);
        var selected = turnus("NEW2027", null);
        when(parser.parse(PAYLOAD)).thenReturn(data);
        when(provider.getByCode("NEW2027")).thenReturn(selected);
        doThrow(new IllegalStateException("SMTP unavailable")).when(client).sendEmail(anyString(), anyString(), anyMap());
        RegistrationService service;
        if (staff) {
            when(persistence.saveStaff(data, PAYLOAD)).thenReturn("REG-1");
            service = new StaffRegistrationService(parser, provider, validator, validation, persistence, notifications);
        } else {
            when(persistence.saveParticipant(data, PAYLOAD)).thenReturn("REG-1");
            service = new ParticipantRegistrationService(parser, provider, validator, validation, persistence, notifications);
        }
        assertEquals("REG-1", assertDoesNotThrow(() -> service.register(PAYLOAD)));
        verify(provider, times(1)).getByCode("NEW2027");
        verify(validator).validate(same(selected), eq(staff ? pl.srm.registrationapi.registration.model.RegistrationType.STAFF
                : pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT));
        if (staff) verify(persistence).saveStaff(data, PAYLOAD);
        else verify(persistence).saveParticipant(data, PAYLOAD);
        verifyNoMoreInteractions(persistence);
    }
}
