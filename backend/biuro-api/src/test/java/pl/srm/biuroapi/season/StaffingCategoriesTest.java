package pl.srm.biuroapi.season;

import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.DefaultResourceLoader;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class StaffingCategoriesTest {
    @TempDir Path directory;
    private String fileName(SeasonType season) { return season.name().toLowerCase(Locale.ROOT) + "-staffing.json"; }
    private void copyFiles(DefaultResourceLoader resources) throws Exception {
        for (var season : SeasonType.values()) {
            try (var input = resources.getResource("classpath:staff-seasons/" + fileName(season)).getInputStream()) {
                Files.copy(input, directory.resolve(fileName(season)));
            }
        }
    }
    @ParameterizedTest @EnumSource(SeasonType.class)
    void badGroupingFailsStartupRatherThanMiscountingStaff(SeasonType season) throws Exception {
        var mapper = new ObjectMapper();
        var resources = new DefaultResourceLoader();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var seasons = new SeasonStaffCatalogue(mapper, resources,
                    new StaffCatalogueValidator(factory.getValidator()), "classpath:staff-seasons/");
            copyFiles(resources);
            var file = directory.resolve(fileName(season));
            ArrayNode valid = (ArrayNode) mapper.readTree(Files.readString(file));
            for (int scenario = 0; scenario < 4; scenario++) {
                var invalid = valid.deepCopy();
                switch (scenario) {
                    case 0 -> ((ObjectNode) invalid.get(1)).put("code", valid.get(0).get("code").asText());
                    case 1 -> ((ObjectNode) invalid.get(0).get("selections").get(0)).put("role", "unknown");
                    case 2 -> ((ArrayNode) invalid.get(0).get("selections")).add(invalid.get(1).get("selections").get(0));
                    case 3 -> invalid.remove(0);
                    default -> throw new AssertionError();
                }
                Files.writeString(file, mapper.writeValueAsString(invalid));
                var failure = assertThrows(IllegalStateException.class, () -> new StaffingCategories(mapper, resources, seasons,
                        directory.toUri().toString()));
                assertTrue(failure.getMessage().contains(season.name()));
            }
            Files.writeString(file, mapper.writeValueAsString(valid));
            var loaded = new StaffingCategories(mapper, resources, seasons, directory.toUri().toString());
            assertEquals(7, loaded.get(SeasonType.WINTER).size());
            assertEquals(8, loaded.get(SeasonType.SUMMER).size());
            assertThrows(UnsupportedOperationException.class, () -> loaded.get(season).clear());
            assertThrows(UnsupportedOperationException.class, () -> loaded.get(season).getFirst().selections().clear());
        }
    }
    @ParameterizedTest @EnumSource(SeasonType.class)
    void missingStaffingFileFailsStartupClearly(SeasonType missing) throws Exception {
        var mapper = new ObjectMapper();
        var resources = new DefaultResourceLoader();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var seasons = new SeasonStaffCatalogue(mapper, resources,
                    new StaffCatalogueValidator(factory.getValidator()), "classpath:staff-seasons/");
            copyFiles(resources);
            Files.delete(directory.resolve(fileName(missing)));
            var failure = assertThrows(IllegalStateException.class, () -> new StaffingCategories(mapper,
                    resources, seasons, directory.toUri().toString()));
            assertTrue(failure.getMessage().contains(missing.name()));
        }
    }
}
