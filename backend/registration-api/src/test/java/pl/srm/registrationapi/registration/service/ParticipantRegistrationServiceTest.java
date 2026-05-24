package pl.srm.registrationapi.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.srm.registrationapi.email.EmailServiceClient;
import pl.srm.registrationapi.registration.util.RegistrationCodeGenerator;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.turnus.domain.SeasonType;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipantRegistrationServiceTest {

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

    private ParticipantRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantRegistrationService(
                parser,
                turnusProvider,
                turnusValidator,
                new PeselUtils(),
                new RegistrationCodeGenerator(),
                repository,
                objectMapper,
                emailServiceClient
        );
    }

    @Test
    void rejectsMinorWithoutGuardian() throws Exception {
        when(parser.parse(any())).thenReturn(new RegistrationContext("ZAGLE26T1", "10210112312", "hash", true, false, true));
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());

        RegistrationException exception = assertThrows(RegistrationException.class, () -> service.register("payload"));

        assertEquals("MISSING_GUARDIAN", exception.getCode());
    }

    @Test
    void persistsProjectedFieldsForParticipantRegistration() throws Exception {
        when(parser.parse(any())).thenReturn(new RegistrationContext("ZAGLE26T1", "90010112349", "hash123", false, true, true));
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());
        when(repository.countByTurnusCode("ZAGLE26T1")).thenReturn(2);
        when(repository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.readTree(anyString())).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "person": {
                            "contact": {
                              "email": "jan.kowalski@example.com"
                            },
                            "firstName": "Jan",
                            "lastName": "Kowalski"
                          }
                        }
                        """)
        );

        String code = service.register("{\"payload\":true}");

        assertEquals("REG-P-ZAGLE26T1-3", code);

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(repository).save(captor.capture());
        Registration saved = captor.getValue();
        assertEquals("PARTICIPANT", saved.getRegistrationType());
        assertEquals("ZAGLE26T1", saved.getTurnusCode());
        assertEquals("hash123", saved.getPeselHash());
        assertEquals("NEW", saved.getStatus());
        assertEquals("{\"payload\":true}", saved.getPayload());
        assertFalse(saved.isMinor());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getUpdatedAt());
        verify(emailServiceClient).sendRegistrationConfirmation(
                "jan.kowalski@example.com",
                "Jan Kowalski",
                "REG-P-ZAGLE26T1-3",
                "PARTICIPANT",
                "ZAGLE26T1"
        );
        verify(emailServiceClient).sendOrganizerNewRegistrationNotification(
                "REG-P-ZAGLE26T1-3",
                "PARTICIPANT",
                "ZAGLE26T1",
                "Jan Kowalski"
        );
    }

    @Test
    void sendsMinorConfirmationToGuardianAndOrganizerNotificationForParticipant() throws Exception {
        when(parser.parse(any())).thenReturn(new RegistrationContext("ZAGLE26T1", "10210112312", "hash456", true, true, true));
        when(turnusProvider.getByCode("ZAGLE26T1")).thenReturn(turnus());
        when(repository.countByTurnusCode("ZAGLE26T1")).thenReturn(4);
        when(repository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.readTree(anyString())).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "person": {
                            "contact": {
                              "email": ""
                            },
                            "firstName": "Ania",
                            "lastName": "Nowak"
                          },
                          "guardian": {
                            "contact": {
                              "email": "rodzic@example.com"
                            },
                            "firstName": "Adam",
                            "lastName": "Nowak"
                          }
                        }
                        """)
        );

        String code = service.register("{\"payload\":true}");

        assertEquals("REG-P-ZAGLE26T1-5", code);
        verify(emailServiceClient).sendRegistrationConfirmation(
                "rodzic@example.com",
                "Adam Nowak",
                "REG-P-ZAGLE26T1-5",
                "PARTICIPANT",
                "ZAGLE26T1"
        );
        verify(emailServiceClient).sendOrganizerNewRegistrationNotification(
                "REG-P-ZAGLE26T1-5",
                "PARTICIPANT",
                "ZAGLE26T1",
                "Ania Nowak"
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
