package pl.srm.biuroapi.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.auth.config.JwtConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "registration.api.password=test-service-password", "turnus.lookup.password=test-lookup-password",
        "spring.datasource.url=jdbc:h2:mem:account-auth;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class AccountAuthenticationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_BIURO_ACCOUNTS_DB_URL");
        if (url == null || url.isBlank()) return;
        if (!url.matches("jdbc:mariadb://[^/]+/biuro_accounts_test"))
            throw new IllegalArgumentException("Use only a disposable biuro_accounts_test schema.");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_PASSWORD"));
    }

    @Autowired ServletWebServerApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtConfig jwt;
    final HttpClient client = HttpClient.newHttpClient();
    String id;
    static final String PASSWORD = "original-password-123";

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM biuro_session");
        jdbc.update("DELETE FROM biuro_account_role");
        jdbc.update("DELETE FROM biuro_account");
        id = UUID.randomUUID().toString();
        accounts.create(id, "anna.kowalska", "Anna Kowalska", encoder.encode(PASSWORD),
                Set.of(AccountRole.OPERATOR), Instant.now());
    }

    HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + context.getWebServer().getPort() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> loginResponse(String username, String password) throws Exception {
        return request("POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }
    String login() throws Exception {
        var response = loginResponse(" ANNA.KOWALSKA ", PASSWORD);
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("passwordHash"));
        assertFalse(response.body().contains(PASSWORD));
        assertEquals("anna.kowalska", JsonPath.read(response.body(), "$.user.username"));
        return JsonPath.read(response.body(), "$.token");
    }
    void permitNormalAccess() {
        jdbc.update("UPDATE biuro_account SET password_change_required = FALSE WHERE id = ?", id);
    }

    @Test void loginUsesStoredHashAndRequiresPasswordChangeBeforeOperationalAccess() throws Exception {
        String hash = jdbc.queryForObject("SELECT password_hash FROM biuro_account WHERE id = ?", String.class, id);
        assertNotEquals(PASSWORD, hash);
        assertTrue(encoder.matches(PASSWORD, hash));
        String token = login();
        assertEquals(200, request("GET", "/api/auth/me", token, null).statusCode());
        assertEquals(403, request("GET", "/api/turnuses", token, null).statusCode());
        assertEquals(401, loginResponse("anna.kowalska", "wrong-password").statusCode());
        assertEquals(401, loginResponse("unknown", PASSWORD).statusCode());
        assertEquals(401, loginResponse("biuro", "biuro123").statusCode());
        var claims = jwt.parseClaims(token);
        assertEquals(7200, (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
    }

    @Test void normalizedUsernamesAreUniqueAndMigrationPreservesCatalogue() {
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> accounts.create(
                UUID.randomUUID().toString(), "ANNA.KOWALSKA", "Duplicate", encoder.encode(PASSWORD),
                Set.of(AccountRole.VIEWER), Instant.now()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM turnus", Integer.class));
        assertEquals(60, jdbc.queryForObject(
                "SELECT participant_capacity FROM turnus WHERE turnus_code = 'ZAGLE26T1'", Integer.class));
    }

    @Test void malformedAndOversizedCredentialsReturnClientErrors() throws Exception {
        assertEquals(400, request("POST", "/api/auth/login", null, "{bad json").statusCode());
        assertEquals(400, loginResponse("anna.kowalska", "x".repeat(73)).statusCode());
        assertEquals(401, loginResponse("anna.kowalska", "ą".repeat(40)).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
    }

    @Test void passwordChangeReplacesSessionAndRevokesEveryOlderSession() throws Exception {
        String first = login(), second = login();
        var changed = request("POST", "/api/auth/password", first,
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"new-password-456789\"}");
        assertEquals(200, changed.statusCode());
        assertEquals(false, JsonPath.read(changed.body(), "$.user.passwordChangeRequired"));
        String replacement = JsonPath.read(changed.body(), "$.token");
        assertEquals(401, request("GET", "/api/auth/me", first, null).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", second, null).statusCode());
        assertEquals(200, request("GET", "/api/turnuses", replacement, null).statusCode());
        assertEquals(401, loginResponse("anna.kowalska", PASSWORD).statusCode());
        assertEquals(200, loginResponse("anna.kowalska", "new-password-456789").statusCode());
    }

    @Test void invalidPasswordChangesDoNotReplaceCredentialsOrSessions() throws Exception {
        String token = login();
        for (String next : new String[]{"short", PASSWORD, "ą".repeat(40)}) {
            assertEquals(400, request("POST", "/api/auth/password", token,
                    "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + next + "\"}").statusCode());
        }
        assertEquals(400, request("POST", "/api/auth/password", token,
                "{\"currentPassword\":\"wrong\",\"newPassword\":\"different-password-123\"}").statusCode());
        assertEquals(200, request("GET", "/api/auth/me", token, null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
        assertEquals(200, loginResponse("anna.kowalska", PASSWORD).statusCode());
    }

    @Test void logoutRevokesOnlyCurrentSessionAndDisableRejectsExistingTokens() throws Exception {
        permitNormalAccess();
        String first = login(), second = login();
        assertEquals(204, request("POST", "/api/auth/logout", first, null).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", first, null).statusCode());
        assertEquals(200, request("GET", "/api/auth/me", second, null).statusCode());
        jdbc.update("UPDATE biuro_account SET active = FALSE WHERE id = ?", id);
        assertEquals(401, request("GET", "/api/auth/me", second, null).statusCode());
        assertEquals(401, loginResponse("anna.kowalska", PASSWORD).statusCode());
    }

    @Test void currentRolesAreReadFromDatabaseAndAdminDoesNotImplyOperator() throws Exception {
        permitNormalAccess();
        String token = login();
        assertEquals(200, request("GET", "/api/turnuses", token, null).statusCode());
        jdbc.update("DELETE FROM biuro_account_role WHERE account_id = ?", id);
        jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, 'SYS_ADMIN')", id);
        assertEquals(403, request("GET", "/api/turnuses", token, null).statusCode());
        jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, 'OPERATOR')", id);
        assertEquals(200, request("GET", "/api/turnuses", token, null).statusCode());
        jdbc.update("DELETE FROM biuro_account_role WHERE account_id = ? AND role = 'OPERATOR'", id);
        jdbc.update("INSERT INTO biuro_account_role (account_id, role) VALUES (?, 'VIEWER')", id);
        assertEquals(403, request("GET", "/api/turnuses/ZAGLE26T1", token, null).statusCode());
        assertEquals(403, request("POST", "/api/turnuses", token, "{}").statusCode());
    }

    @Test void rejectsMissingMalformedTamperedExpiredAndUnstoredSessions() throws Exception {
        permitNormalAccess();
        String valid = login();
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        String expired = jwt.generateToken(id, UUID.randomUUID().toString(), Instant.now().minusSeconds(10800));
        String notStored = jwt.generateToken(id, UUID.randomUUID().toString(), Instant.now());
        for (String token : new String[]{null, "not.a.token", tampered, expired, notStored})
            assertEquals(401, request("GET", "/api/turnuses", token, null).statusCode());
        jdbc.update("UPDATE biuro_session SET expires_at = ? WHERE account_id = ?", Timestamp.from(Instant.EPOCH), id);
        assertEquals(401, request("GET", "/api/auth/me", valid, null).statusCode());
    }

    @Test void sessionStorageOutageReturns503WithoutRevokingSessionOrLeakingDetails() throws Exception {
        permitNormalAccess();
        String token = login();
        jdbc.execute("ALTER TABLE biuro_session RENAME TO biuro_session_unavailable");
        try {
            for (var response : java.util.List.of(request("GET", "/api/auth/me", token, null),
                    loginResponse("anna.kowalska", PASSWORD))) {
                assertEquals(503, response.statusCode());
                String body = response.body().toString();
                assertEquals(503, (int) JsonPath.read(body, "$.status"));
                assertEquals("Usługa jest chwilowo niedostępna. Spróbuj ponownie później.", JsonPath.read(body, "$.message"));
                assertFalse(body.contains("SELECT"));
                assertFalse(body.contains("biuro_session_unavailable"));
                assertFalse(body.contains(token));
            }
        } finally {
            jdbc.execute("ALTER TABLE biuro_session_unavailable RENAME TO biuro_session");
        }
        assertEquals(200, request("GET", "/api/auth/me", token, null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_session", Integer.class));
    }
}
