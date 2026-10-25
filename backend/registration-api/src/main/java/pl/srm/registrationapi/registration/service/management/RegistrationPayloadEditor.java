package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Applies validated field updates and normalizes dependent JSON values. */
@Component
final class RegistrationPayloadEditor {
    record EditResult(String payloadJson, List<String> changedFields) {
        boolean hasChanges() { return !changedFields.isEmpty(); }
    }

    private final ObjectMapper objectMapper;
    private final RegistrationEditPolicy policy;
    private final RegistrationEditValidator validator;

    RegistrationPayloadEditor(ObjectMapper objectMapper, RegistrationEditPolicy policy,
                              RegistrationEditValidator validator) {
        this.objectMapper = objectMapper;
        this.policy = policy;
        this.validator = validator;
    }

    EditResult apply(Registration registration, Map<String, Object> changes) {
        ObjectNode original = readPayload(registration.getPayload());
        ObjectNode payload = original.deepCopy();
        Set<String> editableFields = policy.editableFields(registration, payload);
        TreeSet<String> changedFields = new TreeSet<>();

        for (var change : changes.entrySet()) {
            String path = change.getKey();
            if (!editableFields.contains(path)) throw invalid("Nie można edytować pola: " + path + ".");
            JsonNode value = objectMapper.valueToTree(change.getValue());
            validator.validateValue(path, value);
            JsonNode current = at(payload, path);
            if (!sameNode(current, value)) {
                set(payload, path, value);
                changedFields.add(path);
            }
        }
        if (changedFields.isEmpty()) return new EditResult(registration.getPayload(), List.of());

        normalizeDependentFields(payload, changedFields);
        changedFields.removeIf(path -> sameNode(at(original, path), at(payload, path)));
        if (changedFields.isEmpty()) return new EditResult(registration.getPayload(), List.of());

        validator.validateResult(registration, payload, changedFields);
        try {
            return new EditResult(objectMapper.writeValueAsString(payload), List.copyOf(changedFields));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize edited registration payload", ex);
        }
    }

    private ObjectNode readPayload(String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed instanceof ObjectNode object) return object;
        } catch (Exception ex) {
            throw invalid("Nie można odczytać zapisanych danych zgłoszenia.");
        }
        throw invalid("Nieprawidłowe zapisane dane zgłoszenia.");
    }

    private void normalizeDependentFields(ObjectNode payload, Set<String> changedFields) {
        for (int i = 1; i <= 3; i++) {
            String answerPath = "health.q" + i + ".answer";
            if (changedFields.contains(answerPath) && "nie".equals(at(payload, answerPath).asText())) {
                set(payload, "health.q" + i + ".detail", objectMapper.getNodeFactory().nullNode());
                changedFields.add("health.q" + i + ".detail");
            }
        }
        JsonNode relation = at(payload, "ice.relation");
        if ((changedFields.contains("ice.relation") || changedFields.contains("ice.relationOther"))
                && relation != null && relation.isTextual() && !"inna".equals(relation.asText())) {
            set(payload, "ice.relationOther", objectMapper.getNodeFactory().nullNode());
            changedFields.add("ice.relationOther");
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
            if (!(child instanceof ObjectNode object)) {
                ObjectNode replacement = objectMapper.createObjectNode();
                current.set(parts[i], replacement);
                current = replacement;
            } else {
                current = object;
            }
        }
        current.set(parts[parts.length - 1], value == null ? objectMapper.getNodeFactory().nullNode() : value.deepCopy());
    }

    private static RegistrationException invalid(String message) {
        return new RegistrationException("INVALID_REQUEST", message);
    }
}
