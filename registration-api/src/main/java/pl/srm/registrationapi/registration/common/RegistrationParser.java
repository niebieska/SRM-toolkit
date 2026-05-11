package pl.srm.registrationapi.registration.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.dto.RegistrationValidationRequest;

@Component
public class RegistrationParser {

    private final ObjectMapper objectMapper;

    public RegistrationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RegistrationContext parse(String payload) throws Exception {

        RegistrationValidationRequest request =
                objectMapper.readValue(payload, RegistrationValidationRequest.class);

        String turnusCode = request.turnusCode();
        String pesel = request.person().pesel();

        String key = turnusCode + "_" + pesel;

        return new RegistrationContext(turnusCode, pesel, key);
    }
}

