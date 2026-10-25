package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.List;
import java.util.Set;

/** Validates individual edits and the resulting registration payload. */
@Component
final class RegistrationEditValidator {
    private static final Set<String> PARTICIPANT_ADDRESS_FIELDS = Set.of(
            "address.street", "address.houseNumber", "address.postalCode", "address.city");
    private final RegistrationEditPolicy policy;

    RegistrationEditValidator(RegistrationEditPolicy policy) {
        this.policy = policy;
    }

    void validateValue(String path, JsonNode value) {
        if ("address.sameAddress".equals(path)) {
            if (value == null || !value.isBoolean()) throw invalid("Wybierz, czy adres opiekuna jest taki sam.");
            return;
        }
        if (value == null || value.isNull()) {
            if (policy.isRequired(path)) throw invalid("To pole jest wymagane: " + path + ".");
            return;
        }
        if (!value.isTextual()) throw invalid("Nieprawidłowa wartość pola: " + path + ".");
        String text = value.asText();
        if (text.length() > policy.maxLength(path)) throw invalid("Wartość pola jest za długa: " + path + ".");
        if (policy.isRequired(path) && text.isBlank()) throw invalid("To pole jest wymagane: " + path + ".");
        if (path.endsWith(".answer") && !Set.of("tak", "nie").contains(text)) throw invalid("Odpowiedź musi mieć wartość Tak albo Nie.");
        if (path.endsWith(".relation") && !Set.of("matka", "ojciec", "opiekun_prawny", "inna").contains(text))
            throw invalid("Nieprawidłowa relacja.");
        if ("guardian.relation".equals(path) && "inna".equals(text)) throw invalid("Nieprawidłowa relacja opiekuna.");
        if (path.endsWith(".email") && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail.");
        if (path.endsWith(".phone") && !text.matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu z prefiksem kraju.");
        if (path.endsWith(".postalCode") && !text.matches("[0-9]{2}-[0-9]{3}")) throw invalid("Podaj kod pocztowy w formacie XX-XXX.");
    }

    void validateResult(Registration registration, ObjectNode payload, Set<String> changedFields) {
        if (changedFields.stream().anyMatch(path -> path.startsWith("address.guardianAddress."))
                && payload.path("address").path("sameAddress").asBoolean(false)) {
            throw invalid("Adres opiekuna jest oznaczony jako taki sam jak adres uczestnika.");
        }

        for (String path : RegistrationEditPolicy.BASE_FIELDS) {
            JsonNode node = at(payload, path);
            if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane pole: " + path + ".");
        }
        if (!at(payload, "person.contact.email").asText().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail.");
        if (!at(payload, "person.contact.phone").asText().matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu z prefiksem kraju.");
        if (!at(payload, "address.postalCode").asText().matches("[0-9]{2}-[0-9]{3}")) throw invalid("Podaj kod pocztowy w formacie XX-XXX.");

        if (policy.isStaff(registration)) {
            for (String path : List.of("person.motherName", "person.fatherName", "person.birthSurname")) {
                JsonNode node = at(payload, path);
                if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane pole: " + path + ".");
            }
        }

        if (registration.isMinor()) validateGuardian(payload, policy.isStaff(registration));
        else validateEmergencyContact(payload);
        validateHealth(registration, payload);
    }

    private void validateGuardian(ObjectNode payload, boolean staff) {
        for (String path : List.of("guardian.firstName", "guardian.lastName", "guardian.relation", "guardian.names", "guardian.contact.email", "guardian.contact.phone")) {
            JsonNode node = at(payload, path);
            if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane pole opiekuna: " + path + ".");
        }
        if (!Set.of("matka", "ojciec", "opiekun_prawny").contains(at(payload, "guardian.relation").asText()))
            throw invalid("Nieprawidłowa relacja opiekuna.");
        if (!at(payload, "guardian.contact.email").asText().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail opiekuna.");
        if (!at(payload, "guardian.contact.phone").asText().matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu opiekuna.");
        if (!staff && !payload.path("address").path("sameAddress").asBoolean(true)) {
            for (String field : PARTICIPANT_ADDRESS_FIELDS) {
                String path = field.replace("address.", "address.guardianAddress.");
                JsonNode node = at(payload, path);
                if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij adres opiekuna albo zaznacz, że jest taki sam.");
                if (path.endsWith(".postalCode") && !node.asText().matches("[0-9]{2}-[0-9]{3}"))
                    throw invalid("Podaj kod pocztowy opiekuna w formacie XX-XXX.");
            }
        }
    }

    private void validateEmergencyContact(ObjectNode payload) {
        for (String path : List.of("ice.firstName", "ice.lastName", "ice.phone", "ice.relation")) {
            JsonNode node = at(payload, path);
            if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane dane kontaktu awaryjnego.");
        }
        if (!Set.of("matka", "ojciec", "opiekun_prawny", "inna").contains(at(payload, "ice.relation").asText()))
            throw invalid("Nieprawidłowa relacja kontaktu awaryjnego.");
        if ("inna".equals(at(payload, "ice.relation").asText())) {
            JsonNode relationOther = at(payload, "ice.relationOther");
            if (relationOther == null || !relationOther.isTextual() || relationOther.asText().isBlank()) throw invalid("Doprecyzuj relację kontaktu awaryjnego.");
        }
    }

    private void validateHealth(Registration registration, ObjectNode payload) {
        int count = policy.isStaff(registration) ? 2 : 3;
        for (int i = 1; i <= count; i++) {
            if (at(payload, "health.q" + i) == null) continue; // Older records may predate later questions.
            JsonNode answer = at(payload, "health.q" + i + ".answer");
            if (answer == null || !answer.isTextual() || !Set.of("tak", "nie").contains(answer.asText())) throw invalid("Uzupełnij odpowiedź w kwestionariuszu.");
            JsonNode detail = at(payload, "health.q" + i + ".detail");
            if ("tak".equals(answer.asText()) && (detail == null || !detail.isTextual() || detail.asText().isBlank()))
                throw invalid("Podaj szczegóły odpowiedzi Tak w kwestionariuszu.");
        }
    }

    private JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) current = current == null ? null : current.path(part);
        return current == null || current.isMissingNode() ? null : current;
    }

    private static RegistrationException invalid(String message) {
        return new RegistrationException("INVALID_REQUEST", message);
    }
}
