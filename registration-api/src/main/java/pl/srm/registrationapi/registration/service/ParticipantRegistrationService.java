package pl.srm.registrationapi.registration.service;

import org.springframework.stereotype.Service;
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
public class ParticipantRegistrationService implements RegistrationService {

    private static final String TYPE = "PARTICIPANT";

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final PeselUtils peselUtils;
    private final RegistrationCodeGenerator codeGenerator;
    private final RegistrationRepository repository;

    public ParticipantRegistrationService(RegistrationParser parser,
                                          TurnusProvider turnusProvider,
                                          TurnusValidator turnusValidator,
                                          PeselUtils peselUtils,
                                          RegistrationCodeGenerator codeGenerator,
                                          RegistrationRepository repository) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.peselUtils = peselUtils;
        this.codeGenerator = codeGenerator;
        this.repository = repository;
    }

    @Override
    public String register(String payload) {
        RegistrationContext data = parser.parse(payload);
        Turnus turnus = turnusProvider.getByCode(data.turnusCode());
        turnusValidator.validate(turnus);
        validatePayload(data, turnus);
        validateDuplicate(data);
        return save(data, payload);
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

    @Override
    public List<Registration> getAll() {
        return repository.findByRegistrationType(TYPE);
    }
}
