package pl.srm.biuroapi.common.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
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
            var client = new RegistrationApiClient(new RestClientConfig().restClientBuilder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-service", "test-only");
            assertEquals("ACCEPTED", client.updateStatus("REG-1", new StatusUpdateRequest("ACCEPTED", null)).status());
            assertEquals("PATCH", method.get());
            assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString(
                    "test-service:test-only".getBytes(StandardCharsets.UTF_8)), authorization.get());
            assertTrue(payload.get().contains("ACCEPTED"));
        } finally { server.stop(0); }
    }
}
