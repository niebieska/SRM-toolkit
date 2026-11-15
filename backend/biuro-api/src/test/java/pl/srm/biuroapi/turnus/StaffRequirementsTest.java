package pl.srm.biuroapi.turnus;

import jakarta.servlet.Filter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.config.JwtConfig;
import pl.srm.biuroapi.auth.OperatorTestTokens;
import pl.srm.biuroapi.season.StaffingCategories;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "registration.api.password=test-service-password", "turnus.lookup.password=test-lookup-password",
        "spring.datasource.url=jdbc:h2:mem:staff-requirements;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class StaffRequirementsTest {
    @Autowired TurnusCatalogue catalogue;
    @Autowired StaffingCategories categories;
    @Autowired StaffRequirements requirements;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired JwtConfig jwt;
    MockMvc mvc;
    String token;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM turnus WHERE turnus_code LIKE 'STAFFTEST%'");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        token = "Bearer " + OperatorTestTokens.create(jdbc, jwt, "biuro");
    }
    TurnusRequest request(Long version, List<StaffRequirement> staffing) {
        return new TurnusRequest("STAFFTEST1", "Winter", null, "Place", SeasonType.WINTER,
                2027, LocalDate.of(2027, 1, 17), LocalDate.of(2027, 1, 24), 14,
                50, 10, false, false, false, version, staffing);
    }
    @Test void persistsEditsClearsAndPreservesRequirementsForOlderClients() {
        var original = List.of(new StaffRequirement("animator", 6), new StaffRequirement("ksiadz_prowadzacy", 1));
        assertEquals(original, catalogue.create(request(null, original)).staffRequirements());
        assertEquals(original, requirements.read(jdbc.queryForObject(
                "SELECT staff_requirements FROM turnus WHERE turnus_code = 'STAFFTEST1'", String.class)));
        assertEquals(original, catalogue.update("STAFFTEST1", request(0L, null)).staffRequirements());
        var changed = List.of(new StaffRequirement("animator", 4), new StaffRequirement("kucharka", 0));
        assertEquals(List.of(new StaffRequirement("animator", 4)),
                catalogue.update("STAFFTEST1", request(1L, changed)).staffRequirements());
        assertEquals(List.of(), catalogue.update("STAFFTEST1", request(2L, List.of())).staffRequirements());
    }
    @Test void migrationAndMissingRequirementsLeaveExistingTurnusyUnconfigured() {
        assertEquals(List.of(), catalogue.get("ZAGLE26T1").staffRequirements());
        assertEquals(List.of(), catalogue.create(request(null, null)).staffRequirements());
    }
    @Test void invalidOrStaleEditsCannotAlterDefinitionOrRequirements() {
        catalogue.create(request(null, List.of(new StaffRequirement("animator", 6))));
        var before = catalogue.get("STAFFTEST1");
        for (var invalid : List.of(
                List.of(new StaffRequirement("animator", -1)),
                List.of(new StaffRequirement("sternik", 1)),
                List.of(new StaffRequirement("animator", 1), new StaffRequirement("animator", 2)))) {
            var failure = assertThrows(ResponseStatusException.class,
                    () -> catalogue.update("STAFFTEST1", request(0L, invalid)));
            assertEquals(400, failure.getStatusCode().value());
            assertEquals(before, catalogue.get("STAFFTEST1"));
        }
        catalogue.update("STAFFTEST1", request(0L, List.of(new StaffRequirement("animator", 8))));
        var conflict = assertThrows(ResponseStatusException.class,
                () -> catalogue.update("STAFFTEST1", request(0L, List.of())));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(8, catalogue.get("STAFFTEST1").staffRequirements().getFirst().requiredCount());
        assertThrows(ResponseStatusException.class, () -> requirements.validate(
                List.of(new StaffRequirement("instruktor_narty", 2)), SeasonType.SUMMER));
    }
    @Test void WinterGroupingMatchesAgreedRolesWithoutDoubleCountingPriests() {
        var winter = categories.get(SeasonType.WINTER);
        assertEquals(7, winter.size());
        var animator = winter.stream().filter(c -> c.code().equals("animator")).findFirst().orElseThrow();
        assertEquals(List.of(new StaffingCategories.Selection("animator", null),
                new StaffingCategories.Selection("animator", "kandydat_na_animatora")), animator.selections());
        var priest = winter.stream().filter(c -> c.code().equals("ksiadz")).findFirst().orElseThrow();
        assertTrue(priest.selections().contains(new StaffingCategories.Selection("ksiadz", "instruktor_narty")));
        assertFalse(priest.selections().contains(new StaffingCategories.Selection("ksiadz", "prowadzacy")));
        assertEquals(8, categories.get(SeasonType.SUMMER).size());
    }
    @Test void categoryEndpointRequiresOperatorAndSupportsBothSeasons() throws Exception {
        mvc.perform(get("/api/turnuses/staffing-categories").param("seasonType", "WINTER")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/turnuses/staffing-categories").param("seasonType", "WINTER")
                .header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/turnuses/staffing-categories").param("seasonType", "SUMMER")
                .header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
        mvc.perform(get("/api/turnuses/staffing-categories").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/turnuses/staffing-categories").param("seasonType", "INVALID")
                .header("Authorization", token)).andExpect(status().isBadRequest());
    }
    @Test void feeOptionsEndpointRequiresOperatorAndSupportsBothSeasons() throws Exception {
        mvc.perform(get("/api/turnuses/staff-fee-options").param("seasonType", "WINTER"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/turnuses/staff-fee-options").param("seasonType", "WINTER")
                .header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[?(@.role == 'instruktor' && @.subrole == 'narty')]").exists());
        mvc.perform(get("/api/turnuses/staff-fee-options").param("seasonType", "SUMMER")
                .header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.role == 'animator' && @.subrole == 'animator_wychowawca')]").exists());
        mvc.perform(get("/api/turnuses/staff-fee-options").param("seasonType", "INVALID")
                .header("Authorization", token)).andExpect(status().isBadRequest());
    }
    @Test void successfulHttpSaveAndReadReturnRequirementsAndKeepManualFlags() throws Exception {
        catalogue.create(request(null, List.of(new StaffRequirement("animator", 6))));
        mvc.perform(get("/api/turnuses/STAFFTEST1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.staffRequirements[0].requiredCount").value(6))
                .andExpect(jsonPath("$.staffRegistrationOpen").value(false));
    }
    @Test void requestRejectsFractionalNegativeAndUnknownRequirements() throws Exception {
        String body = """
            {"turnusCode":"STAFFTEST1","turnusName":"Winter","turnusLocation":"Place",
             "seasonType":"WINTER","seasonYear":2027,"startDate":"2027-01-17","endDate":"2027-01-24",
             "minAge":14,"participantCapacity":50,"staffCapacity":10,"active":false,
             "participantRegistrationOpen":false,"staffRegistrationOpen":false,"staffRequirements":%s}
            """;
        for (String value : List.of("[{\"categoryCode\":\"animator\",\"requiredCount\":-1}]",
                "[{\"categoryCode\":\"animator\",\"requiredCount\":null}]", "[null]",
                "[{\"categoryCode\":\"animator\",\"requiredCount\":1.5}]",
                "[{\"categoryCode\":\"animator\",\"requiredCount\":\"2\"}]",
                "[{\"categoryCode\":\"unknown\",\"requiredCount\":1}]")) {
            mvc.perform(post("/api/turnuses").header("Authorization", token).contentType("application/json")
                    .content(body.formatted(value))).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM turnus WHERE turnus_code = 'STAFFTEST1'", Integer.class));
    }
    @Test void requestRejectsInvalidFeesBeforePersistingTurnus() throws Exception {
        String body = """
            {"turnusCode":"STAFFTEST-FEE","turnusName":"Winter","turnusLocation":"Place",
             "seasonType":"WINTER","seasonYear":2027,"startDate":"2027-01-17","endDate":"2027-01-24",
             "minAge":14,"participantCapacity":50,"staffCapacity":10,"active":false,
             "participantRegistrationOpen":false,"staffRegistrationOpen":false,
             "participantFee":%s,"staffFees":%s}
            """;
        mvc.perform(post("/api/turnuses").header("Authorization", token).contentType("application/json")
                .content(body.replace("STAFFTEST-FEE", "STAFFTEST-FEE-OK").formatted("0",
                        "[{\"role\":\"animator\",\"subrole\":\"kandydat_na_animatora\",\"amount\":750}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffFees[0].role").value("animator"))
                .andExpect(jsonPath("$.staffFees[0].subrole").value("kandydat_na_animatora"));
        for (String[] invalid : List.of(
                new String[]{"-1", "[]"},
                new String[]{"10.001", "[]"},
                new String[]{"1200", "[{\"role\":\"unknown\",\"subrole\":null,\"amount\":100}]"},
                new String[]{"1200", "[{\"role\":\"kucharka\",\"subrole\":null,\"amount\":100}]"},
                new String[]{"1200", "[{\"role\":\"animator\",\"subrole\":null,\"amount\":100}]"},
                new String[]{"1200", "[{\"role\":\"kucharka\",\"subrole\":null,\"amount\":100},{\"role\":\"kucharka\",\"subrole\":null,\"amount\":200}]"},
                new String[]{"1200", "[{\"role\":\"animator\",\"subrole\":null,\"amount\":100},{\"role\":\"animator\",\"subrole\":\"kandydat_na_animatora\",\"amount\":50}]"})) {
            mvc.perform(post("/api/turnuses").header("Authorization", token).contentType("application/json")
                    .content(body.formatted(invalid[0], invalid[1]))).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM turnus WHERE turnus_code = 'STAFFTEST-FEE'", Integer.class));
    }

    @Test void summerCandidateFeeUsesOneRateForBothCatalogRepresentations() {
        var base = new TurnusRequest("STAFFTEST-SUMMER-FEE", "Summer", null, "Place", SeasonType.SUMMER,
                2027, LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 8), 14, 50, 10,
                false, false, false, null, List.of(), java.math.BigDecimal.ZERO,
                List.of(new StaffFee("kandydat_na_animatora", null, new java.math.BigDecimal("750"))));
        var configured = catalogue.create(base);
        assertEquals(2, configured.staffFees().size());
        assertEquals(0, configured.staffFees().get(0).amount().compareTo(configured.staffFees().get(1).amount()));

        var otherRepresentation = new TurnusRequest("STAFFTEST-SUMMER-FEE-ADULT", "Summer", null, "Place",
                SeasonType.SUMMER, 2027, LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 8), 14, 50, 10,
                false, false, false, null, List.of(), java.math.BigDecimal.ZERO,
                List.of(new StaffFee("animator", "kandydat_na_animatora", new java.math.BigDecimal("750"))));
        assertEquals(2, catalogue.create(otherRepresentation).staffFees().size());

        var ineligibleRole = new TurnusRequest("STAFFTEST-SUMMER-FEE-OTHER", "Summer", null, "Place",
                SeasonType.SUMMER, 2027, LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 8), 14, 50, 10,
                false, false, false, null, List.of(), java.math.BigDecimal.ZERO,
                List.of(new StaffFee("kucharka", null, new java.math.BigDecimal("750"))));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> catalogue.create(ineligibleRole))
                .getStatusCode().value());

        var mismatchedVariants = new TurnusRequest("STAFFTEST-SUMMER-FEE-MISMATCH", "Summer", null, "Place",
                SeasonType.SUMMER, 2027, LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 8), 14, 50, 10,
                false, false, false, null, List.of(), java.math.BigDecimal.ZERO, List.of(
                        new StaffFee("kandydat_na_animatora", null, new java.math.BigDecimal("750")),
                        new StaffFee("animator", "kandydat_na_animatora", new java.math.BigDecimal("800"))));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> catalogue.create(mismatchedVariants))
                .getStatusCode().value());
    }

    @Test void firstFeeConfigurationOfAnExistingTurnusUsesCandidateOnlyRule() {
        var created = catalogue.create(new TurnusRequest("STAFFTEST-FIRST-FEE", "Winter", null, "Place",
                SeasonType.WINTER, 2027, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 8), 14, 50, 10,
                false, false, false, null, List.of(), java.math.BigDecimal.ZERO, List.of()));
        jdbc.update("UPDATE turnus SET fees_configured = FALSE WHERE turnus_code = ?", created.turnusCode());
        var current = catalogue.get(created.turnusCode());

        var invalid = requestWithFees(current, List.of(new StaffFee("kucharka", null,
                new java.math.BigDecimal("200"))));
        var failure = assertThrows(ResponseStatusException.class,
                () -> catalogue.update(current.turnusCode(), invalid));
        assertEquals(400, failure.getStatusCode().value());

        var valid = requestWithFees(current, List.of(new StaffFee("animator", "kandydat_na_animatora",
                new java.math.BigDecimal("750"))));
        var configured = catalogue.update(current.turnusCode(), valid);
        assertTrue(configured.feesConfigured());
        assertEquals(1, configured.staffFees().size());
    }

    private TurnusRequest requestWithFees(TurnusDefinition current, List<StaffFee> fees) {
        return new TurnusRequest(current.turnusCode(), current.turnusName(), current.turnusDescription(),
                current.turnusLocation(), current.seasonType(), current.seasonYear(), current.startDate(),
                current.endDate(), current.minAge(), current.participantCapacity(), current.staffCapacity(),
                current.active(), current.participantRegistrationOpen(), current.staffRegistrationOpen(),
                current.version(), current.staffRequirements(), current.participantFee(), fees);
    }
}
