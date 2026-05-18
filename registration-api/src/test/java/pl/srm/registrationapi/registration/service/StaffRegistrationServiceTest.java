package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.srm.registrationapi.email.EmailServiceClient;
import pl.srm.registrationapi.registration.common.RegistrationCodeGenerator;
import pl.srm.registrationapi.registration.common.RegistrationContext;
import pl.srm.registrationapi.registration.common.RegistrationParser;
import pl.srm.registrationapi.registration.common.TurnusValidator;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.turnus.domain.SeasonType;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private RegistrationRepository repository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EmailServiceClient emailServiceClient;

    private StaffRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new StaffRegistrationService(
                parser,
                turnusProvider,
                turnusValidator,
                new PeselUtils(),
                repository,
                new RegistrationCodeGenerator(),
                objectMapper,
                emailServiceClient
        );
    }

    @Test
    void sendsStaffConfirmationAndOrganizerNotification() throws Exception {
        when(parser.parse(any())).thenReturn(new RegistrationContext("ZAGLE26T1", "90010112349", "hash123", false, true, true));
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());
        when(repository.countByTurnusCode("ZAGLE26T1")).thenReturn(1);
        when(objectMapper.readTree(anyString())).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "person": {
                            "contact": {
                              "email": "kadra@example.com"
                            },
                            "firstName": "Katarzyna",
                            "lastName": "Nowak"
                          }
                        }
                        """)
        );

        String code = service.register("{\"payload\":true}");

        assertEquals("REG-S-ZAGLE26T1-2", code);
        verify(emailServiceClient).sendRegistrationConfirmation(
                "kadra@example.com",
                "Katarzyna Nowak",
                "REG-S-ZAGLE26T1-2",
                "STAFF",
                "ZAGLE26T1"
        );
        verify(emailServiceClient).sendOrganizerNewRegistrationNotification(
                "REG-S-ZAGLE26T1-2",
                "STAFF",
                "ZAGLE26T1",
                "Katarzyna Nowak"
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
