package pl.srm.biuroapi.turnus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.srm.biuroapi.auth.config.JwtConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

// Real HTTP also exercises servlet filter registration, which MockMvc can miss.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "registration.api.password=test-service-password",
        "turnus.lookup.username=test-reader", "turnus.lookup.password=test-reader-password",
        "biuro.auth.username=test-operator", "biuro.auth.password=test-operator-password",
        "spring.datasource.url=jdbc:h2:mem:internal-turnus-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class InternalTurnusSecurityTest {
    @Autowired ServletWebServerApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtConfig jwt;
    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void resetCatalogue() {
        jdbc.update("DELETE FROM turnus WHERE turnus_code LIKE 'TEST%'");
        jdbc.update("UPDATE turnus SET active = true, participant_registration_open = true");
    }

    String basic(String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                ("test-reader:" + password).getBytes(StandardCharsets.UTF_8));
    }

    HttpResponse<String> request(String method, String path, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"
                + context.getWebServer().getPort() + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) builder.header("Authorization", authorization);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void requiresDedicatedBasicCredentials() throws Exception {
        for (String auth : new String[]{null, basic("wrong"), "Basic !!!",
                "Bearer " + jwt.generateToken("test-operator")}) {
            var response = request("GET", "/internal/turnuses", auth);
            assertEquals(401, response.statusCode());
            assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic"));
        }
    }

    @Test void returnsFullCatalogueIncludingClosedAndArchivedDefinitions() throws Exception {
        jdbc.update("UPDATE turnus SET participant_registration_open = false WHERE turnus_code = 'ZAGLE26T1'");
        jdbc.update("UPDATE turnus SET active = false WHERE turnus_code = 'ZAGLE26T2'");
        var response = request("GET", "/internal/turnuses", basic("test-reader-password"));
        assertEquals(200, response.statusCode());
        assertEquals(2, (int) JsonPath.read(response.body(), "$.length()"));
        assertEquals(false, JsonPath.read(response.body(), "$[0].participantRegistrationOpen"));
        assertEquals(false, JsonPath.read(response.body(), "$[1].active"));
        assertEquals(60, (int) JsonPath.read(response.body(), "$[0].participantCapacity"));
        assertEquals("SUMMER", JsonPath.read(response.body(), "$[0].staffCatalogue.seasonType"));
        assertEquals(8, (int) JsonPath.read(response.body(), "$[0].staffCatalogue.roles.length()"));
        assertFalse(response.body().contains("staffRequirements"));
        assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
    }

    @Test void serviceAccountCannotWriteOrAccessOperatorRoutes() throws Exception {
        String auth = basic("test-reader-password");
        for (String method : new String[]{"POST", "PATCH", "DELETE", "HEAD"})
            assertEquals(403, request(method, "/internal/turnuses", auth).statusCode());
        for (String path : new String[]{"/internal/turnuses/ZAGLE26T1", "/internal/unknown",
                "/api/turnuses", "/api/turnuses/ZAGLE26T1/lists", "/api/biuro/registrations"})
            assertEquals(403, request("GET", path, auth).statusCode());
        assertEquals(403, request("POST", "/api/turnuses", auth).statusCode());
    }

    @Test void emptyCatalogueReturnsSuccessfulEmptyJsonArray() throws Exception {
        // Preserve fixtures in this isolated H2 database for subsequent tests.
        jdbc.execute("CREATE TABLE turnus_test_backup AS SELECT * FROM turnus");
        try {
            jdbc.update("DELETE FROM turnus");
            var response = request("GET", "/internal/turnuses", basic("test-reader-password"));
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
            assertEquals("[]", response.body().trim());
        } finally {
            jdbc.update("INSERT INTO turnus SELECT * FROM turnus_test_backup");
            jdbc.execute("DROP TABLE turnus_test_backup");
        }
    }

    @Test void databaseFailureReturnsServerErrorInsteadOfEmptyCatalogue() throws Exception {
        // Break the real query without mocking the controller/service/error handler.
        jdbc.execute("ALTER TABLE turnus RENAME TO turnus_unavailable");
        try {
            var response = request("GET", "/internal/turnuses", basic("test-reader-password"));
            assertEquals(500, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
            assertNotEquals("[]", response.body().trim());
            assertEquals("Wystąpił nieoczekiwany błąd", JsonPath.read(response.body(), "$.message"));
            assertFalse(response.body().contains("SELECT"));
            assertFalse(response.body().contains("turnus_unavailable"));
        } finally {
            jdbc.execute("ALTER TABLE turnus_unavailable RENAME TO turnus");
        }
        assertEquals(200, request("GET", "/internal/turnuses", basic("test-reader-password")).statusCode());
    }

    @Test void operatorLoginAndJwtCatalogueAccessStillWork() throws Exception {
        var login = HttpRequest.newBuilder(URI.create("http://localhost:"
                + context.getWebServer().getPort() + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"test-operator\",\"password\":\"test-operator-password\"}"))
                .build();
        var response = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String token = JsonPath.read(response.body(), "$.token");
        assertEquals(200, request("GET", "/api/turnuses", "Bearer " + token).statusCode());
        assertEquals(401, request("GET", "/internal/turnuses", "Bearer " + token).statusCode());
    }

    @Test void missingBrowserResourcesReturn404InsteadOfServerError() throws Exception {
        for (String path : new String[]{"/", "/favicon.ico"}) {
            var response = request("GET", path, null);
            assertEquals(404, response.statusCode());
            assertEquals(404, (int) JsonPath.read(response.body(), "$.status"));
            assertEquals("Nie znaleziono zasobu.", JsonPath.read(response.body(), "$.message"));
        }
    }

    @Test void listsRequireAnOperatorAndUnknownTurnusReturns404() throws Exception {
        assertEquals(403, request("GET", "/api/turnuses/ZAGLE26T1/lists", null).statusCode());
        assertEquals(403, request("GET", "/api/turnuses/ZAGLE26T1/lists", "Bearer invalid-token").statusCode());
        assertEquals(404, request("GET", "/api/turnuses/MISSING/lists",
                "Bearer " + jwt.generateToken("test-operator")).statusCode());
    }
}
