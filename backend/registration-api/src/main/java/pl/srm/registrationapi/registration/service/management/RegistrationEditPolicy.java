package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Defines which payload fields can be edited and their field-level constraints. */
@Component
final class RegistrationEditPolicy {
    static final Set<String> BASE_FIELDS = Set.of(
            "person.firstName", "person.lastName", "person.contact.email", "person.contact.phone",
            "address.street", "address.houseNumber", "address.postalCode", "address.city");
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "person.firstName", "person.lastName", "person.contact.email", "person.contact.phone",
            "address.street", "address.houseNumber", "address.postalCode", "address.city",
            "person.motherName", "person.fatherName", "person.birthSurname",
            "guardian.firstName", "guardian.lastName", "guardian.names", "guardian.contact.email",
            "guardian.contact.phone", "ice.firstName", "ice.lastName", "ice.phone", "ice.relation");
    private static final Map<String, Integer> MAX_LENGTHS = Map.ofEntries(
            Map.entry("person.firstName", 100), Map.entry("person.lastName", 150),
            Map.entry("person.contact.email", 254), Map.entry("person.contact.phone", 16),
            Map.entry("address.street", 200), Map.entry("address.houseNumber", 30),
            Map.entry("address.postalCode", 6), Map.entry("address.city", 150),
            Map.entry("address.guardianAddress.street", 200), Map.entry("address.guardianAddress.houseNumber", 30),
            Map.entry("address.guardianAddress.postalCode", 6), Map.entry("address.guardianAddress.city", 150),
            Map.entry("person.motherName", 100), Map.entry("person.fatherName", 100),
            Map.entry("person.birthSurname", 150),
            Map.entry("guardian.firstName", 100), Map.entry("guardian.lastName", 150),
            Map.entry("guardian.names", 500), Map.entry("guardian.contact.email", 254),
            Map.entry("guardian.contact.phone", 16),
            Map.entry("ice.firstName", 100), Map.entry("ice.lastName", 150),
            Map.entry("ice.phone", 16), Map.entry("ice.relationOther", 200),
            Map.entry("health.q1.detail", 4000), Map.entry("health.q2.detail", 4000),
            Map.entry("health.q3.detail", 4000), Map.entry("health.additionalInfo.value", 4000),
            Map.entry("health.additionalInfo", 4000));

    Set<String> editableFields(Registration registration, JsonNode payload) {
        Set<String> fields = new HashSet<>(BASE_FIELDS);
        boolean staff = isStaff(registration);
        if (staff) fields.addAll(Set.of("person.motherName", "person.fatherName", "person.birthSurname"));
        if (registration.isMinor()) {
            fields.addAll(Set.of("guardian.firstName", "guardian.lastName", "guardian.relation", "guardian.names",
                    "guardian.contact.email", "guardian.contact.phone"));
            if (!staff) fields.addAll(Set.of("address.sameAddress", "address.guardianAddress.street",
                    "address.guardianAddress.houseNumber", "address.guardianAddress.postalCode", "address.guardianAddress.city"));
        } else {
            fields.addAll(Set.of("ice.firstName", "ice.lastName", "ice.relation", "ice.relationOther", "ice.phone"));
        }
        int questionCount = staff ? 2 : 3;
        for (int i = 1; i <= questionCount; i++) {
            String question = "health.q" + i;
            if (payload.path("health").has("q" + i)) {
                fields.add(question + ".answer");
                fields.add(question + ".detail");
            }
        }
        JsonNode additionalInfo = payload.path("health").path("additionalInfo");
        if (additionalInfo.isObject() && additionalInfo.has("value")) {
            fields.add("health.additionalInfo.value");
        } else if (additionalInfo.isTextual()) {
            fields.add("health.additionalInfo");
        }
        return fields;
    }

    boolean isRequired(String path) {
        return REQUIRED_FIELDS.contains(path);
    }

    int maxLength(String path) {
        return MAX_LENGTHS.getOrDefault(path, 500);
    }

    boolean isStaff(Registration registration) {
        return "STAFF".equals(registration.getRegistrationType());
    }
}
