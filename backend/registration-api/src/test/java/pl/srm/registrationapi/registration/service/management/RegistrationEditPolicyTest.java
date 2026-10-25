package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.model.Registration;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationEditPolicyTest {
    private final RegistrationEditPolicy policy = new RegistrationEditPolicy();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void adultParticipantCanEditEmergencyContactButNotGuardianOrStaffFields() throws Exception {
        Registration registration = registration("PARTICIPANT", false);

        var fields = policy.editableFields(registration, mapper.readTree(payload()));

        assertTrue(fields.contains("ice.firstName"));
        assertFalse(fields.contains("guardian.firstName"));
        assertFalse(fields.contains("person.motherName"));
        assertFalse(fields.contains("person.pesel"));
        assertFalse(fields.contains("status"));
    }

    @Test
    void minorParticipantCanEditGuardianAndGuardianAddressButNotEmergencyContact() throws Exception {
        Registration registration = registration("PARTICIPANT", true);

        var fields = policy.editableFields(registration, mapper.readTree(payload()));

        assertTrue(fields.contains("guardian.firstName"));
        assertTrue(fields.contains("address.sameAddress"));
        assertTrue(fields.contains("address.guardianAddress.city"));
        assertFalse(fields.contains("ice.firstName"));
    }

    @Test
    void staffEditsIncludeParentNamesAndOnlyQuestionsPresentInTheirPayload() throws Exception {
        Registration registration = registration("STAFF", true);

        var fields = policy.editableFields(registration, mapper.readTree(payload()));

        assertTrue(fields.contains("person.motherName"));
        assertTrue(fields.contains("person.fatherName"));
        assertFalse(fields.contains("address.guardianAddress.city"));
        assertTrue(fields.contains("health.q1.answer"));
        assertTrue(fields.contains("health.q2.answer"));
        assertFalse(fields.contains("health.q3.answer"));
    }

    private Registration registration(String type, boolean minor) {
        return new Registration("REG-1", type, "T1", "hash", minor, "NEW", null, payload(), null, null);
    }

    private String payload() {
        return """
                {"person":{},"address":{"guardianAddress":{}},"health":{"q1":{},"q2":{},"q3":{}}}
                """;
    }
}
