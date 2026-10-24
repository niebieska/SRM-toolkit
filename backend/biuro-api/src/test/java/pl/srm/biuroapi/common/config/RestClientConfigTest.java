package pl.srm.biuroapi.common.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import pl.srm.biuroapi.registration.api.StatusUpdateRequest;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RestClientConfigTest {
    @Test
    void configuredTransportSendsAuthenticatedStatusPatchToRealHttpServer() throws Exception {
        var method = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var payload = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/registrations/REG-1/status", exchange -> {
            try {
                method.set(exchange.getRequestMethod());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = "{\"registrationCode\":\"REG-1\",\"minor\":false,\"status\":\"ACCEPTED\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new RegistrationApiClient(new RestClientConfig().restClientBuilder(Duration.ofSeconds(60)),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-service", "test-only");
            assertEquals("ACCEPTED", client.updateStatus("REG-1", new StatusUpdateRequest("ACCEPTED", null), "actor-id", "operator").status());
            assertEquals("PATCH", method.get());
            assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString(
                    "test-service:test-only".getBytes(StandardCharsets.UTF_8)), authorization.get());
            assertTrue(payload.get().contains("ACCEPTED"));
        } finally { server.stop(0); }
    }

    @Test
    void configuredReadTimeoutIsAppliedToTheActualTransport() throws Exception {
        var release = new CountDownLatch(1);
        var connected = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            connected.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withInitializer(context -> context.getBeanFactory().setConversionService(
                            ApplicationConversionService.getSharedInstance()))
                    .withUserConfiguration(RestClientConfig.class)
                    .withPropertyValues("registration.api.read-timeout=PT0.15S")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        var client = context.getBean(RestClient.Builder.class).build();
                        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                                assertThrows(ResourceAccessException.class, () ->
                                        client.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")
                                                .retrieve().toBodilessEntity()));
                        assertEquals(0, connected.getCount());
                    });
        } finally { release.countDown(); server.stop(0); }
    }

}
