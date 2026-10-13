package pl.srm.biuroapi.season;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** File-backed staffing groups validated against the matching season role catalogue. */
@Component
public class StaffingCategories {
    public record Selection(String role, String subrole) {}
    public record Category(String code, String label, List<Selection> selections) {
        public Category { selections = List.copyOf(selections); }
    }
    private final Map<SeasonType, List<Category>> catalogues;
    public StaffingCategories(ObjectMapper mapper, ResourceLoader resources, SeasonStaffCatalogue seasons,
            @Value("${staff.catalogue.location:classpath:staff-seasons/}") String location) {
        var loaded = new EnumMap<SeasonType, List<Category>>(SeasonType.class);
        for (SeasonType season : SeasonType.values()) {
            var resource = resources.getResource(location + (location.endsWith("/") ? "" : "/")
                    + season.name().toLowerCase(Locale.ROOT) + "-staffing.json");
            try (var input = resource.getInputStream()) {
                List<Category> categories = List.copyOf(mapper.readValue(input, new TypeReference<List<Category>>() {}));
                validate(categories, seasons.get(season));
                loaded.put(season, categories);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot load staffing categories for " + season.name(), exception);
            }
        }
        catalogues = Map.copyOf(loaded);
    }
    public List<Category> get(SeasonType season) { return catalogues.get(season); }
    private void validate(List<Category> categories, StaffCatalogue catalogue) {
        Assert.notEmpty(categories, "Missing staffing categories");
        var codes = new HashSet<String>();
        var mapped = new HashSet<Selection>();
        for (var category : categories) {
            Assert.hasText(category.code(), "Missing staffing category code");
            Assert.hasText(category.label(), "Missing staffing category label");
            Assert.isTrue(codes.add(category.code()), "Duplicate staffing category");
            Assert.notEmpty(category.selections(), "Missing staffing selections");
            for (var selection : category.selections()) {
                Assert.isTrue(mapped.add(selection), "Staff selection belongs to multiple categories");
                var role = catalogue.roles().stream().filter(r -> r.value().equals(selection.role())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown staffing role"));
                if (selection.subrole() == null) Assert.isTrue(!role.subroleRequired(), "Staff function is required");
                else Assert.isTrue(role.subroles().stream().anyMatch(f -> f.value().equals(selection.subrole())),
                        "Unknown staffing function");
            }
        }
        for (var role : catalogue.roles()) {
            if (!role.subroleRequired()) Assert.isTrue(mapped.contains(new Selection(role.value(), null)), "Unmapped staffing role");
            for (var function : role.subroles())
                Assert.isTrue(mapped.contains(new Selection(role.value(), function.value())), "Unmapped staffing function");
        }
    }
}
