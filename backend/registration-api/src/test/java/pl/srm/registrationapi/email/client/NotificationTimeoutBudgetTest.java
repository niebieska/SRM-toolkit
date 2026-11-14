package pl.srm.registrationapi.email.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.srm.registrationapi.common.config.RestClientConfig;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.repository.RegistrationStatusHistoryRepository;
import pl.srm.registrationapi.registration.repository.RegistrationEditHistoryRepository;
import pl.srm.registrationapi.registration.service.management.RegistrationStatusService;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.util.PeselHelper;
import pl.srm.registrationapi.registration.service.management.StatusNotificationService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationTimeoutBudgetTest {
    private Duration biuroReadTimeout() throws IOException {
        var sources = new YamlPropertySourceLoader().load("biuro-timeout-contract",
                new ClassPathResource("timeout-contract/application.yml"));
        return Duration.parse((String) sources.getFirst().getProperty("registration.api.read-timeout"));
    }
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(RestClientConfig.class, EmailServiceClient.class)
            .withPropertyValues("email.service.username=email-sender", "email.service.password=test-email-password");

    @Test void configuredDefaultsLeaveHeadroomForReturningTheSavedStatus() throws Exception {
        var biuroRead = biuroReadTimeout();
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var connect = Duration.parse(context.getEnvironment().getProperty("email.service.connect-timeout"));
            var read = Duration.parse(context.getEnvironment().getProperty("email.service.read-timeout"));
            var bothRecipients = connect.plus(read).multipliedBy(2);
            assertTrue(bothRecipients.plusSeconds(10).compareTo(biuroRead) <= 0,
                    "Two inner email attempts must leave at least 10 seconds for the status response");
        });
    }

    @Test void timeoutOverridesCannotExceedTheTwoRecipientBudget() {
        runner.withPropertyValues("EMAIL_CONNECT_TIMEOUT=PT15S", "EMAIL_READ_TIMEOUT=PT15S")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test void twoStalledMinorStaffEmailsStillAllowTheSavedStatusToReachTheOuterCaller() throws Exception {
        var email = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var registration = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        var handlerFailure = new AtomicReference<Throwable>();
        var receivedEmails = new AtomicInteger();
        var emailExecutor = Executors.newCachedThreadPool();
        var repository = mock(RegistrationRepository.class);
        var saved = new Registration("REG-S-T1-1", "STAFF", "T1", "hash", true, "NEW", null,
                "{\"person\":{\"contact\":{\"email\":\"staff@example.test\"}},"
                        + "\"guardian\":{\"contact\":{\"email\":\"guardian@example.test\"}}}",
                LocalDateTime.now(), null);
        when(repository.findByRegistrationCodeForUpdate(saved.getRegistrationCode())).thenReturn(Optional.of(saved));
        when(repository.save(saved)).thenReturn(saved);
        email.createContext("/api/email/send", exchange -> {
            receivedEmails.incrementAndGet();
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        email.setExecutor(emailExecutor);
        email.start();
        try {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                // Scale the production configuration together, keeping this real
                // nested HTTP regression short without hardcoding different ratios.
                var connect = Duration.parse(context.getEnvironment().getProperty("email.service.connect-timeout")).dividedBy(5);
                var read = Duration.parse(context.getEnvironment().getProperty("email.service.read-timeout")).dividedBy(5);
                var inner = new EmailServiceClient(RestClient.builder(), "http://127.0.0.1:" + email.getAddress().getPort(), connect, read, "email-sender", "test-email-password");
                var notifications = new StatusNotificationService(inner, new ObjectMapper());
                var historyRepository = mock(RegistrationStatusHistoryRepository.class);
                var editHistoryRepository = mock(RegistrationEditHistoryRepository.class);
                var statusService = new RegistrationStatusService(repository, historyRepository);
                var service = new RegistrationManagementService(repository, historyRepository,
                        editHistoryRepository, new ObjectMapper(), new PeselHelper(), statusService, notifications);
                registration.createContext("/status", exchange -> {
                    try {
                        exchange.getRequestBody().readAllBytes();
                        service.updateStatus(saved.getRegistrationCode(), new StatusUpdateRequest("ACCEPTED", null, "actor-id", "operator"));
                        byte[] body = "{\"status\":\"ACCEPTED\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    } catch (Throwable ex) { handlerFailure.set(ex); }
                    finally { exchange.close(); }
                });
                registration.start();
                var factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
                factory.setReadTimeout(biuroReadTimeout().dividedBy(5));
                var outer = RestClient.builder().requestFactory(factory).build();
                var response = outer.patch().uri("http://127.0.0.1:" + registration.getAddress().getPort() + "/status")
                        .body("{}").retrieve().toEntity(String.class);
                assertEquals(200, response.getStatusCode().value());
                assertEquals("{\"status\":\"ACCEPTED\"}", response.getBody());
                assertEquals(2, receivedEmails.get(), "Both the minor staff member and guardian are attempted");
                assertEquals("ACCEPTED", saved.getStatus()); verify(repository).save(saved);
                assertNull(handlerFailure.get());
            });
        } finally {
            release.countDown();
            registration.stop(0);
            email.stop(0);
            emailExecutor.shutdownNow();
        }
    }
}
