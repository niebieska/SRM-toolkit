package pl.srm.biuroapi.season;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pl.srm.biuroapi.turnus.TurnusDefinition.SeasonType;
import tools.jackson.databind.ObjectMapper;

/** File-backed role catalogue, loaded and validated once at startup. */
@Component
public class SeasonStaffCatalogue {
    private final Map<SeasonType, StaffCatalogue> catalogues;
    public SeasonStaffCatalogue(ObjectMapper mapper, ResourceLoader resources, StaffCatalogueValidator validator,
            @Value("${staff.catalogue.location:classpath:staff-seasons/}") String location) {
        var loaded = new EnumMap<SeasonType, StaffCatalogue>(SeasonType.class);
        for (SeasonType season : SeasonType.values()) {
            var resource = resources.getResource(location + (location.endsWith("/") ? "" : "/")
                    + season.name().toLowerCase(Locale.ROOT) + ".json");
            try (var input = resource.getInputStream()) {
                StaffCatalogue catalogue = mapper.readValue(input, StaffCatalogue.class);
                validator.validate(catalogue, season.name());
                loaded.put(season, catalogue);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot load staff catalogue for " + season.name(), exception);
            }
        }
        catalogues = Map.copyOf(loaded);
    }
    public StaffCatalogue get(SeasonType season) { return catalogues.get(season); }
}
