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
import java.util.concurrent.TimeUnit;
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
            .withUserConfiguration(RestClientConfig.class, EmailServiceClient.class);

    @Test void configuredDefaultsLeaveHeadroomForReturningTheSavedStatus() throws Exception {
        var biuroRead = biuroReadTimeout();
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var connect = Duration.parse(context.getEnvironment().getProperty("email.service.connect-timeout"));
            var read = Duration.parse(context.getEnvironment().getProperty("email.service.read-timeout"));
            assertTrue(connect.plus(read).compareTo(biuroRead) < 0,
                    "Inner email connection/read waits must leave room for the saved-status response");
        });
    }

    @Test void stalledEmailStillAllowsTheSavedStatusToReachTheOuterCaller() throws Exception {
        var email = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var registration = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        var handlerFailure = new AtomicReference<Throwable>();
        var repository = mock(RegistrationRepository.class);
        var saved = new Registration("REG-P-T1-1", "PARTICIPANT", "T1", "hash", false, "NEW", null,
                "{\"person\":{\"contact\":{\"email\":\"recipient@example.test\"}}}", LocalDateTime.now(), null);
        when(repository.findByRegistrationCode(saved.getRegistrationCode())).thenReturn(Optional.of(saved));
        when(repository.save(saved)).thenReturn(saved);
        email.createContext("/api/email/send", exchange -> {
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        email.start();
        try {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                // Scale the production configuration together, keeping this real
                // nested HTTP regression short without hardcoding different ratios.
                var connect = Duration.parse(context.getEnvironment().getProperty("email.service.connect-timeout")).dividedBy(10);
                var read = Duration.parse(context.getEnvironment().getProperty("email.service.read-timeout")).dividedBy(10);
                var inner = new EmailServiceClient(RestClient.builder(), "http://127.0.0.1:" + email.getAddress().getPort(), connect, read);
                var notifications = new StatusNotificationService(inner, new ObjectMapper());
                var historyRepository = mock(RegistrationStatusHistoryRepository.class);
                var statusService = new RegistrationStatusService(repository, historyRepository);
                var service = new RegistrationManagementService(repository, historyRepository,
                        new ObjectMapper(), new PeselHelper(), statusService, notifications);
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
                factory.setReadTimeout(biuroReadTimeout().dividedBy(10));
                var outer = RestClient.builder().requestFactory(factory).build();
                var response = outer.patch().uri("http://127.0.0.1:" + registration.getAddress().getPort() + "/status")
                        .body("{}").retrieve().toEntity(String.class);
                assertEquals(200, response.getStatusCode().value());
                assertEquals("{\"status\":\"ACCEPTED\"}", response.getBody());
                assertEquals("ACCEPTED", saved.getStatus()); verify(repository).save(saved);
                assertNull(handlerFailure.get());
            });
        } finally { release.countDown(); registration.stop(0); email.stop(0); }
    }
}
