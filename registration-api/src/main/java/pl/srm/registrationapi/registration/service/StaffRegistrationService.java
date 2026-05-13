package pl.srm.registrationapi.registration.service;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.common.RegistrationCodeGenerator;
import pl.srm.registrationapi.registration.common.RegistrationContext;
import pl.srm.registrationapi.registration.common.RegistrationParser;
import pl.srm.registrationapi.registration.common.TurnusValidator;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.repository.StaffRegistrationRepository;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffRegistrationService implements RegistrationService {

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final StaffRegistrationRepository repository;
    private final RegistrationCodeGenerator codeGenerator;

    public StaffRegistrationService(RegistrationParser parser,
                                    TurnusProvider turnusProvider,
                                    TurnusValidator turnusValidator,
                                    StaffRegistrationRepository repository,
                                    RegistrationCodeGenerator codeGenerator) {
        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    @Override
    public String register(String payload) {
        try {
            RegistrationContext data = parser.parse(payload);
            Turnus turnus = turnusProvider.getByCode(data.turnusCode());
            turnusValidator.validate(turnus);
            validateDuplicate(data);
            return save(data, payload);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("INVALID_REQUEST", e);
        }
    }

    private void validateDuplicate(RegistrationContext data) {
        if (repository.exists(data.turnusCode(), data.key())) {
            throw new RuntimeException("ALREADY_REGISTERED");
        }
    }

    private String save(RegistrationContext data, String payload) {
        int count = repository.countByTurnus(data.turnusCode());
        String code = codeGenerator.generateParticipantCode(data.turnusCode(), count + 1);
        repository.save(new Registration(
                code,
                data.turnusCode(),
                data.key(),
                "NEW",
                LocalDateTime.now(),
                payload
        ));
        return code;
    }

    public List<Registration> getAll() {
        return repository.findAll();
    }
}
