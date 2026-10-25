package pl.srm.biuroapi.auth.management;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.srm.biuroapi.BiuroApiApplication;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.TurnusListPerson;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "registration.api.password=test-service-password", "turnus.lookup.password=test-lookup-password",
        "biuro.bootstrap.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:account-management;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="},
        classes = {BiuroApiApplication.class, AccountManagementHttpSupport.Hashing.class})
abstract class AccountManagementHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_BIURO_ACCOUNTS_DB_URL");
        if (url == null || url.isBlank()) return;
        if (!url.matches("jdbc:mariadb://[^/]+/biuro_accounts_test")) throw new IllegalArgumentException("Use a disposable biuro_accounts_test schema.");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_BIURO_ACCOUNTS_DB_PASSWORD"));
    }
    @Autowired ServletWebServerApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired AccountManagementRepository management;
    @Autowired AccountManagementService service;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoBean RegistrationApiClient registrations;
    final HttpClient client = HttpClient.newHttpClient();
    static final String PASSWORD = "test-password-123456";
    String adminId, adminToken, operatorId, operatorToken, viewerId, viewerToken;

    @BeforeEach void fixtures() throws Exception {
        jdbc.update("DELETE FROM biuro_account_audit");
        jdbc.update("DELETE FROM biuro_session");
        jdbc.update("DELETE FROM biuro_account_role");
        jdbc.update("DELETE FROM biuro_account"); // Assignments cascade; no real environment is used.
        adminId = account("admin", Set.of(AccountRole.SYS_ADMIN), Set.of());
        operatorId = account("operator", Set.of(AccountRole.OPERATOR), Set.of());
        viewerId = account("viewer", Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T1"));
        adminToken = login("admin", PASSWORD); operatorToken = login("operator", PASSWORD); viewerToken = login("viewer", PASSWORD);
        reset(registrations);
        when(registrations.fetchTurnusList(anyString())).thenReturn(List.of(new TurnusListPerson(
                "SYNTHETIC-P1", "PARTICIPANT", "Anna", "Testowa", "00210112345",
                new TurnusListPerson.Address("Testowa", "1", "00-000", "Testowo"), LocalDate.of(2000, 1, 1),
                "female", "001234567", null, "009876543", null, null, false, false,
                new TurnusListPerson.AdditionalData("test@example.invalid", "Jan", "Testowy", "inna", "Kontakt",
                        null, List.of(), false, null, null))));
    }
    String account(String username, Set<AccountRole> roles, Set<String> codes) {
        String id = UUID.randomUUID().toString();
        accounts.create(id, username, username, encoder.encode(PASSWORD), roles, Instant.now());
        jdbc.update("UPDATE biuro_account SET password_change_required = FALSE WHERE id = ?", id);
        management.replaceAssignments(id, codes);
        return id;
    }
    HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + context.getWebServer().getPort() + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    String login(String username, String password) throws Exception {
        var response = request("POST", "/api/auth/login", null, Map.of("username", username, "password", password));
        assertEquals(200, response.statusCode(), response.body());
        return JsonPath.read(response.body(), "$.token");
    }
    HttpResponse<String> update(String token, String id, long version, boolean active, Set<AccountRole> roles, Set<String> codes) throws Exception {
        return request("PATCH", "/api/admin/accounts/" + id, token, Map.of("version", version, "displayName", "Updated",
                "active", active, "roles", roles, "turnusCodes", codes));
    }
    long events(String type, String target) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account_audit WHERE event_type = ? AND target_account_id = ?", Long.class, type, target);
    }
    @TestConfiguration static class Hashing {
        @Bean @Primary PasswordEncoder testPasswords() { return new BCryptPasswordEncoder(4); }
    }
}
