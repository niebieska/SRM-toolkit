package pl.srm.biuroapi.registration.service;

import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.StaffRoleStats;
import pl.srm.biuroapi.season.SeasonStaffCatalogue;
import pl.srm.biuroapi.season.StaffCatalogueValidator;
import pl.srm.biuroapi.season.StaffingCategories;
import pl.srm.biuroapi.turnus.StaffRequirement;
import pl.srm.biuroapi.turnus.TurnusDefinition;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class StaffingStatisticsServiceTest {
    private StaffingStatisticsService service() {
        var mapper = new ObjectMapper();
        var resources = new DefaultResourceLoader();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var seasons = new SeasonStaffCatalogue(mapper, resources,
                    new StaffCatalogueValidator(factory.getValidator()), "classpath:staff-seasons/");
            return new StaffingStatisticsService(new StaffingCategories(mapper, resources, seasons, "classpath:staff-seasons/"), seasons);
        }
    }
    private TurnusDefinition turnus(SeasonType season, StaffRequirement... requirements) {
        return new TurnusDefinition("T1", "Turnus", null, "Place", season, 2027,
                LocalDate.of(2027, 1, 17), LocalDate.of(2027, 1, 24), 14, 50, 10,
                true, true, true, 0, List.of(requirements));
    }
    private RegistrationSummary row(String role, String subrole) { return row("T1", "STAFF", "ACCEPTED", role, subrole); }
    private RegistrationSummary row(String code, String type, String status, String role, String subrole) {
        return new RegistrationSummary("REG", type, code, false, status, null, null, "Name", "Surname", 20, null, role, subrole);
    }
    private StaffRoleStats role(StaffingStatisticsService.Result result, String code) {
        return result.roles().stream().filter(r -> r.categoryCode().equals(code)).findFirst().orElseThrow();
    }
    private long detail(StaffRoleStats role, String code) {
        return role.subroles().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow().accepted();
    }
    @Test void SummerCombinesCandidatesAndSterniksButSeparatesEducatorsAndLeadingPriests() {
        var result = service().calculate(turnus(SeasonType.SUMMER,
                new StaffRequirement("animator", 6), new StaffRequirement("animator_wychowawca", 2),
                new StaffRequirement("sternik", 5), new StaffRequirement("ksiadz_prowadzacy", 1),
                new StaffRequirement("ksiadz", 2)), List.of(
                row("animator", "animator"), row("animator", "kandydat_na_animatora"), row("kandydat_na_animatora", null),
                row("animator", "animator_wychowawca"), row("sternik", null), row("sternik_z_opiekunem", null),
                row("ksiadz", "sternik"), row("ksiadz", "prowadzacy"), row("ksiadz", "wychowawca"), row("ksiadz", "pomoc")));
        var animator = role(result, "animator");
        assertEquals(3, animator.accepted());
        assertEquals(6, animator.required());
        assertEquals(2, detail(animator, "kandydat_na_animatora"));
        assertEquals(1, detail(animator, "animator"));
        assertEquals(1, role(result, "animator_wychowawca").accepted());
        assertEquals(3, role(result, "sternik").accepted());
        assertEquals(1, detail(role(result, "sternik"), "sternik_z_opiekunem"));
        assertEquals(1, detail(role(result, "sternik"), "sternik"));
        assertEquals("Ksiądz sternik", role(result, "sternik").subroles().stream().filter(s -> s.code().equals("sternik")).findFirst().orElseThrow().label());
        assertEquals(1, role(result, "ksiadz_prowadzacy").accepted());
        assertEquals(2, role(result, "ksiadz").accepted());
        assertEquals(0, result.unmappedAccepted());
        assertEquals(10, result.roles().stream().mapToLong(StaffRoleStats::accepted).sum());
    }
    @Test void WinterPriestInstructorsStayPriestsAndStandaloneDisciplinesStaySeparate() {
        var result = service().calculate(turnus(SeasonType.WINTER,
                new StaffRequirement("animator", 3), new StaffRequirement("ksiadz", 4),
                new StaffRequirement("ksiadz_prowadzacy", 1), new StaffRequirement("instruktor_narty", 2),
                new StaffRequirement("instruktor_snowboard", 2)), List.of(
                row("animator", null), row("animator", "kandydat_na_animatora"),
                row("ksiadz", null), row("ksiadz", "instruktor_narty"), row("ksiadz", "instruktor_snowboard"),
                row("ksiadz", "pomoc"), row("ksiadz", "prowadzacy"), row("instruktor", "narty"), row("instruktor", "snowboard")));
        assertEquals(2, role(result, "animator").accepted());
        assertEquals(1, detail(role(result, "animator"), "kandydat_na_animatora"));
        assertEquals(4, role(result, "ksiadz").accepted());
        assertEquals(1, detail(role(result, "ksiadz"), "instruktor_narty"));
        assertEquals(1, role(result, "ksiadz_prowadzacy").accepted());
        assertEquals(1, role(result, "instruktor_narty").accepted());
        assertEquals(1, role(result, "instruktor_snowboard").accepted());
    }
    @Test void countsOnlyAcceptedStaffForSelectedTurnusAndHidesZeroOrAbsentTargets() {
        var definition = turnus(SeasonType.WINTER, new StaffRequirement("animator", 1), new StaffRequirement("kucharka", 0));
        var result = service().calculate(definition, List.of(row("animator", null), row("animator", null), row("kucharka", null),
                row("T1", "STAFF", "NEW", "animator", null), row("T1", "STAFF", "WAITLIST", "animator", null),
                row("T1", "STAFF", "REJECTED", "animator", null), row("T1", "PARTICIPANT", "ACCEPTED", "animator", null),
                row("T2", "STAFF", "ACCEPTED", "animator", null)));
        assertEquals(1, result.roles().size());
        assertEquals(2, result.roles().getFirst().accepted());
        assertEquals(1, result.roles().getFirst().required());
        assertEquals(0, result.unmappedAccepted());
        assertEquals(0, service().calculate(definition, List.of()).roles().getFirst().accepted());
        assertTrue(service().calculate(turnus(SeasonType.SUMMER), List.of(row("unknown", null))).roles().isEmpty());
    }
    @Test void malformedLegacyRolesAreReportedWithoutLosingOverallStaffOrCountingThemTwice() {
        var result = service().calculate(turnus(SeasonType.WINTER, new StaffRequirement("animator", 4)),
                List.of(row(null, null), row("unknown", null), row("instruktor", null), row("animator", "invalid"),
                        row(" animator ", "  "), row("animator", " kandydat_na_animatora ")));
        assertEquals(4, result.unmappedAccepted());
        assertEquals(2, role(result, "animator").accepted());
        assertEquals(1, detail(role(result, "animator"), "kandydat_na_animatora"));
    }
}
