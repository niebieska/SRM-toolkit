package pl.srm.registrationapi.registration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.service.submission.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.submission.RegistrationNotificationService;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;
import pl.srm.registrationapi.registration.service.submission.RegistrationValidationService;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantRegistrationServiceTest {

    @Mock
    private RegistrationParser parser;
    @Mock
    private TurnusProvider turnusProvider;
    @Mock
    private TurnusValidator turnusValidator;
    @Mock
    private RegistrationValidationService validationService;
    @Mock
    private RegistrationPersistenceService persistenceService;
    @Mock
    private RegistrationNotificationService notificationService;

    private ParticipantRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantRegistrationService(
                parser,
                turnusProvider,
                turnusValidator,
                validationService,
                persistenceService,
                notificationService
        );
    }

    @Test
    void rejectsMinorWithoutGuardian() {
        RegistrationContext context = new RegistrationContext(
                "ZAGLE26T1",
                "10210112312",
                "hash",
                true,
                false,
                true
        );

        when(parser.parse(anyString())).thenReturn(context);
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());

        doThrow(new RegistrationException(
                "MISSING_GUARDIAN",
                "Dla osoby niepełnoletniej wymagane są dane opiekuna."
        ))
                .when(validationService)
                .validateEligibility(any(), any());

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> service.register("payload")
        );

        assertEquals("MISSING_GUARDIAN", exception.getCode());

        verify(validationService).validateEligibility(eq(context), any());
    }

    @Test
    void registersAdultParticipantAndSendsNotification() {
        RegistrationContext context = new RegistrationContext(
                "ZAGLE26T1",
                "90010112349",
                "hash123",
                false,
                true,
                true
        );

        when(parser.parse(anyString())).thenReturn(context);
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());
        when(persistenceService.saveParticipant(context, "{\"payload\":true}"))
                .thenReturn("REG-P-ZAGLE26T1-3");

        String code = service.register("{\"payload\":true}");

        assertEquals("REG-P-ZAGLE26T1-3", code);

        verify(turnusValidator).validate(any());
        verify(validationService).validateEligibility(eq(context), any());
        verify(persistenceService).saveParticipant(context, "{\"payload\":true}");
        verify(notificationService).sendParticipantRegistrationConfirmation(
                "{\"payload\":true}",
                context,
                "REG-P-ZAGLE26T1-3"
        );
    }

    @Test
    void registersMinorParticipantAndSendsNotification() {
        RegistrationContext context = new RegistrationContext(
                "ZAGLE26T1",
                "10210112312",
                "hash456",
                true,
                true,
                true
        );

        when(parser.parse(anyString())).thenReturn(context);
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());
        when(persistenceService.saveParticipant(context, "{\"payload\":true}"))
                .thenReturn("REG-P-ZAGLE26T1-5");

        String code = service.register("{\"payload\":true}");

        assertEquals("REG-P-ZAGLE26T1-5", code);

        verify(validationService).validateEligibility(eq(context), any());
        verify(persistenceService).saveParticipant(context, "{\"payload\":true}");
        verify(notificationService).sendParticipantRegistrationConfirmation(
                "{\"payload\":true}",
                context,
                "REG-P-ZAGLE26T1-5"
        );
    }

    private Turnus turnus() {
        return new Turnus(
                "ZAGLE26T1",
                "Turnus I",
                "Opis",
                "Rajgród",
                SeasonType.SUMMER,
                2026,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 11),
                14,
                60,
                true,
                true
        );
    }
}