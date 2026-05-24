package pl.srm.registrationapi.registration.validator;

import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.turnus.model.Turnus;

@Component
public class TurnusValidator {

    public void validate(Turnus turnus) {
        if (!turnus.active()) {
            throw new RegistrationException("TURNUS_INACTIVE", "Zapisy na ten turnus są wyłączone.");
        }

        if (!turnus.registrationOpen()) {
            throw new RegistrationException("REGISTRATION_CLOSED", "Rejestracja na ten turnus została zamknięta.");
        }
    }
}
