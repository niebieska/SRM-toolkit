package pl.srm.registrationapi.turnus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BiuroTurnusClientTest {
    static final String VALID = """
            [{"turnusCode":"T1","turnusName":"Turnus","turnusLocation":"Miejsce",
            "seasonType":"SUMMER","seasonYear":2026,"startDate":"2026-08-01",
            "endDate":"2026-08-10","minAge":14,"participantCapacity":60,"staffCapacity":10,"active":true,
            "participantRegistrationOpen":true,"staffRegistrationOpen":true,"version":3}]
            """;
    HttpServer server;
    String body = VALID;
    int status = 200;
    long delay;
    AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/turnuses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    BiuroTurnusClient client(Duration timeout) {
        return new BiuroTurnusClient(new ObjectMapper().findAndRegisterModules(),
                new pl.srm.registrationapi.turnus.validator.TurnusCatalogueValidator(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "reader", "test-secret",
                Duration.ofSeconds(15), timeout);
    }

    @Test void readsFullDefinitionAndUsesDedicatedBasicCredentials() throws Exception {
        var result = client(Duration.ofSeconds(30)).fetch();
        assertEquals(1, result.size());
        assertEquals(60, result.getFirst().participantCapacity());
        assertEquals(10, result.getFirst().staffCapacity());
        assertEquals("2026-08-01", result.getFirst().startDate().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("reader:test-secret".getBytes(StandardCharsets.UTF_8)),
                authorization.get());
    }

    @Test void acceptsEmptyArrayButRejectsMalformedOrIncompleteCatalogues() throws Exception {
        var client = client(Duration.ofSeconds(30));
        body = "[]";
        assertTrue(client.fetch().isEmpty());
        for (String invalid : new String[]{"null", "{}", "not-json", "[{}]",
                VALID.replace("\"active\":true,", ""), VALID.replace("\"participantCapacity\":60", "\"participantCapacity\":0"),
                VALID.replace("\"staffCapacity\":10", "\"staffCapacity\":0"),
                VALID.replace("\"staffRegistrationOpen\":true,", ""),
                VALID.replace("\"seasonYear\":2026", "\"seasonYear\":1999"),
                VALID.replace("\"seasonYear\":2026", "\"seasonYear\":10000"),
                VALID.replace("2026-08-10", "2026-07-01"),
                "[" + VALID.trim().substring(1, VALID.trim().length() - 1) + ","
                        + VALID.trim().substring(1, VALID.trim().length() - 1) + "]"}) {
            body = invalid;
            assertThrows(Exception.class, client::fetch);
        }
    }

    @Test void rejectsAuthenticationAndServerFailuresInsteadOfReturningEmptyList() {
        var client = client(Duration.ofSeconds(30));
        for (int error : new int[]{401, 403, 500, 503}) {
            status = error;
            body = "[]";
            assertThrows(org.springframework.web.client.RestClientResponseException.class, client::fetch);
        }
    }

    @Test void configuredReadTimeoutAndConnectionFailureAreSurfaced() {
        delay = 500;
        var client = client(Duration.ofMillis(50));
        assertThrows(org.springframework.web.client.ResourceAccessException.class, client::fetch);
        server.stop(0);
        assertThrows(org.springframework.web.client.ResourceAccessException.class, client::fetch);
    }
}
