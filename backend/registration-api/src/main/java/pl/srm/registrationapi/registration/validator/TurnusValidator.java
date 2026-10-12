package pl.srm.registrationapi.registration.validator;

import pl.srm.registrationapi.registration.model.RegistrationType;

import org.springframework.stereotype.Component;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.turnus.model.Turnus;

@Component
public class TurnusValidator {

    public void validate(Turnus turnus, RegistrationType type) {
        if (!turnus.active()) {
            throw new RegistrationException("TURNUS_INACTIVE", "Zapisy na ten turnus są wyłączone.");
        }

        if (!(type == RegistrationType.STAFF
                ? turnus.staffRegistrationOpen() : turnus.participantRegistrationOpen())) {
            throw new RegistrationException("REGISTRATION_CLOSED", "Rejestracja na ten turnus została zamknięta.");
        }
    }
}
