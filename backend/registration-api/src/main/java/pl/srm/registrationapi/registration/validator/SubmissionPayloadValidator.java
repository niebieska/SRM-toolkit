package pl.srm.registrationapi.registration.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;

import java.util.Set;

/** Validates the existing JSON contract without changing stored historical payloads. */
@Component
public class SubmissionPayloadValidator {
    private final ObjectMapper mapper;
    private final pl.srm.registrationapi.season.StaffRoleValidator staffRoles;

    public SubmissionPayloadValidator(ObjectMapper mapper, pl.srm.registrationapi.season.StaffRoleValidator staffRoles) {
        this.mapper = mapper;
        this.staffRoles = staffRoles;
    }

    public void validate(String payload, RegistrationContext context, RegistrationType type, pl.srm.registrationapi.season.StaffCatalogue catalogue) {
        final JsonNode root;
        try { root = mapper.readTree(payload); }
        catch (Exception ex) { throw invalid("Nieprawidłowy format zgłoszenia."); }
        JsonNode person = root.path("person");
        name(person);
        contact(person.path("contact"), "person.contact");
        // These derived fields are used by Biuro; never accept a contradictory client value.
        boolean adult = !context.isMinor();
        if (!person.path("isAdult").isBoolean() || person.path("isAdult").asBoolean() != adult)
            throw invalid("Nieprawidłowa informacja o pełnoletności.");
        String gender = Character.getNumericValue(context.pesel().charAt(9)) % 2 == 0 ? "female" : "male";
        if (!gender.equals(person.path("gender").asText(""))) throw invalid("Płeć nie zgadza się z numerem PESEL.");
        if (context.isMinor()) {
            JsonNode guardian = root.path("guardian");
            name(guardian);
            contact(guardian.path("contact"), "guardian.contact");
            allowed(text(guardian, "relation", 30), Set.of("matka", "ojciec", "opiekun_prawny"), "Relacja opiekuna");
            text(guardian, "names", 500);
        } else {
            JsonNode ice = root.path("ice");
            name(ice);
            phone(ice, "phone");
            String relation = text(ice, "relation", 30);
            allowed(relation, Set.of("matka", "ojciec", "opiekun_prawny", "inna"), "Relacja kontaktu awaryjnego");
            if (relation.equals("inna")) text(ice, "relationOther", 200);
        }
        JsonNode address = root.path("address");
        address(address);
        if (type == RegistrationType.PARTICIPANT && context.isMinor()) {
            if (!address.path("sameAddress").isBoolean()) throw invalid("Określ adres opiekuna.");
            if (!address.path("sameAddress").asBoolean()) address(address.path("guardianAddress"));
        }
        JsonNode consents = root.path("consents");
        for (String consent : Set.of("dataProcessing", "regulations", "truthDeclaration")) {
            if (!consents.path(consent).isBoolean() || !consents.path(consent).asBoolean())
                throw new RegistrationException("MISSING_CONSENTS", "Wymagana zgoda lub oświadczenie: " + consent + ".");
        }
        if (!consents.path("imageUsage").isMissingNode() && !consents.path("imageUsage").isBoolean())
            throw invalid("Nieprawidłowa zgoda na wykorzystanie wizerunku.");
        JsonNode health = root.path("health");
        for (String question : type == RegistrationType.PARTICIPANT ? new String[]{"q1", "q2", "q3"} : new String[]{"q1", "q2"}) {
            JsonNode answer = health.path(question);
            String value = text(answer, "answer", 3);
            allowed(value, Set.of("tak", "nie"), "Odpowiedź na pytanie zdrowotne");
            if (value.equals("tak")) text(answer, "detail", 4000);
        }
        if (type == RegistrationType.STAFF) {
            text(root, "role", 50);
            staffRoles.validate(root, context.isMinor(), catalogue);
        }
    }

    private void name(JsonNode node) {
        text(node, "firstName", 100); text(node, "lastName", 150);
    }
    private void contact(JsonNode node, String label) {
        String email = text(node, "email", 254);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail (" + label + ").");
        phone(node, "phone");
    }
    private void phone(JsonNode node, String field) {
        if (!text(node, field, 16).matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu z prefiksem kraju.");
    }
    private void address(JsonNode node) {
        text(node, "street", 200); text(node, "houseNumber", 30); text(node, "city", 150);
        if (!text(node, "postalCode", 6).matches("[0-9]{2}-[0-9]{3}")) throw invalid("Podaj kod pocztowy w formacie XX-XXX.");
    }
    private String text(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max)
            throw invalid("Uzupełnij prawidłowo pole: " + field + " (maks. " + max + " znaków).");
        return value.asText().trim();
    }
    private void allowed(String value, Set<String> options, String label) {
        if (!options.contains(value)) throw invalid(label + ": nieprawidłowa wartość.");
    }
    private RegistrationException invalid(String message) { return new RegistrationException("INVALID_REQUEST", message); }
}
