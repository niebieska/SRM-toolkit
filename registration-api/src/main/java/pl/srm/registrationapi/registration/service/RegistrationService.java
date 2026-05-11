package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.dto.RegistrationValidationRequest;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RegistrationService {

    private final ObjectMapper objectMapper;
    private final TurnusProvider turnusProvider;
    private final PeselUtils peselUtils;

    private final List<Registration> storage = new ArrayList<>();

    public RegistrationService(ObjectMapper objectMapper,
                               TurnusProvider turnusProvider,
                               PeselUtils peselUtils) {

        this.objectMapper = objectMapper;
        this.turnusProvider = turnusProvider;
        this.peselUtils = peselUtils;
    }


    public void register(String payload) {

        try {

            RegistrationValidationRequest request =
                    objectMapper.readValue(payload, RegistrationValidationRequest.class);

            String turnusCode = request.turnusCode();
            String pesel = request.person().pesel();

            Turnus turnus = turnusProvider.getByCode(turnusCode);

            validateTurnus(turnus);

            int age = peselUtils.calculateAge(pesel);

            if (age < turnus.minAge()) {
                throw new RuntimeException("AGE_TOO_LOW");
            }

            String key = buildKey(turnusCode, pesel);
            boolean alreadyRegistered = storage.stream()
                    .anyMatch(r ->
                            r.turnusCode().equals(turnusCode)
                                    && r.personKey().equals(key)
                    );


            if (alreadyRegistered) {
                throw new RuntimeException("ALREADY_REGISTERED");
            }



            String code = generateCode();

            Registration registration = new Registration(
                    code,
                    turnusCode,
                    key,
                    "NEW",
                    LocalDateTime.now(),
                    payload
            );

            storage.add(registration);

            System.out.println("✅ SAVED REGISTRATION: " + code);



        } catch (Exception e) {
            throw new RuntimeException("INVALID_REQUEST", e);
        }

        System.out.println("✅ ORIGINAL PAYLOAD:");
        System.out.println(payload);
    }


    public List<Registration> getAll() {
        return storage;
    }


    private String buildKey(String turnusCode, String pesel) {
        return turnusCode + "_" + pesel;
    }

    private void validateTurnus(Turnus turnus) {

        if (!turnus.active()) {
            throw new RuntimeException("TURNUS_INACTIVE");
        }

        if (!turnus.registrationOpen()) {
            throw new RuntimeException("REGISTRATION_CLOSED");
        }
    }

    private String generateCode() {
        return "REG-" + (storage.size() + 1);
    }




}