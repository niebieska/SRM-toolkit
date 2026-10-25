package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.dto.request.RegistrationEditRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationEditHistory;
import pl.srm.registrationapi.registration.repository.RegistrationEditHistoryRepository;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationEditServiceTest {
    private static final String CODE = "REG-P-T1-1";
    private static final String ACTOR_ID = "d4b3a65e-9c33-48ce-a34a-fbc71be7e197";
    private final RegistrationRepository registrationRepository = mock(RegistrationRepository.class);
    private final RegistrationEditHistoryRepository historyRepository = mock(RegistrationEditHistoryRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final RegistrationEditPolicy policy = new RegistrationEditPolicy();
    private final RegistrationEditValidator validator = new RegistrationEditValidator(policy);
    private final RegistrationPayloadEditor payloadEditor = new RegistrationPayloadEditor(mapper, policy, validator);
    private final RegistrationEditService service = new RegistrationEditService(
            registrationRepository, historyRepository, payloadEditor, mapper);
    private Registration registration;

    @BeforeEach
    void setUp() {
        registration = new Registration(CODE, "PARTICIPANT", "T1", "hash", false, "NEW", null,
                validAdultPayload(), null, null);
        registration.setId(12L);
        when(registrationRepository.findByRegistrationCodeForUpdate(CODE)).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(registration)).thenReturn(registration);
    }

    @Test
    void updatesAllowedFieldAndPersistsAttributedChangedFieldAudit() throws Exception {
        service.update(CODE, request(0, Map.of("person.firstName", "Alicja")));

        JsonNode payload = mapper.readTree(registration.getPayload());
        assertEquals("Alicja", payload.at("/person/firstName").asText());
        assertNotNull(registration.getUpdatedAt());
        verify(registrationRepository).findByRegistrationCodeForUpdate(CODE);
        verify(registrationRepository).saveAndFlush(registration);

        var captor = org.mockito.ArgumentCaptor.forClass(RegistrationEditHistory.class);
        verify(historyRepository).save(captor.capture());
        RegistrationEditHistory history = captor.getValue();
        assertEquals(12L, history.getRegistrationId());
        assertEquals(ACTOR_ID, history.getActorAccountId());
        assertEquals("operator", history.getActorUsername());
        assertEquals(List.of("person.firstName"), mapper.readValue(history.getChangedFields(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
    }

    @Test
    void rejectsProtectedFieldsWithoutPersistingAnything() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> service.update(CODE, request(0, Map.of("person.pesel", "12345678901"))));

        assertEquals("INVALID_REQUEST", error.getCode());
        verifyNoInteractions(historyRepository);
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStaleVersionBeforeApplyingChanges() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> service.update(CODE, request(3, Map.of("person.firstName", "Alicja"))));

        assertEquals("EDIT_CONFLICT", error.getCode());
        verifyNoInteractions(historyRepository);
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidEmailWithoutPersistingAnything() {
        RegistrationException error = assertThrows(RegistrationException.class,
                () -> service.update(CODE, request(0, Map.of("person.contact.email", "invalid"))));

        assertEquals("INVALID_REQUEST", error.getCode());
        verifyNoInteractions(historyRepository);
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void unchangedValuesDoNotCreateAnEditOrAuditEntry() {
        service.update(CODE, request(0, Map.of("person.firstName", "Anna")));

        verify(registrationRepository).findByRegistrationCodeForUpdate(CODE);
        verify(registrationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void changingHealthAnswerToNoClearsDetailsAndAuditsBothFields() throws Exception {
        service.update(CODE, request(0, Map.of("health.q1.answer", "nie")));

        JsonNode payload = mapper.readTree(registration.getPayload());
        assertEquals("nie", payload.at("/health/q1/answer").asText());
        assertTrue(payload.at("/health/q1/detail").isNull());
        var captor = org.mockito.ArgumentCaptor.forClass(RegistrationEditHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(List.of("health.q1.answer", "health.q1.detail"), mapper.readValue(
                captor.getValue().getChangedFields(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
    }

    private RegistrationEditRequest request(long expectedVersion, Map<String, Object> changes) {
        return new RegistrationEditRequest(expectedVersion, changes, ACTOR_ID, "operator");
    }

    private String validAdultPayload() {
        return """
                {
                  "person":{"firstName":"Anna","lastName":"Nowak","contact":{"email":"anna@example.org","phone":"+48123456789"}},
                  "address":{"street":"Leśna","houseNumber":"1","postalCode":"00-001","city":"Warszawa"},
                  "ice":{"firstName":"Jan","lastName":"Nowak","relation":"inna","relationOther":"mąż","phone":"+48123456789"},
                  "health":{
                    "q1":{"answer":"tak","detail":"Astma"},
                    "q2":{"answer":"nie","detail":null},
                    "q3":{"answer":"nie","detail":null}
                  }
                }
                """;
    }
}
