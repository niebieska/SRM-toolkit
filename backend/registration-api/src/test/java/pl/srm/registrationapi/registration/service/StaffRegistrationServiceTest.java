package pl.srm.registrationapi.registration.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.service.submission.RegistrationNotificationService;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;
import pl.srm.registrationapi.registration.service.submission.RegistrationValidationService;
import pl.srm.registrationapi.registration.service.submission.StaffRegistrationService;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffRegistrationServiceTest {

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

    private StaffRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new StaffRegistrationService(
                parser,
                turnusProvider,
                turnusValidator,
                validationService,
                persistenceService,
                notificationService
        );
    }

    @Test
    void registersStaffAndSendsNotification() {
        RegistrationContext context = new RegistrationContext(
                "ZAGLE26T1",
                "90010112349",
                "hash123",
                false,
                true,
                true
        );

        when(parser.parse(anyString())).thenReturn(context);
        Turnus selectedTurnus = turnus();
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(selectedTurnus);
        when(persistenceService.saveStaff(context, "{\"payload\":true}"))
                .thenReturn("REG-S-ZAGLE26T1-2");

        String code = service.register("{\"payload\":true}");

        assertEquals("REG-S-ZAGLE26T1-2", code);

        verify(turnusValidator).validate(any(), any());
        verify(validationService).validateEligibility(same(context), any());
        verify(persistenceService).saveStaff(same(context), eq("{\"payload\":true}"));

        verify(notificationService).sendStaffRegistrationConfirmation(
                eq("{\"payload\":true}"),
                same(context),
                eq("REG-S-ZAGLE26T1-2"),
                same(selectedTurnus)
        );
    }

    @Test
    void invalidPayloadNeverPersistsOrSendsNotification() {
        RegistrationContext context = new RegistrationContext("ZAGLE26T1", "90010112349", "hash", false, false, true);
        when(parser.parse(anyString())).thenReturn(context);
        Turnus selectedTurnus = turnus();
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(selectedTurnus);
        Mockito.doThrow(new RegistrationException("INVALID_REQUEST", "Missing contact"))
                .when(validationService).validatePayload(eq("{}"), eq(context), eq(RegistrationType.STAFF), any());
        Assertions.assertThrows(RegistrationException.class,
                () -> service.register("{}"));
        Mockito.verifyNoInteractions(persistenceService, notificationService);
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
                60, 10,
                true,
                true
        ,
                true
        );
    }
    @Test
    void unavailableCatalogueDoesNotPersistOrSendEmail() {
        var data = new RegistrationContext("ZAGLE26T1", "90010112349", "hash", false, true, true);
        when(parser.parse("payload")).thenReturn(data);
        when(turnusProvider.getByCode("ZAGLE26T1")).thenThrow(
                new RegistrationException(
                        "TURNUS_CATALOGUE_UNAVAILABLE", "Catalogue unavailable"));
        var error = Assertions.assertThrows(
                RegistrationException.class,
                () -> service.register("payload"));
        assertEquals("TURNUS_CATALOGUE_UNAVAILABLE", error.getCode());
        Mockito.verifyNoInteractions(persistenceService, notificationService);
    }

}