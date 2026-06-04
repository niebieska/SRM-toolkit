package pl.srm.registrationapi.registration.service.submission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

@Service
public class StaffRegistrationService implements RegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffRegistrationService.class);

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final RegistrationValidationService validationService;
    private final RegistrationPersistenceService persistenceService;
    private final RegistrationNotificationService notificationService;

    public StaffRegistrationService(RegistrationParser parser,
                                    TurnusProvider turnusProvider,
                                    TurnusValidator turnusValidator,
                                    RegistrationValidationService validationService,
                                    RegistrationPersistenceService persistenceService,
                                    RegistrationNotificationService notificationService) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.validationService = validationService;
        this.persistenceService = persistenceService;
        this.notificationService = notificationService;
    }

    @Override
    public String register(String payload) {
        RegistrationContext data = parser.parse(payload);
        LOGGER.info(
                "Processing staff registration for turnus {}",
                data.turnusCode()
        );


        Turnus turnus = turnusProvider.getByCode(data.turnusCode());

        turnusValidator.validate(turnus);
        validationService.validateEligibility(data, turnus);

        String code = persistenceService.saveStaff(data, payload);
        LOGGER.info(
                "Staff registration {} created for turnus {}",
                code,
                data.turnusCode()
        );

        notificationService.sendStaffRegistrationConfirmation(payload,data, code);

        return code;
    }


}

