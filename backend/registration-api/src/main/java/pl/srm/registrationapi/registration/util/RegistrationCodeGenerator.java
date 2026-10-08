package pl.srm.registrationapi.registration.util;

import org.springframework.stereotype.Component;

@Component
public class RegistrationCodeGenerator {

    public String generateParticipantCode(String turnusCode, long sequence) {
        return "REG-P-" + turnusCode + "-" + sequence;
    }

    public String generateStaffCode(String turnusCode, long sequence) {
        return "REG-S-" + turnusCode + "-" + sequence;
    }
}
