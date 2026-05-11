package pl.srm.registrationapi.registration.common;

import org.springframework.stereotype.Component;
import pl.srm.registrationapi.turnus.domain.Turnus;

@Component
public class TurnusValidator {

    public void validate(Turnus turnus) {

        if (!turnus.active()) {
            throw new RuntimeException("TURNUS_INACTIVE");
        }

        if (!turnus.registrationOpen()) {
            throw new RuntimeException("REGISTRATION_CLOSED");
        }
    }
}