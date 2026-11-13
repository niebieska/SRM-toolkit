package pl.srm.registrationapi.registration.service.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

class ManualStaffRegistrationServiceTest {

    private static final String PAYLOAD = "{}";
    private static final String CODE = "TEST2027T1";
    private static final RegistrationContext CONTEXT =
            new RegistrationContext(CODE, "08121012345", "pesel-hash", false, true, true);

    private final RegistrationParser parser = mock(RegistrationParser.class);
    private final TurnusProvider turnusProvider = mock(TurnusProvider.class);
    private final RegistrationValidationService validationService = mock(RegistrationValidationService.class);
    private final RegistrationPersistenceService persistenceService = mock(RegistrationPersistenceService.class);
    private final ManualStaffRegistrationService service = new ManualStaffRegistrationService(
            parser, turnusProvider, validationService, persistenceService);

    @Test
    void inactiveTurnusCannotReceiveManualRegistration() {
        when(parser.parse(PAYLOAD)).thenReturn(CONTEXT);
        when(turnusProvider.getByCode(CODE)).thenReturn(turnus(false));

        RegistrationException error = assertThrows(RegistrationException.class,
                () -> service.register(PAYLOAD, "account-id", "operator"));

        assertEquals("TURNUS_INACTIVE", error.getCode());
        verify(validationService, never()).validateEligibility(CONTEXT, turnus(false));
        verify(persistenceService, never()).saveStaffAccepted(CONTEXT, PAYLOAD, "account-id", "operator");
    }

    private static Turnus turnus(boolean active) {
        LocalDate start = LocalDate.of(2027, 1, 18);
        return new Turnus(CODE, "Test", "", "", SeasonType.WINTER, 2027,
                start, start.plusDays(5), 14, 50, 10, active, true, false);
    }
}
