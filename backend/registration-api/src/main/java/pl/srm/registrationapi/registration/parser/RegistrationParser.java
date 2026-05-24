package pl.srm.registrationapi.registration.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.util.PeselHasher;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.util.PeselHelper;

import java.time.LocalDate;

@Component
public class RegistrationParser {

    private final ObjectMapper objectMapper;
    private final PeselHelper peselHelper;
    private final PeselHasher peselHasher;

    public RegistrationParser(ObjectMapper objectMapper,
                              PeselHelper peselHelper,
                              PeselHasher peselHasher) {
        this.objectMapper = objectMapper;
        this.peselHelper = peselHelper;
        this.peselHasher = peselHasher;
    }

    public RegistrationContext parse(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String turnusCode = requiredText(root, "turnusCode");
            String pesel = requiredText(root.path("person"), "pesel");
            String peselHash = peselHasher.hash(pesel);

            JsonNode guardian = root.path("guardian");
            boolean hasGuardian = guardian.isObject() && !guardian.path("firstName").asText("").isBlank();
            boolean hasConsent1 = root.path("consents").path("dataProcessing").asBoolean(false);
            boolean minor = peselHelper.isMinor(pesel, LocalDate.now());

            return new RegistrationContext(turnusCode, pesel, peselHash, minor, hasGuardian, hasConsent1);
        } catch (RegistrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RegistrationException("INVALID_REQUEST", "Nie udało się przetworzyć danych zgłoszenia.");
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("").trim();
        if (value.isEmpty()) {
            throw new RegistrationException("INVALID_REQUEST", "Brakuje wymaganego pola: " + fieldName + ".");
        }
        return value;
    }
}
