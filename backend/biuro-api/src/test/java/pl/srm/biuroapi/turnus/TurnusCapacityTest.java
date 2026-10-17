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
import pl.srm.biuroapi.auth.OperatorTestTokens;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "registration.api.password=test-service-password",
        "turnus.lookup.password=test-lookup-password",
        "spring.datasource.url=jdbc:h2:mem:capacity-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class TurnusCapacityTest {
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    pl.srm.biuroapi.registration.client.RegistrationApiClient registrations;
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
        token = "Bearer " + OperatorTestTokens.create(jdbc, jwt, "biuro");
    }
    TurnusRequest request(String code, Long version, int participantCapacity) {
        return new TurnusRequest(code, "Turnus testowy", "Opis", "Miejsce", TurnusDefinition.SeasonType.SUMMER,
                2026, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 11), 14, participantCapacity, 10, true, true, true, version, List.of());
    }
    TurnusRequest capacities(String code, Long version, int participants, int staff, boolean participantOpen, boolean staffOpen) {
        var value = request(code, version, participants);
        return new TurnusRequest(value.turnusCode(), value.turnusName(), value.turnusDescription(), value.turnusLocation(),
                value.seasonType(), value.seasonYear(), value.startDate(), value.endDate(), value.minAge(),
                participants, staff, value.active(), participantOpen, staffOpen, version, List.of());
    }
    @Test void migratesLegacyCapacityAndInitialStaffCapacity() {
        var value = catalogue.get("ZAGLE26T1");
        assertEquals(60, value.participantCapacity());
        assertEquals(10, value.staffCapacity());
        assertEquals(value.participantRegistrationOpen(), value.staffRegistrationOpen());
    }
    @Test void databaseRejectsZeroForBothCapacities() {
        catalogue.create(request("TEST1", null, 60));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE turnus SET participant_capacity = 0 WHERE turnus_code = 'TEST1'"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE turnus SET staff_capacity = 0 WHERE turnus_code = 'TEST1'"));
    }
    @Test void independentSwitchesPersistAndIncreaseDoesNotRequireUpstream() {
        catalogue.create(capacities("TEST1", null, 60, 10, false, true));
        var updated = catalogue.update("TEST1", capacities("TEST1", 0L, 70, 12, true, false));
        assertEquals(12, updated.staffCapacity());
        assertTrue(updated.participantRegistrationOpen());
        assertFalse(updated.staffRegistrationOpen());
        org.mockito.Mockito.verifyNoInteractions(registrations);
    }
    @Test void reductionsAtAcceptedCountsAreAllowedButBelowEitherCountAreRejected() {
        catalogue.create(request("TEST1", null, 60));
        org.mockito.Mockito.when(registrations.fetchAcceptedCounts("TEST1"))
                .thenReturn(new pl.srm.biuroapi.registration.client.RegistrationApiClient.AcceptedCounts(42L, 8L));
        for (var invalid : new TurnusRequest[]{capacities("TEST1", 0L, 41, 10, true, true),
                capacities("TEST1", 0L, 60, 7, true, true)}) {
            var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> catalogue.update("TEST1", invalid));
            assertEquals(422, error.getStatusCode().value());
            assertEquals(0, catalogue.get("TEST1").version());
            assertEquals(60, catalogue.get("TEST1").participantCapacity());
            assertEquals(10, catalogue.get("TEST1").staffCapacity());
        }
        var value = catalogue.update("TEST1", capacities("TEST1", 0L, 42, 8, true, true));
        assertEquals(42, value.participantCapacity());
        assertEquals(8, value.staffCapacity());
    }
    @Test void failedCountLookupPreventsReductionAndPreservesAllFields() {
        catalogue.create(request("TEST1", null, 60));
        org.mockito.Mockito.when(registrations.fetchAcceptedCounts("TEST1"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> catalogue.update("TEST1", capacities("TEST1", 0L, 59, 9, false, false)));
        var value = catalogue.get("TEST1");
        assertEquals(0, value.version());
        assertEquals(60, value.participantCapacity());
        assertTrue(value.participantRegistrationOpen());
    }
    @Test void zeroStaffCapacityAndMissingSwitchAreRejectedOverHttp() throws Exception {
        String body = """
                {"turnusCode":"TEST-ZERO","turnusName":"Test","turnusLocation":"Place","seasonType":"SUMMER",
                 "seasonYear":2026,"startDate":"2026-08-01","endDate":"2026-08-10","minAge":14,
                 "participantCapacity":60,"staffCapacity":0,"active":true,
                 "participantRegistrationOpen":true,"staffRegistrationOpen":false}
                """;
        mvc.perform(post("/api/turnuses").header("Authorization", token)
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/turnuses").header("Authorization", token)
                .contentType("application/json").content(body.replace("\"staffCapacity\":0", "\"staffCapacity\":10")
                        .replace("\"staffRegistrationOpen\":false", "\"staffRegistrationOpen\":null")))
                .andExpect(status().isBadRequest());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> catalogue.get("TEST-ZERO"));
    }
}
