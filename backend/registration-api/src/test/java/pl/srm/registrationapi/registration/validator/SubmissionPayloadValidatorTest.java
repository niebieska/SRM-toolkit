package pl.srm.registrationapi.registration.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionPayloadValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SubmissionPayloadValidator validator = new SubmissionPayloadValidator(mapper);
    private final RegistrationContext adult = new RegistrationContext("T", "90010112349", "hash", false, false, true);
    private final RegistrationContext minor = new RegistrationContext("T", "10210112312", "hash", true, true, true);

    private ObjectNode payload(boolean isMinor) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"person":{"firstName":"Jan","lastName":"Testowy","gender":"male","isAdult":true,
                  "contact":{"email":"test@example.org","phone":"+48123456789"}},
                 "guardian":{"firstName":"Anna","lastName":"Testowa","relation":"matka","names":"Anna Testowa",
                  "contact":{"email":"guardian@example.org","phone":"+48987654321"}},
                 "ice":{"firstName":"Anna","lastName":"Testowa","relation":"inna","relationOther":"Przyjaciel","phone":"+48987654321"},
                 "address":{"street":"Testowa","houseNumber":"1","postalCode":"00-001","city":"Testowo","sameAddress":true},
                 "health":{"q1":{"answer":"nie"},"q2":{"answer":"nie"},"q3":{"answer":"nie"}},
                 "consents":{"dataProcessing":true,"regulations":true,"truthDeclaration":true,"imageUsage":false},
                 "role":"sternik","subrole":"","certificates":{},"certificateDetails":{}}
                """);
        ((ObjectNode) root.path("person")).put("isAdult", !isMinor);
        ((ObjectNode) root.path("person")).put("gender", isMinor ? "male" : "female");
        if (isMinor) root.put("role", "sternik_z_opiekunem");
        return root;
    }

    @Test void acceptsAllFourPathsWithOptionalImageConsentDeclined() throws Exception {
        for (boolean isMinor : new boolean[]{false, true}) {
            for (var type : RegistrationType.values()) {
                assertDoesNotThrow(() -> validator.validate(payload(isMinor).toString(), isMinor ? minor : adult, type));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"firstName", "lastName", "contact", "isAdult", "gender"})
    void rejectsMissingPersonFields(String field) throws Exception {
        var root = payload(false); ((ObjectNode) root.path("person")).remove(field);
        rejects(root, adult, RegistrationType.PARTICIPANT);
    }

    @ParameterizedTest @ValueSource(strings = {"firstName", "lastName", "contact", "relation", "names"})
    void rejectsIncompleteGuardian(String field) throws Exception {
        var root = payload(true); ((ObjectNode) root.path("guardian")).remove(field);
        rejects(root, minor, RegistrationType.PARTICIPANT);
    }

    @ParameterizedTest @ValueSource(strings = {"dataProcessing", "regulations", "truthDeclaration"})
    void rejectsMissingMandatoryConsents(String field) throws Exception {
        var root = payload(false); ((ObjectNode) root.path("consents")).put(field, false);
        var ex = assertThrows(RegistrationException.class, () -> validator.validate(root.toString(), adult, RegistrationType.PARTICIPANT));
        assertEquals("MISSING_CONSENTS", ex.getCode());
    }

    @Test void rejectsInvalidContactAndMissingOtherRelationship() throws Exception {
        var root = payload(false);
        ((ObjectNode) root.path("person").path("contact")).put("email", "bad-email");
        rejects(root, adult, RegistrationType.STAFF);
        root = payload(false); ((ObjectNode) root.path("ice")).remove("relationOther");
        rejects(root, adult, RegistrationType.PARTICIPANT);
    }

    @Test void requiresSeparateGuardianAddressForMinorParticipantOnly() throws Exception {
        var root = payload(true); ((ObjectNode) root.path("address")).put("sameAddress", false);
        rejects(root, minor, RegistrationType.PARTICIPANT);
        assertDoesNotThrow(() -> validator.validate(root.toString(), minor, RegistrationType.STAFF));
        ((ObjectNode) root.path("address")).set("guardianAddress", root.path("address").deepCopy());
        assertDoesNotThrow(() -> validator.validate(root.toString(), minor, RegistrationType.PARTICIPANT));
    }

    @Test void validatesHealthAnswersAndPositiveAnswerDetails() throws Exception {
        var root = payload(false); ((ObjectNode) root.path("health").path("q1")).put("answer", "tak");
        rejects(root, adult, RegistrationType.PARTICIPANT);
        ((ObjectNode) root.path("health").path("q1")).put("detail", "Informacja testowa");
        assertDoesNotThrow(() -> validator.validate(root.toString(), adult, RegistrationType.PARTICIPANT));
    }

    @Test void validatesRoleAgeAndSubroleCombinations() throws Exception {
        var root = payload(true); root.put("role", "animator");
        rejects(root, minor, RegistrationType.STAFF);
        root = payload(false); root.put("role", "ksiadz");
        rejects(root, adult, RegistrationType.STAFF);
        root.put("subrole", "prowadzacy");
        final var valid = root;
        assertDoesNotThrow(() -> validator.validate(valid.toString(), adult, RegistrationType.STAFF));
        root.put("subrole", "animator"); rejects(root, adult, RegistrationType.STAFF);
    }

    @Test void requiresDetailsOnlyWhenOtherCertificateSelected() throws Exception {
        var root = payload(false); ((ObjectNode) root.path("certificates")).put("inne", true);
        rejects(root, adult, RegistrationType.STAFF);
        ((ObjectNode) root.path("certificateDetails")).put("inne", "Kurs testowy");
        assertDoesNotThrow(() -> validator.validate(root.toString(), adult, RegistrationType.STAFF));
    }

    @Test void rejectsWrongJsonTypesAndExcessiveNames() throws Exception {
        var root = payload(false); ((ObjectNode) root.path("person")).put("firstName", 123);
        rejects(root, adult, RegistrationType.PARTICIPANT);
        ((ObjectNode) root.path("person")).put("firstName", "a".repeat(101));
        rejects(root, adult, RegistrationType.PARTICIPANT);
    }

    private void rejects(ObjectNode root, RegistrationContext context, RegistrationType type) {
        assertThrows(RegistrationException.class, () -> validator.validate(root.toString(), context, type));
    }
}
