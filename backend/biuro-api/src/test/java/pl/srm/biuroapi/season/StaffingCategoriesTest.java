package pl.srm.biuroapi.season;

import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class StaffingCategoriesTest {
    @TempDir Path directory;
    @Test void badGroupingFailsStartupRatherThanMiscountingStaff() throws Exception {
        var mapper = new ObjectMapper();
        var resources = new DefaultResourceLoader();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var seasons = new SeasonStaffCatalogue(mapper, resources,
                    new StaffCatalogueValidator(factory.getValidator()), "classpath:staff-seasons/");
            var source = resources.getResource("classpath:staff-seasons/winter-staffing.json");
            ArrayNode valid;
            try (var input = source.getInputStream()) { valid = (ArrayNode) mapper.readTree(input); }
            var file = directory.resolve("winter-staffing.json");
            for (int scenario = 0; scenario < 4; scenario++) {
                var invalid = valid.deepCopy();
                switch (scenario) {
                    case 0 -> ((ObjectNode) invalid.get(1)).put("code", "kucharka");
                    case 1 -> ((ObjectNode) invalid.get(0).get("selections").get(0)).put("role", "unknown");
                    case 2 -> ((ArrayNode) invalid.get(0).get("selections")).add(invalid.get(1).get("selections").get(0));
                    case 3 -> invalid.remove(0);
                    default -> throw new AssertionError();
                }
                Files.writeString(file, mapper.writeValueAsString(invalid));
                assertThrows(IllegalStateException.class, () -> new StaffingCategories(mapper, resources, seasons,
                        directory.toUri().toString()));
            }
            Files.writeString(file, mapper.writeValueAsString(valid));
            var loaded = new StaffingCategories(mapper, resources, seasons, directory.toUri().toString());
            assertEquals(7, loaded.get(SeasonType.WINTER).size());
            assertThrows(UnsupportedOperationException.class, () -> loaded.get(SeasonType.WINTER).clear());
        }
    }
    @Test void missingStaffingFileFailsStartupClearly() {
        var mapper = new ObjectMapper();
        assertThrows(IllegalStateException.class, () -> new StaffingCategories(mapper,
                new DefaultResourceLoader(), null, directory.toUri().toString()));
    }
}
