package pl.srm.registrationapi.season;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import java.util.List;

@Component
public class StaffRoleValidator {
    public void validate(JsonNode root, boolean minor, StaffCatalogue catalogue) {
        if (catalogue == null) throw new RegistrationException("TURNUS_CATALOGUE_UNAVAILABLE",
                "Opcje kadry są chwilowo niedostępne. Spróbuj ponownie później.");
        String code = root.path("role").asText("").trim();
        var role = catalogue.roles().stream().filter(r -> r.value().equals(code)).findFirst()
                .orElseThrow(() -> invalid("Wybierz rolę dostępną dla tego sezonu."));
        JsonNode functionNode = root.path("subrole");
        if (!functionNode.isMissingNode() && !functionNode.isNull() && !functionNode.isTextual())
            throw invalid("Wybierz jedną funkcję kadry.");
        String functionCode = functionNode.asText("").trim();
        validateFunction(role, functionCode, minor);
        validateCertificates(root, certificates(catalogue, code, functionCode));
    }

    private void validateFunction(StaffCatalogue.Role role, String code, boolean minor) {
        if (code.isBlank()) {
            if (role.subroleRequired()) throw invalid("Wybierz funkcję kadry.");
            if (!eligible(role.adultOnly(), role.minorOnly(), minor)) throw invalid("Rola nie jest dostępna dla tej grupy wiekowej.");
            return;
        }
        var function = role.subroles().stream().filter(f -> f.value().equals(code)).findFirst()
                .orElseThrow(() -> invalid("Nieprawidłowa funkcja kadry."));
        if (!eligible(function.adultOnly(), function.minorOnly(), minor))
            throw invalid("Funkcja nie jest dostępna dla tej grupy wiekowej.");
    }
    private boolean eligible(boolean adultOnly, boolean minorOnly, boolean minor) {
        return minor ? !adultOnly : !minorOnly;
    }
    private List<StaffCatalogue.Certificate> certificates(StaffCatalogue catalogue, String role, String function) {
        var options = catalogue.certificatesByRole();
        if (!function.isBlank() && options.containsKey(role + "_" + function)) return options.get(role + "_" + function);
        if (!function.isBlank() && options.containsKey(function)) return options.get(function);
        return options.getOrDefault(role, List.of());
    }
    private void validateCertificates(JsonNode root, List<StaffCatalogue.Certificate> available) {
        JsonNode selected = root.path("certificates"), details = root.path("certificateDetails");
        if (!selected.isObject() || !details.isObject()) throw invalid("Nieprawidłowy format uprawnień.");
        selected.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isBoolean()) throw invalid("Nieprawidłowa deklaracja uprawnień.");
            var certificate = available.stream().filter(c -> c.id().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> invalid("Uprawnienie nie jest dostępne dla tej funkcji."));
            if (entry.getValue().asBoolean() && certificate.hasDetails()) {
                JsonNode text = details.path(certificate.id());
                if (!text.isTextual() || text.asText().isBlank() || text.asText().length() > 2000)
                    throw invalid("Opisz wybrane uprawnienie: " + certificate.label() + ".");
            }
        });
    }
    private RegistrationException invalid(String message) { return new RegistrationException("INVALID_REQUEST", message); }
}
