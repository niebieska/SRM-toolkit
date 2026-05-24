package pl.srm.registrationapi.registration.util;

import org.springframework.stereotype.Component;

@Component
public class RegistrationCodeGenerator {

    public String generateParticipantCode(String turnusCode, int sequence) {
        return "REG-P-" + turnusCode + "-" + sequence;
    }

    public String generateStaffCode(String turnusCode, int sequence) {
        return "REG-S-" + turnusCode + "-" + sequence;
    }
}
