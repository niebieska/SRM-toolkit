package pl.srm.registrationapi.registration.service;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.common.RegistrationContext;
import pl.srm.registrationapi.registration.common.RegistrationParser;
import pl.srm.registrationapi.registration.common.TurnusValidator;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StaffRegistrationService implements RegistrationService {

    private final RegistrationParser parser;
    private final TurnusProvider turnusProvider;
    private final TurnusValidator turnusValidator;

    private final List<Registration> storage = new ArrayList<>();

    public StaffRegistrationService(RegistrationParser parser,
                                    TurnusProvider turnusProvider,
                                    TurnusValidator turnusValidator) {

        this.parser = parser;
        this.turnusProvider = turnusProvider;
        this.turnusValidator = turnusValidator;
    }

    @Override
    public void register(String payload) {

        try {

            RegistrationContext data = parser.parse(payload);

            Turnus turnus = turnusProvider.getByCode(data.turnusCode());

            turnusValidator.validate(turnus);

            validateDuplicate(data);

            save(data, payload);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("INVALID_REQUEST", e);
        }
    }

    private void validateDuplicate(RegistrationContext data) {

        boolean exists = storage.stream()
                .anyMatch(r ->
                        r.turnusCode().equals(data.turnusCode())
                                && r.personKey().equals(data.key())
                );

        if (exists) {
            throw new RuntimeException("ALREADY_REGISTERED");
        }
    }

    private void save(RegistrationContext data, String payload) {

        String code = "REG-S-" + (storage.size() + 1);

        storage.add(new Registration(
                code,
                data.turnusCode(),
                data.key(),
                "NEW",
                LocalDateTime.now(),
                payload
        ));
    }

    public List<Registration> getAll() {
        return storage;
    }

}
