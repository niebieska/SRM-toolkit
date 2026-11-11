package pl.srm.registrationapi.registration.service.submission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

/** Handles staff registrations entered by an authenticated Biuro operator. */
@Service
public class ManualStaffRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualStaffRegistrationService.class);

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final RegistrationValidationService validationService;
    private final RegistrationPersistenceService persistenceService;

    public ManualStaffRegistrationService(RegistrationParser parser,
                                          TurnusProvider turnusProvider,
                                          RegistrationValidationService validationService,
                                          RegistrationPersistenceService persistenceService) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.validationService = validationService;
        this.persistenceService = persistenceService;
    }

    /**
     * Creates an accepted staff registration without public-form availability checks or notification email.
     */
    public String register(String payload, String actorAccountId, String actorUsername) {
        validateActor(actorAccountId, actorUsername);

        RegistrationContext data = parser.parse(payload);
        Turnus turnus = turnusProvider.getByCode(data.turnusCode());
        validationService.validateEligibility(data, turnus);
        validationService.validateManualStaffPayload(payload, data, turnus);

        String code = persistenceService.saveStaffAccepted(data, payload, actorAccountId, actorUsername);
        LOGGER.info("Manual staff registration {} created for turnus {} by operator {}",
                code, data.turnusCode(), actorUsername);
        return code;
    }

    private void validateActor(String actorAccountId, String actorUsername) {
        if (actorAccountId == null || actorAccountId.isBlank() || actorAccountId.length() > 36
                || actorUsername == null || actorUsername.isBlank() || actorUsername.length() > 64) {
            throw new RegistrationException("INVALID_ACTOR", "Brak prawidłowych danych operatora.");
        }
    }
}
