package pl.srm.registrationapi.registration.service.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

@Service
public class StaffRegistrationService implements RegistrationService {

    private static final RegistrationType TYPE = RegistrationType.STAFF;
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffRegistrationService.class);

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final ObjectMapper objectMapper;
    private final EmailServiceClient emailServiceClient;
    private final RegistrationValidationService validationService;
    private final RegistrationPersistenceService persistenceService;

    public StaffRegistrationService(RegistrationParser parser,
                                    TurnusProvider turnusProvider,
                                    TurnusValidator turnusValidator,
                                    ObjectMapper objectMapper,
                                    EmailServiceClient emailServiceClient,
                                    RegistrationValidationService validationService,
                                    RegistrationPersistenceService persistenceService) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.objectMapper = objectMapper;
        this.emailServiceClient = emailServiceClient;
        this.validationService = validationService;
        this.persistenceService = persistenceService;
    }

    @Override
    public String register(String payload) {
        RegistrationContext data = parser.parse(payload);
        Turnus turnus = turnusProvider.getByCode(data.turnusCode());

        turnusValidator.validate(turnus);
        validationService.validateEligibility(data, turnus);

        String code = persistenceService.saveStaff(data, payload);

        sendRegistrationConfirmation(payload, data.turnusCode(), code);

        return code;
    }

    private void sendRegistrationConfirmation(String payload, String turnusCode, String registrationCode) {
        try {
            JsonNode person = objectMapper.readTree(payload).path("person");
            String to = person.path("contact").path("email").asText("").trim();
            String firstName = person.path("firstName").asText("").trim();
            String lastName = person.path("lastName").asText("").trim();

            String staffName = (firstName + " " + lastName).trim();
            String recipientName = staffName;

            if (staffName.isBlank()) {
                recipientName = "Kadro";
                staffName = "Nieznana kadra";
            }

            emailServiceClient.sendRegistrationConfirmation(
                    to,
                    recipientName,
                    registrationCode,
                    TYPE.name(),
                    turnusCode
            );

            emailServiceClient.sendOrganizerNewRegistrationNotification(
                    registrationCode,
                    TYPE.name(),
                    turnusCode,
                    staffName
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }
}

