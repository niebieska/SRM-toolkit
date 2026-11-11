package pl.srm.registrationapi.registration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.service.submission.RegistrationValidationService;
import pl.srm.registrationapi.registration.util.PeselHelper;
import pl.srm.registrationapi.registration.validator.SubmissionPayloadValidator;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;

class RegistrationValidationServiceTest {

    private static final LocalDate TURNUS_START = LocalDate.of(2027, 1, 18);
    private static final String TURNUS_CODE = "TEST2027T1";

    private final RegistrationRepository repository = mock(RegistrationRepository.class);
    private final RegistrationValidationService service = new RegistrationValidationService(
            new PeselHelper(), repository, mock(SubmissionPayloadValidator.class));

    @Test
    void acceptsMinimumAgeCohortWhenBirthdayIsAfterTurnusStart() {
        String pesel = pesel("133010"); // 10 October 2013: turns 14 later in 2027
        RegistrationContext registration = registration(pesel);
        when(repository.existsByTurnusCodeAndPeselHash(TURNUS_CODE, registration.key())).thenReturn(false);

        assertEquals(13, new PeselHelper().calculateAge(pesel, TURNUS_START));
        assertDoesNotThrow(() -> service.validateEligibility(registration, turnusWithMinimumAge(14)));
    }

    @Test
    void rejectsPersonBornInYearBelowMinimumAgeCohort() {
        String pesel = pesel("143010"); // 10 October 2014: only 13 by the 2027 cohort rule
        RegistrationContext registration = registration(pesel);
        when(repository.existsByTurnusCodeAndPeselHash(TURNUS_CODE, registration.key())).thenReturn(false);

        RegistrationException error = assertThrows(RegistrationException.class,
                () -> service.validateEligibility(registration, turnusWithMinimumAge(14)));

        assertEquals("AGE_TOO_LOW", error.getCode());
    }

    private static RegistrationContext registration(String pesel) {
        return new RegistrationContext(TURNUS_CODE, pesel, "test-hash", true, true, true);
    }

    private static Turnus turnusWithMinimumAge(int minimumAge) {
        return new Turnus(TURNUS_CODE, "Turnus testowy", "", "", SeasonType.WINTER,
                2027, TURNUS_START, TURNUS_START.plusDays(5), minimumAge,
                50, 10, true, true, true);
    }

    private static String pesel(String birthDate) {
        String prefix = birthDate + "1234";
        int[] weights = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (prefix.charAt(i) - '0') * weights[i];
        }
        return prefix + ((10 - sum % 10) % 10);
    }
}
