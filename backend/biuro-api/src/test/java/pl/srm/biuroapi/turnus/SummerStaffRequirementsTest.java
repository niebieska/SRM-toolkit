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
import pl.srm.biuroapi.auth.config.JwtConfig;
import pl.srm.biuroapi.season.StaffingCategories;
import pl.srm.biuroapi.season.StaffingCategories.Selection;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "registration.api.password=test-service-password", "turnus.lookup.password=test-lookup-password",
        "biuro.auth.username=test-operator", "biuro.auth.password=test-only-password",
        "spring.datasource.url=jdbc:h2:mem:staff-requirements;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class SummerStaffRequirementsTest {
    @Autowired TurnusCatalogue catalogue;
    @Autowired StaffingCategories categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired JwtConfig jwt;
    MockMvc mvc;
    String token;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM turnus WHERE turnus_code LIKE 'SUMMERSTAFF%'");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        token = "Bearer " + jwt.generateToken("biuro");
    }
    private List<Selection> selections(String code) {
        return categories.get(SeasonType.SUMMER).stream().filter(c -> c.code().equals(code))
                .findFirst().orElseThrow().selections();
    }
    private TurnusRequest request(Long version, List<StaffRequirement> staffing) {
        return new TurnusRequest("SUMMERSTAFF1", "Summer", null, "Place", SeasonType.SUMMER,
                2027, LocalDate.of(2027, 8, 1), LocalDate.of(2027, 8, 11), 14,
                50, 10, true, false, true, version, staffing);
    }
    @Test void mapsSharedAnimatorAndSternikTargetsWithoutDoubleCountingPriests() {
        assertEquals(8, categories.get(SeasonType.SUMMER).size());
        assertEquals(List.of(new Selection("kandydat_na_animatora", null),
                new Selection("animator", "kandydat_na_animatora"), new Selection("animator", "animator")), selections("animator"));
        assertEquals(List.of(new Selection("animator", "animator_wychowawca")), selections("animator_wychowawca"));
        assertEquals(List.of(new Selection("sternik", null), new Selection("sternik_z_opiekunem", null),
                new Selection("ksiadz", "sternik")), selections("sternik"));
        assertEquals(List.of(new Selection("ksiadz", "prowadzacy")), selections("ksiadz_prowadzacy"));
        assertEquals(List.of(new Selection("ksiadz", "wychowawca"), new Selection("ksiadz", "pomoc")), selections("ksiadz"));
        var all = categories.get(SeasonType.SUMMER).stream().flatMap(c -> c.selections().stream()).toList();
        assertEquals(all.size(), all.stream().distinct().count());
    }
    @Test void createsEditsAndClearsSummerTargetsWithoutCapacityCoupling() {
        assertEquals(List.of(), catalogue.get("ZAGLE26T1").staffRequirements());
        var targets = List.of(new StaffRequirement("sternik", 5), new StaffRequirement("animator", 20),
                new StaffRequirement("animator_wychowawca", 2));
        var created = catalogue.create(request(null, targets));
        assertEquals(targets, catalogue.get(created.turnusCode()).staffRequirements());
        assertEquals(10, created.staffCapacity());
        assertTrue(created.staffRegistrationOpen());
        assertFalse(created.participantRegistrationOpen());
        var changed = List.of(new StaffRequirement("sternik", 0), new StaffRequirement("animator", 6),
                new StaffRequirement("animator_wychowawca", 3));
        var updated = catalogue.update(created.turnusCode(), request(0L, changed));
        assertEquals(changed.subList(1, 3), updated.staffRequirements());
        assertEquals(1, updated.version());
        assertEquals(List.of(), catalogue.update(created.turnusCode(), request(1L, List.of())).staffRequirements());
    }
    @Test void SummerApiPersistsRequirementsAndRejectsWinterOnlyCategory() throws Exception {
        String body = """
            {"turnusCode":"SUMMERSTAFF1","turnusName":"Summer","turnusLocation":"Place",
             "seasonType":"SUMMER","seasonYear":2027,"startDate":"2027-08-01","endDate":"2027-08-11",
             "minAge":14,"participantCapacity":50,"staffCapacity":10,"active":true,
             "participantRegistrationOpen":false,"staffRegistrationOpen":true,"version":0,"staffRequirements":%s}
            """;
        String targets = "[{\"categoryCode\":\"sternik\",\"requiredCount\":5},{\"categoryCode\":\"animator_wychowawca\",\"requiredCount\":2}]";
        mvc.perform(post("/api/turnuses").header("Authorization", token).contentType("application/json")
                .content(body.formatted(targets))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffRequirements[0].categoryCode").value("sternik"));
        mvc.perform(get("/api/turnuses/SUMMERSTAFF1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.staffRequirements[1].requiredCount").value(2));
        mvc.perform(patch("/api/turnuses/SUMMERSTAFF1").header("Authorization", token).contentType("application/json")
                .content(body.formatted("[{\"categoryCode\":\"instruktor_narty\",\"requiredCount\":1}]")))
                .andExpect(status().isBadRequest());
        assertEquals(0, catalogue.get("SUMMERSTAFF1").version());
        mvc.perform(patch("/api/turnuses/SUMMERSTAFF1").header("Authorization", token).contentType("application/json")
                .content(body.formatted("[]"))).andExpect(status().isOk()).andExpect(jsonPath("$.staffRequirements").isEmpty());
    }
}
