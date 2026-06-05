package pl.srm.registrationapi.registration.service.submission;

import org.springframework.stereotype.Service;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.util.PeselHelper;
import pl.srm.registrationapi.turnus.model.Turnus;

@Service
public class RegistrationValidationService {

   private final PeselHelper peselHelper;
   private final RegistrationRepository repository;

    public RegistrationValidationService(PeselHelper peselHelper, RegistrationRepository repository) {
        this.peselHelper = peselHelper;
        this.repository = repository;
    }

    public void validateEligibility(RegistrationContext data, Turnus turnus) {
        validatePesel(data);
        validateGuardian(data);
        validateConsents(data);
        validateAge(data, turnus);
        validateDuplicate(data);
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
            throw new RegistrationException("AGE_TOO_LOW", "Zgłaszany  nie spełnia minimalnego wieku dla tego turnusu.");
        }
    }

    private void validateDuplicate(RegistrationContext data) {
        if (repository.existsByTurnusCodeAndPeselHash(data.turnusCode(), data.key())) {
            throw new RegistrationException("ALREADY_REGISTERED", "Ta osoba jest już zarejestrowana na ten turnus.");
        }
    }




}
