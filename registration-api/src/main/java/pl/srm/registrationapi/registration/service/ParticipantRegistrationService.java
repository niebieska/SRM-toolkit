package pl.srm.registrationapi.registration.service;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.common.RegistrationCodeGenerator;
import pl.srm.registrationapi.registration.common.RegistrationContext;
import pl.srm.registrationapi.registration.common.RegistrationParser;
import pl.srm.registrationapi.registration.common.TurnusValidator;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.repository.ParticipantRegistrationRepository;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ParticipantRegistrationService implements RegistrationService {

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;
    private final PeselUtils peselUtils;
    private final RegistrationCodeGenerator codeGenerator;

    private final ParticipantRegistrationRepository repository;

    public ParticipantRegistrationService(RegistrationParser parser,
                                          TurnusProvider turnusProvider,
                                          TurnusValidator turnusValidator,
                                          PeselUtils peselUtils, RegistrationCodeGenerator codeGenerator,
                                          ParticipantRegistrationRepository repository) {


        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
        this.peselUtils = peselUtils;
        this.codeGenerator = codeGenerator;
        this.repository = repository;
    }

    @Override
    public void register(String payload) {

        try {

            RegistrationContext data = parser.parse(payload);

            Turnus turnus = turnusProvider.getByCode(data.turnusCode());

            turnusValidator.validate(turnus);

            validateAge(data, turnus);

            validateDuplicate(data);

            save(data, payload);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("INVALID_REQUEST", e);
        }
    }

    private void validateAge(RegistrationContext data, Turnus turnus) {

        int age = peselUtils.calculateAge(data.pesel());

        if (age < turnus.minAge()) {
            throw new RuntimeException("AGE_TOO_LOW");
        }
    }

    private void validateDuplicate(RegistrationContext data) {


        if (repository.exists(data.turnusCode(), data.key())) {
            throw new RuntimeException("ALREADY_REGISTERED");
        }
    }


    private void save(RegistrationContext data, String payload) {

        int count = repository.countByTurnus(data.turnusCode());
        String code = codeGenerator.generateParticipantCode(
                data.turnusCode(),
                count + 1
        );


        Registration registration = new Registration(
                code,
                data.turnusCode(),
                data.key(),
                "NEW",
                LocalDateTime.now(),
                payload
        );
        repository.save(registration);
    }


    public List<Registration> getAll() {
        return repository.findAll();
    }

}