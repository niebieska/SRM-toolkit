package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.srm.registrationapi.registration.dto.request.RegistrationEditRequest;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationEditHistory;
import pl.srm.registrationapi.registration.repository.RegistrationEditHistoryRepository;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RegistrationEditService {
    private static final Set<String> ADDRESS_FIELDS = Set.of(
            "address.street", "address.houseNumber", "address.postalCode", "address.city");
    private static final Set<String> BASE_FIELDS = Set.of(
            "person.firstName", "person.lastName", "person.contact.email", "person.contact.phone",
            "address.street", "address.houseNumber", "address.postalCode", "address.city");
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

    private final RegistrationRepository registrationRepository;
    private final RegistrationEditHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public RegistrationEditService(RegistrationRepository registrationRepository,
                                   RegistrationEditHistoryRepository historyRepository,
                                   ObjectMapper objectMapper) {
        this.registrationRepository = registrationRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void update(String code, RegistrationEditRequest request) {
        Registration registration = registrationRepository.findByRegistrationCodeForUpdate(code)
                .orElseThrow(() -> new RegistrationException("REGISTRATION_NOT_FOUND", "Nie znaleziono zgłoszenia o podanym kodzie."));
        if (registration.getVersion() != request.expectedVersion()) {
            throw new RegistrationException("EDIT_CONFLICT", "Zgłoszenie zostało wcześniej zmienione. Odśwież szczegóły i spróbuj ponownie.");
        }

        ObjectNode payload;
        ObjectNode originalPayload;
        try {
            JsonNode parsed = objectMapper.readTree(registration.getPayload());
            if (!(parsed instanceof ObjectNode object)) throw invalid("Nieprawidłowe zapisane dane zgłoszenia.");
            payload = object.deepCopy();
            originalPayload = payload.deepCopy();
        } catch (RegistrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("Nie można odczytać zapisanych danych zgłoszenia.");
        }

        Set<String> allowed = allowedFields(registration, payload);
        TreeSet<String> changed = new TreeSet<>();
        for (var entry : request.changes().entrySet()) {
            String path = entry.getKey();
            if (!allowed.contains(path)) throw invalid("Nie można edytować pola: " + path + ".");
            JsonNode value = objectMapper.valueToTree(entry.getValue());
            validateValue(path, value);
            JsonNode current = at(payload, path);
            if (!sameNode(current, value)) {
                set(payload, path, value);
                changed.add(path);
            }
        }
        if (changed.isEmpty()) return;

        if (changed.stream().anyMatch(path -> path.startsWith("address.guardianAddress."))
                && payload.path("address").path("sameAddress").asBoolean(false)) {
            throw invalid("Adres opiekuna jest oznaczony jako taki sam jak adres uczestnika.");
        }

        normalizeHealthDetails(payload, changed);
        changed.removeIf(path -> sameNode(at(originalPayload, path), at(payload, path)));
        if (changed.isEmpty()) return;
        validateRequiredData(registration, payload);
        try {
            registration.setPayload(objectMapper.writeValueAsString(payload));
            LocalDateTime changedAt = LocalDateTime.now();
            registration.setUpdatedAt(changedAt);
            Registration saved = registrationRepository.saveAndFlush(registration);
            historyRepository.save(new RegistrationEditHistory(saved.getId(), changedAt,
                    request.actorAccountId().trim(), request.actorUsername().trim(), objectMapper.writeValueAsString(changed)));
        } catch (RegistrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not persist registration edit", ex);
        }
    }

    private Set<String> allowedFields(Registration registration, ObjectNode payload) {
        Set<String> fields = new HashSet<>(BASE_FIELDS);
        boolean staff = "STAFF".equals(registration.getRegistrationType());
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
        if (payload.path("health").path("additionalInfo").isObject()
                && payload.path("health").path("additionalInfo").has("value")) {
            fields.add("health.additionalInfo.value");
        } else if (payload.path("health").path("additionalInfo").isTextual()) {
            fields.add("health.additionalInfo");
        }
        return fields;
    }

    private void validateValue(String path, JsonNode value) {
        if ("address.sameAddress".equals(path)) {
            if (value == null || !value.isBoolean()) throw invalid("Wybierz, czy adres opiekuna jest taki sam.");
            return;
        }
        if (value == null || value.isNull()) {
            if (isRequired(path)) throw invalid("To pole jest wymagane: " + path + ".");
            return;
        }
        if (!value.isTextual()) throw invalid("Nieprawidłowa wartość pola: " + path + ".");
        String text = value.asText();
        if (text.length() > MAX_LENGTHS.getOrDefault(path, 500)) throw invalid("Wartość pola jest za długa: " + path + ".");
        if (isRequired(path) && text.isBlank()) throw invalid("To pole jest wymagane: " + path + ".");
        if (path.endsWith(".answer") && !Set.of("tak", "nie").contains(text)) throw invalid("Odpowiedź musi mieć wartość Tak albo Nie.");
        if (path.endsWith(".relation") && !Set.of("matka", "ojciec", "opiekun_prawny", "inna").contains(text))
            throw invalid("Nieprawidłowa relacja.");
        if ("guardian.relation".equals(path) && "inna".equals(text)) throw invalid("Nieprawidłowa relacja opiekuna.");
        if (path.endsWith(".email") && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail.");
        if (path.endsWith(".phone") && !text.matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu z prefiksem kraju.");
        if (path.endsWith(".postalCode") && !text.matches("[0-9]{2}-[0-9]{3}")) throw invalid("Podaj kod pocztowy w formacie XX-XXX.");
    }

    private boolean isRequired(String path) {
        return Set.of("person.firstName", "person.lastName", "person.contact.email", "person.contact.phone",
                "address.street", "address.houseNumber", "address.postalCode", "address.city",
                "person.motherName", "person.fatherName", "person.birthSurname",
                "guardian.firstName", "guardian.lastName", "guardian.names", "guardian.contact.email",
                "guardian.contact.phone", "ice.firstName", "ice.lastName", "ice.phone", "ice.relation")
                .contains(path);
    }

    private void validateRequiredData(Registration registration, ObjectNode payload) {
        for (String path : BASE_FIELDS) {
            JsonNode node = at(payload, path);
            if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane pole: " + path + ".");
        }
        if (!at(payload, "person.contact.email").asText().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail.");
        if (!at(payload, "person.contact.phone").asText().matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu z prefiksem kraju.");
        if (!at(payload, "address.postalCode").asText().matches("[0-9]{2}-[0-9]{3}")) throw invalid("Podaj kod pocztowy w formacie XX-XXX.");

        if ("STAFF".equals(registration.getRegistrationType())) {
            for (String path : List.of("person.motherName", "person.fatherName", "person.birthSurname")) {
                JsonNode node = at(payload, path);
                if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane pole: " + path + ".");
            }
        }

        if (registration.isMinor()) {
            for (String path : List.of("guardian.firstName", "guardian.lastName", "guardian.relation", "guardian.names", "guardian.contact.email", "guardian.contact.phone")) {
                JsonNode node = at(payload, path);
                if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij wymagane pole opiekuna: " + path + ".");
            }
            if (!Set.of("matka", "ojciec", "opiekun_prawny").contains(at(payload, "guardian.relation").asText()))
                throw invalid("Nieprawidłowa relacja opiekuna.");
            if (!at(payload, "guardian.contact.email").asText().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("Podaj prawidłowy adres e-mail opiekuna.");
            if (!at(payload, "guardian.contact.phone").asText().matches("\\+[1-9][0-9]{9,14}")) throw invalid("Podaj prawidłowy numer telefonu opiekuna.");
            if ("PARTICIPANT".equals(registration.getRegistrationType()) && !payload.path("address").path("sameAddress").asBoolean(true)) {
                for (String field : ADDRESS_FIELDS) {
                    String path = field.replace("address.", "address.guardianAddress.");
                    JsonNode node = at(payload, path);
                    if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("Uzupełnij adres opiekuna albo zaznacz, że jest taki sam.");
                    if (path.endsWith(".postalCode") && !node.asText().matches("[0-9]{2}-[0-9]{3}"))
                        throw invalid("Podaj kod pocztowy opiekuna w formacie XX-XXX.");
                }
            }
        } else {
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
        int count = "STAFF".equals(registration.getRegistrationType()) ? 2 : 3;
        for (int i = 1; i <= count; i++) {
            if (at(payload, "health.q" + i) == null) continue; // Older records may predate later questions.
            JsonNode answer = at(payload, "health.q" + i + ".answer");
            if (answer == null || !answer.isTextual() || !Set.of("tak", "nie").contains(answer.asText())) throw invalid("Uzupełnij odpowiedź w kwestionariuszu.");
            JsonNode detail = at(payload, "health.q" + i + ".detail");
            if ("tak".equals(answer.asText()) && (detail == null || !detail.isTextual() || detail.asText().isBlank()))
                throw invalid("Podaj szczegóły odpowiedzi Tak w kwestionariuszu.");
        }
    }

    private void normalizeHealthDetails(ObjectNode payload, Set<String> changed) {
        for (int i = 1; i <= 3; i++) {
            String answerPath = "health.q" + i + ".answer";
            if (changed.contains(answerPath) && "nie".equals(at(payload, answerPath).asText())) {
                set(payload, "health.q" + i + ".detail", objectMapper.getNodeFactory().nullNode());
                changed.add("health.q" + i + ".detail");
            }
        }
        JsonNode relation = at(payload, "ice.relation");
        if ((changed.contains("ice.relation") || changed.contains("ice.relationOther"))
                && relation != null && relation.isTextual() && !"inna".equals(relation.asText())) {
            set(payload, "ice.relationOther", objectMapper.getNodeFactory().nullNode());
            changed.add("ice.relationOther");
        }
    }

    private JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) current = current == null ? null : current.path(part);
        return current == null || current.isMissingNode() ? null : current;
    }

    private boolean sameNode(JsonNode left, JsonNode right) {
        boolean leftNull = left == null || left.isNull();
        boolean rightNull = right == null || right.isNull();
        return leftNull || rightNull ? leftNull == rightNull : left.equals(right);
    }

    private void set(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode child = current.get(parts[i]);
            if (!(child instanceof ObjectNode)) {
                ObjectNode replacement = objectMapper.createObjectNode();
                current.set(parts[i], replacement);
                current = replacement;
            } else current = (ObjectNode) child;
        }
        current.set(parts[parts.length - 1], value == null ? objectMapper.getNodeFactory().nullNode() : value.deepCopy());
    }

    private RegistrationException invalid(String message) { return new RegistrationException("INVALID_REQUEST", message); }
}
