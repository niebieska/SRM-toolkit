package pl.srm.biuroapi.turnus;

import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.season.StaffingCategories;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class StaffRequirements {
    private final ObjectMapper mapper;
    private final StaffingCategories categories;
    public StaffRequirements(ObjectMapper mapper, StaffingCategories categories) {
        this.mapper = mapper;
        this.categories = categories;
    }
    public List<StaffRequirement> validate(List<StaffRequirement> requirements, TurnusDefinition.SeasonType season) {
        if (requirements == null) return List.of();
        var allowed = categories.get(season).stream().map(StaffingCategories.Category::code).toList();
        var seen = new HashSet<String>();
        for (var requirement : requirements) {
            if (requirement == null || requirement.requiredCount() == null || requirement.requiredCount() < 0)
                throw invalid("Podaj nieujemną liczbę wymaganych osób.");
            if (!allowed.contains(requirement.categoryCode())) throw invalid("Nieprawidłowa kategoria wymaganej kadry dla sezonu.");
            if (!seen.add(requirement.categoryCode())) throw invalid("Kategoria wymaganej kadry nie może się powtarzać.");
        }
        return requirements.stream().filter(r -> r.requiredCount() > 0).toList();
    }
    public String write(List<StaffRequirement> requirements) {
        return mapper.writeValueAsString(requirements);
    }
    public List<StaffRequirement> read(String json) {
        return List.copyOf(mapper.readValue(json, new TypeReference<List<StaffRequirement>>() {}));
    }
    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
