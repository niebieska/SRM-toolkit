package pl.srm.registrationapi.registration.service.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.util.PeselHelper;
import pl.srm.registrationapi.registration.util.RegistrationCodeGenerator;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ParticipantRegistrationService implements RegistrationService {

    private static final String TYPE = "PARTICIPANT";
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantRegistrationService.class);

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final PeselHelper peselHelper;
    private final RegistrationCodeGenerator codeGenerator;
    private final RegistrationRepository repository;
    private final ObjectMapper objectMapper;
    private final EmailServiceClient emailServiceClient;

    public ParticipantRegistrationService(RegistrationParser parser,
                                          TurnusProvider turnusProvider,
                                          TurnusValidator turnusValidator,
                                          PeselHelper peselHelper,
                                          RegistrationCodeGenerator codeGenerator,
                                          RegistrationRepository repository,
                                          ObjectMapper objectMapper,
                                          EmailServiceClient emailServiceClient) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.peselHelper = peselHelper;
        this.codeGenerator = codeGenerator;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.emailServiceClient = emailServiceClient;
    }

    @Override
    public String register(String payload) {
        RegistrationContext data = parser.parse(payload);
        Turnus turnus = turnusProvider.getByCode(data.turnusCode());
        turnusValidator.validate(turnus);
        validatePayload(data, turnus);
        validateDuplicate(data);
        String code = save(data, payload);
        sendRegistrationConfirmation(payload, data, code);
        return code;
    }

    private void validatePayload(RegistrationContext data, Turnus turnus) {
        validatePesel(data);
        validateGuardian(data);
        validateConsents(data);
        validateAge(data, turnus);
    }

    private void validatePesel(RegistrationContext data) {
        if (!peselHelper.isValid(data.pesel())) {
            throw new RegistrationException("INVALID_PESEL", "Podany numer PESEL jest nieprawidłowy.");
        }
    }

    private void validateGuardian(RegistrationContext data) {
        if (data.isMinor() && !data.hasGuardian()) {
            throw new RegistrationException("MISSING_GUARDIAN", "Dla osoby niepełnoletniej wymagane są dane opiekuna.");
        }
    }

    private void validateConsents(RegistrationContext data) {
        if (!data.hasConsent1()) {
            throw new RegistrationException("MISSING_CONSENTS", "Wymagana jest zgoda na przetwarzanie danych osobowych.");
        }
    }

    private void validateAge(RegistrationContext data, Turnus turnus) {
        int age = peselHelper.calculateAge(data.pesel(), turnus.startDate());
        if (age < turnus.minAge()) {
            throw new RegistrationException("AGE_TOO_LOW", "Uczestnik nie spełnia minimalnego wieku dla tego turnusu.");
        }
    }

    private void validateDuplicate(RegistrationContext data) {
        if (repository.existsByTurnusCodeAndPeselHash(data.turnusCode(), data.key())) {
            throw new RegistrationException("ALREADY_REGISTERED", "Ta osoba jest już zarejestrowana na ten turnus.");
        }
    }

    private String save(RegistrationContext data, String payload) {
        int count = repository.countByTurnusCode(data.turnusCode());
        String code = codeGenerator.generateParticipantCode(data.turnusCode(), count + 1);
        Registration registration = new Registration(
                code,
                TYPE,
                data.turnusCode(),
                data.key(),
                data.isMinor(),
                "NEW",
                null,
                payload,
                LocalDateTime.now(),
                null
        );
        repository.save(registration);
        return code;
    }

    private void sendRegistrationConfirmation(String payload, RegistrationContext data, String registrationCode) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode recipient = data.isMinor() ? root.path("guardian") : root.path("person");
            JsonNode participant = root.path("person");
            String to = recipient.path("contact").path("email").asText("").trim();
            String firstName = recipient.path("firstName").asText("").trim();
            String lastName = recipient.path("lastName").asText("").trim();
            String recipientName = (firstName + " " + lastName).trim();
            if (recipientName.isBlank()) {
                recipientName = "Uczestniku";
            }
            String participantName = participantFullName(participant);

            emailServiceClient.sendRegistrationConfirmation(
                    to,
                    recipientName,
                    registrationCode,
                    TYPE,
                    data.turnusCode()
            );
            emailServiceClient.sendOrganizerNewRegistrationNotification(
                    registrationCode,
                    TYPE,
                    data.turnusCode(),
                    participantName
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }

    private String participantFullName(JsonNode participant) {
        String firstName = participant.path("firstName").asText("").trim();
        String lastName = participant.path("lastName").asText("").trim();
        String participantName = (firstName + " " + lastName).trim();
        return participantName.isBlank() ? "Nieznany uczestnik" : participantName;
    }

    @Override
    public List<Registration> getAll() {
        return repository.findByRegistrationType(TYPE);
    }
}
