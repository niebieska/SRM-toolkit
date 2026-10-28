package pl.srm.registrationapi.email.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.srm.registrationapi.registration.parser.RegistrationContext;
import pl.srm.registrationapi.registration.parser.RegistrationParser;
import pl.srm.registrationapi.registration.service.submission.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.submission.StaffRegistrationService;
import pl.srm.registrationapi.registration.service.submission.RegistrationNotificationService;
import pl.srm.registrationapi.registration.service.submission.RegistrationPersistenceService;
import pl.srm.registrationapi.registration.service.submission.RegistrationValidationService;
import pl.srm.registrationapi.registration.validator.TurnusValidator;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;
import java.time.LocalDate;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.repository.RegistrationEditHistoryRepository;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.repository.RegistrationStatusHistoryRepository;
import pl.srm.registrationapi.registration.service.management.RegistrationStatusService;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.management.StatusNotificationService;
import pl.srm.registrationapi.registration.util.PeselHelper;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceClientTest {
    HttpServer server;
    final AtomicInteger calls = new AtomicInteger();
    final AtomicReference<String> authorization = new AtomicReference<>();
    final Logger logger = (Logger) LoggerFactory.getLogger(EmailServiceClient.class);
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    static final Map<String, String> VARIABLES = Map.of("registrationCode", "REG-P-T1-1", "private", "SECRET_PAYLOAD");
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        logs.start(); logger.addAppender(logs);
    }
    @AfterEach void cleanup() { server.stop(0); logger.detachAppender(logs); logs.stop(); }
    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    EmailServiceClient client(Duration read) { return new EmailServiceClient(RestClient.builder(), url(), Duration.ofMillis(300), read, "email-sender", "test-email-password"); }
    void respond(int status, String body) {
        server.createContext("/api/email/send", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("POST", exchange.getRequestMethod());
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
    }
    String messages() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a + "\n" + b); }
    void send() { client(Duration.ofSeconds(1)).sendEmail("recipient@example.test", "status-update", VARIABLES); }

    @Test void readsAcceptanceResponseAndLogsRegistrationWithoutPersonalData() {
        respond(200, "{\"success\":true,\"message\":\"SMTP accepted\"}"); send();
        assertEquals(1, calls.get()); assertTrue(messages().contains("outcome=SMTP_ACCEPTED"));
        assertTrue(messages().contains("code=REG-P-T1-1 template=status-update"));
        assertFalse(messages().contains("recipient@")); assertFalse(messages().contains("SECRET_PAYLOAD"));
    }
    @Test void disabledSendingIsNotLoggedAsSuccess() {
        respond(200, "{\"success\":false,\"message\":\"disabled\"}"); send();
        assertTrue(messages().contains("outcome=NOT_SENT")); assertFalse(messages().contains("SMTP_ACCEPTED"));
        assertEquals(1, calls.get());
    }
    @Test void malformedSuccessResponseCannotBecomeFalseSuccess() {
        respond(200, "{}"); send();
        assertTrue(messages().contains("UNCONFIRMED")); assertFalse(messages().contains("SMTP_ACCEPTED"));
        assertEquals(1, calls.get());
    }
    @Test void smtpErrorIsConservativelyUnconfirmedAndResponseBodyIsNotLogged() {
        respond(500, "{\"success\":false,\"message\":\"PRIVATE recipient@example.test credential=SECRET\"}"); send();
        assertTrue(messages().contains("UNCONFIRMED")); assertTrue(messages().contains("httpStatus=500"));
        assertFalse(messages().contains("PRIVATE")); assertFalse(messages().contains("recipient@"));
        assertFalse(messages().contains("SECRET")); assertEquals(1, calls.get());
    }
    @Test void rejectedRequestIsNotRetriedAndDoesNotEscapeToRegistration() {
        respond(400, "{\"message\":\"private invalid request\"}"); assertDoesNotThrow(this::send);
        assertTrue(messages().contains("outcome=NOT_SENT")); assertEquals(1, calls.get());
    }
    @Test void realReadTimeoutReturnsWithoutRetryOrLosingRegistrationContext() {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/api/email/send", exchange -> {
            calls.incrementAndGet();
            try { release.await(4, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        }); server.start();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> client(Duration.ofMillis(150))
                    .sendEmail("recipient@example.test", "status-update", VARIABLES));
            assertEquals(1, calls.get()); assertTrue(messages().contains("UNCONFIRMED"));
            assertTrue(messages().contains("REG-P-T1-1")); assertFalse(messages().contains("recipient@"));
        } finally { release.countDown(); }
    }
    @Test void sendsDedicatedCredentialsWithoutLoggingThem() {
        respond(200, "{\"success\":true}"); send();
        String expected = "Basic " + HttpHeaders.encodeBasicAuth("email-sender", "test-email-password", null);
        assertEquals(expected, authorization.get());
        assertFalse(messages().contains("test-email-password"));
        assertFalse(messages().contains(expected));
    }
    @Test void refusedConnectionDoesNotEscapeAndIsNotReportedAsAcceptance() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var client = new EmailServiceClient(RestClient.builder(), "http://127.0.0.1:" + port,
                Duration.ofMillis(150), Duration.ofMillis(150), "email-sender", "test-email-password");
        assertDoesNotThrow(() -> client.sendEmail("recipient@example.test", "status-update", VARIABLES));
        assertTrue(messages().contains("UNCONFIRMED")); assertFalse(messages().contains("SMTP_ACCEPTED"));
    }
    @Test void missingRecipientMakesNoRequestAndUnsafeIdentifiersCannotInjectLogLines() {
        respond(200, "{\"success\":true}");
        client(Duration.ofSeconds(1)).sendEmail("", "private\nINJECTED", Map.of("registrationCode", "private\nINJECTED"));
        assertEquals(0, calls.get()); assertTrue(messages().contains("MISSING_RECIPIENT"));
        assertTrue(messages().contains("code=unknown template=unsupported")); assertFalse(messages().contains("INJECTED"));
    }
    @Test void invalidHttpTimeoutsFailFast() {
        for (Duration value : new Duration[]{Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(1), Duration.ofDays(100)}) {
            assertThrows(IllegalArgumentException.class, () -> new EmailServiceClient(RestClient.builder(), url(), value, Duration.ofSeconds(1), "email-sender", "test-email-password"));
            assertThrows(IllegalArgumentException.class, () -> new EmailServiceClient(RestClient.builder(), url(), Duration.ofSeconds(1), value, "email-sender", "test-email-password"));
        }
    }
    @ParameterizedTest @ValueSource(ints = {401, 500})
    void statusRemainsSavedWhenActualEmailHttpRequestFails(int status) {
        respond(status, "{\"success\":false}");
        RegistrationRepository repository = mock(RegistrationRepository.class);
        Registration registration = new Registration("REG-P-T1-1", "PARTICIPANT", "T1", "hash", false, "NEW", null,
                "{\"person\":{\"firstName\":\"Synthetic\",\"contact\":{\"email\":\"recipient@example.test\"}}}", LocalDateTime.now(), null);
        when(repository.findByRegistrationCodeForUpdate("REG-P-T1-1")).thenReturn(Optional.of(registration));
        when(repository.save(registration)).thenReturn(registration);
        var notifications = new StatusNotificationService(client(Duration.ofSeconds(1)), new ObjectMapper());
        var historyRepository = mock(RegistrationStatusHistoryRepository.class);
        var editHistoryRepository = mock(RegistrationEditHistoryRepository.class);
        var statusService = new RegistrationStatusService(repository, historyRepository);
        var service = new RegistrationManagementService(repository, historyRepository,
                editHistoryRepository, new ObjectMapper(), new PeselHelper(), statusService, notifications);
        assertEquals("ACCEPTED", service.updateStatus("REG-P-T1-1",
                new StatusUpdateRequest("ACCEPTED", null, "actor-id", "operator")).status());
        verify(repository).save(registration); assertEquals(1, calls.get());
        assertTrue(messages().contains(status == 401 ? "NOT_SENT" : "UNCONFIRMED"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void participantCodeStillReturnsAfterHttpEmailFailure(boolean minor) {
        verifySubmissionFailureIsolation(minor, false);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void staffCodeStillReturnsAndMinorGuardianIsAttemptedAfterFirstHttpFailure(boolean minor) {
        verifySubmissionFailureIsolation(minor, true);
    }
    private void verifySubmissionFailureIsolation(boolean minor, boolean staff) {
        respond(500, "{\"success\":false}");
        var parser = mock(RegistrationParser.class);
        var provider = mock(TurnusProvider.class);
        var validator = mock(TurnusValidator.class);
        var validation = mock(RegistrationValidationService.class);
        var persistence = mock(RegistrationPersistenceService.class);
        var email = spy(client(Duration.ofSeconds(1)));
        var notifications = new RegistrationNotificationService(email, new ObjectMapper());
        String payload = """
                {"person":{"firstName":"Synthetic","contact":{"email":"person@example.test"}},
                 "guardian":{"firstName":"Guardian","contact":{"email":"guardian@example.test"}}}
                """;
        var context = new RegistrationContext("T1", "synthetic", "hash", minor, true, true);
        var turnus = new Turnus("T1", "Synthetic", "Test", "Test", SeasonType.WINTER, 2027,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 7), 14, 50, 10, true, true, true);
        when(parser.parse(payload)).thenReturn(context);
        when(provider.getByCode("T1")).thenReturn(turnus);
        if (staff) {
            when(persistence.saveStaff(context, payload)).thenReturn("REG-S-T1-1");
            var service = new StaffRegistrationService(parser, provider, validator, validation, persistence, notifications);
            assertEquals("REG-S-T1-1", service.register(payload));
        } else {
            when(persistence.saveParticipant(context, payload)).thenReturn("REG-P-T1-1");
            var service = new ParticipantRegistrationService(parser, provider, validator, validation, persistence, notifications);
            assertEquals("REG-P-T1-1", service.register(payload));
        }
        var order = inOrder(persistence, email);
        if (staff) order.verify(persistence).saveStaff(context, payload);
        else order.verify(persistence).saveParticipant(context, payload);
        String template = staff ? "registration-confirmation-staff" : "registration-confirmation-participant";
        order.verify(email).sendEmail(eq(!staff && minor ? "guardian@example.test" : "person@example.test"), eq(template), anyMap());
        if (staff && minor) order.verify(email).sendEmail(eq("guardian@example.test"), eq(template), anyMap());
        assertEquals(staff && minor ? 2 : 1, calls.get());
        assertTrue(messages().contains("UNCONFIRMED"));
    }

}
