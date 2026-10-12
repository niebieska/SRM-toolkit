package pl.srm.biuroapi.season;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class SeasonStaffCatalogueTest {
    static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    @AfterAll static void close() { FACTORY.close(); }
    final ObjectMapper mapper = new ObjectMapper();
    final StaffCatalogueValidator validator = new StaffCatalogueValidator(FACTORY.getValidator());
    @TempDir Path directory;
    SeasonStaffCatalogue load(String location) {
        return new SeasonStaffCatalogue(mapper, new DefaultResourceLoader(), validator, location);
    }
    void copyFiles() throws Exception {
        for (String season : new String[]{"summer", "winter"}) {
            try (var input = getClass().getResourceAsStream("/staff-seasons/" + season + ".json")) {
                Files.copy(input, directory.resolve(season + ".json"));
            }
        }
    }
    @Test void loadsBothBundledSeasonsAndKeepsLegacySummerOptions() {
        var catalogue = load("classpath:staff-seasons/");
        assertEquals(8, catalogue.get(SeasonType.SUMMER).roles().size());
        assertEquals(5, catalogue.get(SeasonType.WINTER).roles().size());
        var animator = catalogue.get(SeasonType.WINTER).roles().stream().filter(r -> r.value().equals("animator")).findFirst().orElseThrow();
        assertFalse(animator.subroleRequired());
        assertFalse(animator.subroles().getFirst().adultOnly());
        var priest = catalogue.get(SeasonType.WINTER).roles().stream().filter(r -> r.value().equals("ksiadz")).findFirst().orElseThrow();
        assertEquals(4, priest.subroles().size());
        assertFalse(priest.subroleRequired());
        assertFalse(mapper.valueToTree(priest).has("ageRuleValid"));
    }
    @Test void externalFileChangesTakeEffectOnlyWhenReloadedOnRestart() throws Exception {
        copyFiles();
        String location = directory.toUri().toString();
        var before = load(location);
        var summer = directory.resolve("summer.json");
        Files.writeString(summer, Files.readString(summer).replace("Kandydat na animatora", "Nowa etykieta"));
        assertEquals("Kandydat na animatora", before.get(SeasonType.SUMMER).roles().getFirst().label());
        assertEquals("Nowa etykieta", load(location).get(SeasonType.SUMMER).roles().getFirst().label());
        assertThrows(UnsupportedOperationException.class, () -> before.get(SeasonType.SUMMER).roles().clear());
    }
    @Test void missingFileOrMalformedConfigPreventsStartupInsteadOfFallback() throws Exception {
        assertThrows(IllegalStateException.class, () -> load(directory.toUri().toString()));
        copyFiles();
        var winter = directory.resolve("winter.json");
        String original = Files.readString(winter);
        Files.writeString(winter, original.replace("\"WINTER\"", "\"SUMMER\""));
        assertThrows(IllegalStateException.class, () -> load(directory.toUri().toString()));
        Files.writeString(winter, "{bad json");
        assertThrows(IllegalStateException.class, () -> load(directory.toUri().toString()));
    }
    @Test void incompleteOrContradictoryAgeRulesAreRejected() throws Exception {
        copyFiles();
        var path = directory.resolve("winter.json");
        var root = (ObjectNode) mapper.readTree(Files.readString(path));
        var role = (ObjectNode) root.path("roles").get(0);
        role.put("minorOnly", true);
        Files.writeString(path, mapper.writeValueAsString(root));
        assertThrows(IllegalStateException.class, () -> load(directory.toUri().toString()));
        role.putNull("minorOnly");
        Files.writeString(path, mapper.writeValueAsString(root));
        assertThrows(IllegalStateException.class, () -> load(directory.toUri().toString()));
    }

    @Test void duplicateRoleCodesAreRejected() throws Exception {
        copyFiles();
        var path = directory.resolve("winter.json");
        var root = (ObjectNode) mapper.readTree(Files.readString(path));
        var roles = (ArrayNode) root.path("roles");
        roles.add(roles.get(0).deepCopy());
        Files.writeString(path, mapper.writeValueAsString(root));
        assertThrows(IllegalStateException.class, () -> load(directory.toUri().toString()));
    }
}
