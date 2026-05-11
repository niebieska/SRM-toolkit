package pl.srm.registrationapi.registration.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.dto.RegistrationValidationRequest;
import pl.srm.registrationapi.registration.service.PeselUtils;

@Component
public class RegistrationParser {

    private final ObjectMapper objectMapper;
    private final PeselUtils peselUtils;

    public RegistrationParser(ObjectMapper objectMapper, PeselUtils peselUtils) {
        this.objectMapper = objectMapper;
        this.peselUtils = peselUtils;
    }

    public RegistrationContext parse(String payload) throws Exception {

        RegistrationValidationRequest request =
                objectMapper.readValue(payload, RegistrationValidationRequest.class);

        String turnusCode = request.turnusCode();
        String pesel = request.person().pesel();
        String personHash = peselUtils.hashPesel(pesel);
        String key = turnusCode + "_" + personHash;

        return new RegistrationContext(turnusCode, pesel, key);
    }
}

