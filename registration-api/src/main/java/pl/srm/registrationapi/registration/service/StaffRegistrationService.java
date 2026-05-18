package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.email.EmailServiceClient;
import pl.srm.registrationapi.registration.common.RegistrationCodeGenerator;
import pl.srm.registrationapi.registration.common.RegistrationContext;
import pl.srm.registrationapi.registration.common.RegistrationParser;
import pl.srm.registrationapi.registration.common.TurnusValidator;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffRegistrationService implements RegistrationService {

    private static final String TYPE = "STAFF";
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffRegistrationService.class);

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final PeselUtils peselUtils;
    private final RegistrationRepository repository;
    private final RegistrationCodeGenerator codeGenerator;
    private final ObjectMapper objectMapper;
    private final EmailServiceClient emailServiceClient;

    public StaffRegistrationService(RegistrationParser parser,
                                    TurnusProvider turnusProvider,
                                    TurnusValidator turnusValidator,
                                    PeselUtils peselUtils,
                                    RegistrationRepository repository,
                                    RegistrationCodeGenerator codeGenerator,
                                    ObjectMapper objectMapper,
                                    EmailServiceClient emailServiceClient) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.peselUtils = peselUtils;
        this.repository = repository;
        this.codeGenerator = codeGenerator;
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
        sendRegistrationConfirmation(payload, data.turnusCode(), code);
        return code;
    }

    private void validatePayload(RegistrationContext data, Turnus turnus) {
        validatePesel(data);
        validateGuardian(data);
        validateConsents(data);
        validateAge(data, turnus);
    }

    private void validatePesel(RegistrationContext data) {
        if (!peselUtils.isValid(data.pesel())) {
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
        int age = peselUtils.calculateAge(data.pesel(), turnus.startDate());
        if (age < turnus.minAge()) {
            throw new RegistrationException("AGE_TOO_LOW", "Osoba zgłaszana nie spełnia minimalnego wieku dla tego turnusu.");
        }
    }

    private void validateDuplicate(RegistrationContext data) {
        if (repository.existsByTurnusCodeAndPeselHash(data.turnusCode(), data.key())) {
            throw new RegistrationException("ALREADY_REGISTERED", "Ta osoba jest już zarejestrowana na ten turnus.");
        }
    }

    private String save(RegistrationContext data, String payload) {
        int count = repository.countByTurnusCode(data.turnusCode());
        String code = codeGenerator.generateStaffCode(data.turnusCode(), count + 1);
        repository.save(new Registration(
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
        ));
        return code;
    }

    private void sendRegistrationConfirmation(String payload, String turnusCode, String registrationCode) {
        try {
            JsonNode person = objectMapper.readTree(payload).path("person");
            String to = person.path("email").asText("").trim();
            String firstName = person.path("firstName").asText("").trim();
            String lastName = person.path("lastName").asText("").trim();
            String recipientName = (firstName + " " + lastName).trim();
            if (recipientName.isBlank()) {
                recipientName = "Kadro";
            }

            emailServiceClient.sendRegistrationConfirmation(to, recipientName, registrationCode, TYPE, turnusCode);
            emailServiceClient.sendOrganizerNewRegistrationNotification(
                    registrationCode,
                    TYPE,
                    turnusCode,
                    recipientName
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }

    @Override
    public List<Registration> getAll() {
        return repository.findByRegistrationType(TYPE);
    }
}
