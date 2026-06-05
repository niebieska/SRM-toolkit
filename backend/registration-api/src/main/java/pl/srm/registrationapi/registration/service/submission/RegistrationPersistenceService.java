package pl.srm.registrationapi.registration.service.submission;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.model.RegistrationStatus;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.util.RegistrationCodeGenerator;

import java.time.LocalDateTime;

@Service
public class RegistrationPersistenceService {

    private final RegistrationRepository repository;
    private final RegistrationCodeGenerator codeGenerator;

    public RegistrationPersistenceService(RegistrationRepository repository,
                                          RegistrationCodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    public String saveParticipant(RegistrationContext context, String payload) {
        int sequence = nextSequence(context.turnusCode());
        String code = codeGenerator.generateParticipantCode(context.turnusCode(), sequence);

        return save(context, payload, code, RegistrationType.PARTICIPANT);
    }

    public String saveStaff(RegistrationContext context, String payload) {
        int sequence = nextSequence(context.turnusCode());
        String code = codeGenerator.generateStaffCode(context.turnusCode(), sequence);

        return save(context, payload, code, RegistrationType.STAFF);
    }

    private int nextSequence(String turnusCode) {
        return repository.countByTurnusCode(turnusCode) + 1;
    }

    private String save(RegistrationContext context,
                        String payload,
                        String registrationCode,
                        RegistrationType registrationType) {
        Registration registration = new Registration(
                registrationCode,
                registrationType.name(),
                context.turnusCode(),
                context.key(),
                context.isMinor(),
                RegistrationStatus.NEW.name(),
                null,
                payload,
                LocalDateTime.now(),
                null
        );

        repository.save(registration);

        return registrationCode;
    }
}