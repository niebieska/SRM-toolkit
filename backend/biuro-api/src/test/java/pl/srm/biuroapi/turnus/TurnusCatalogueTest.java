package pl.srm.biuroapi.turnus;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pl.srm.biuroapi.auth.config.JwtConfig;
import java.time.LocalDate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "registration.api.password=test-service-password",
        "turnus.lookup.password=test-lookup-password",
        "biuro.auth.username=test-operator", "biuro.auth.password=test-only-password",
        "spring.datasource.url=jdbc:h2:mem:turnus-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class TurnusCatalogueTest {
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_BIURO_DB_URL");
        if (url == null || url.isBlank()) return;
        if (!url.matches("jdbc:mariadb://[^/]+/biuro_turnus_test"))
            throw new IllegalArgumentException("Use only a disposable biuro_turnus_test schema.");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("TEST_BIURO_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_BIURO_DB_PASSWORD"));
    }
    @Autowired TurnusCatalogue catalogue;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired JwtConfig jwt;
    MockMvc mvc;
    String token;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM turnus WHERE turnus_code LIKE 'TEST%'");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        token = "Bearer " + jwt.generateToken("biuro");
    }
    TurnusRequest request(String code, Long version, int participantCapacity) {
        return new TurnusRequest(code, "Turnus testowy", "Opis", "Miejsce", TurnusDefinition.SeasonType.SUMMER,
                2026, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 11), 14, participantCapacity, 10, true, true, true, version);
    }
    String payload(String code, int participantCapacity) {
        return """
                {"turnusCode":"%s","turnusName":"Turnus testowy","turnusLocation":"Miejsce",
                 "seasonType":"SUMMER","seasonYear":2026,"startDate":"2026-08-01","endDate":"2026-08-11",
                 "minAge":14,"participantCapacity":%d,"staffCapacity":10,"active":true,"participantRegistrationOpen":true,"staffRegistrationOpen":true,"version":0}
                """.formatted(code, participantCapacity);
    }
    @Test void importsExistingCodesAndExactDatesCapacity() {
        assertEquals(60, catalogue.get("ZAGLE26T1").participantCapacity());
        assertEquals(LocalDate.of(2026, 8, 22), catalogue.get("ZAGLE26T2").endDate());
    }
    @Test void createEditAndDuplicateCode() {
        var created = catalogue.create(request("TEST1", null, 60));
        assertEquals(0, created.version());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> catalogue.create(request("TEST1", null, 60)));
        var updated = catalogue.update("TEST1", request("TEST1", 0L, 70));
        assertEquals(1, updated.version());
        assertEquals(70, catalogue.get("TEST1").participantCapacity());
    }
    @Test void refusesCodeChangesMissingVersionAndStaleEdits() {
        catalogue.create(request("TEST1", null, 60));
        for (var invalid : new TurnusRequest[]{request("TEST2", 0L, 70), request("TEST1", null, 70)})
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> catalogue.update("TEST1", invalid));
        catalogue.update("TEST1", request("TEST1", 0L, 70));
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> catalogue.update("TEST1", request("TEST1", 0L, 80)));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(70, catalogue.get("TEST1").participantCapacity());
    }
    @Test void concurrentEditsCannotSilentlyOverwriteEachOther() throws Exception {
        catalogue.create(request("TEST1", null, 60));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        Callable<Boolean> edit = () -> {
            gate.await();
            try { catalogue.update("TEST1", request("TEST1", 0L, 70)); return true; }
            catch (org.springframework.web.server.ResponseStatusException ex) {
                assertEquals(409, ex.getStatusCode().value()); return false;
            }
        };
        try {
            var a = pool.submit(edit); var b = pool.submit(edit); gate.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, catalogue.get("TEST1").version());
        } finally { pool.shutdownNow(); }
    }
    @Test void protectedRoutesRejectMissingAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/turnuses")).andExpect(status().isForbidden());
        mvc.perform(post("/api/turnuses").header("Authorization", "Bearer invalid")
                .contentType("application/json").content(payload("TEST1", 60))).andExpect(status().isForbidden());
        assertEquals(2, catalogue.list().size());
    }
    @Test void loginIssuedTokenWorksWithBrowserOriginAndPreflight() throws Exception {
        mvc.perform(options("/api/turnuses")
                .header("Origin", "http://localhost:5174")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
        String login = mvc.perform(post("/api/auth/login")
                .header("Origin", "http://localhost:5174")
                .contentType("application/json")
                .content("{\"username\":\"test-operator\",\"password\":\"test-only-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String issuedToken = com.jayway.jsonpath.JsonPath.read(login, "$.token");
        mvc.perform(get("/api/turnuses").header("Origin", "http://localhost:5174")
                .header("Authorization", "Bearer " + issuedToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void operatorCanCreateReadAndEditThroughHttp() throws Exception {
        mvc.perform(post("/api/turnuses").header("Authorization", token)
                .contentType("application/json").content(payload("TEST1", 60)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.turnusCode").value("TEST1"));
        mvc.perform(patch("/api/turnuses/TEST1").header("Authorization", token)
                .contentType("application/json").content(payload("TEST1", 70)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/turnuses/TEST1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantCapacity").value(70));
    }
    @Test void invalidRequestsNeverPersist() throws Exception {
        for (String body : new String[]{payload("TEST1", 0), payload("TEST1", 60).replace("\"staffCapacity\":10", "\"staffCapacity\":0"), payload("TEST1", 60).replace("2026-08-11", "2026-07-01"),
                payload("TEST1", 60).replace("SUMMER", "UNKNOWN"), "{bad json", payload("TEST1", 60).replace("true", "null")}) {
            mvc.perform(post("/api/turnuses").header("Authorization", token)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(2, catalogue.list().size());
    }
    @Test void missingAndDuplicateCodesUseBusinessStatuses() throws Exception {
        mvc.perform(get("/api/turnuses/MISSING").header("Authorization", token)).andExpect(status().isNotFound());
        catalogue.create(request("TEST1", null, 60));
        mvc.perform(post("/api/turnuses").header("Authorization", token)
                .contentType("application/json").content(payload("TEST1", 60))).andExpect(status().isConflict());
    }
}
