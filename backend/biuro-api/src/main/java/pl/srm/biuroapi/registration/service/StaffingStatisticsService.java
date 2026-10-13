package pl.srm.biuroapi.registration.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.StaffRoleStats;
import pl.srm.biuroapi.registration.model.StaffRoleStats.SubroleCount;
import pl.srm.biuroapi.season.SeasonStaffCatalogue;
import pl.srm.biuroapi.season.StaffCatalogue;
import pl.srm.biuroapi.season.StaffingCategories;
import pl.srm.biuroapi.season.StaffingCategories.Category;
import pl.srm.biuroapi.season.StaffingCategories.Selection;
import pl.srm.biuroapi.turnus.TurnusDefinition;

@Service
public class StaffingStatisticsService {
    public record Result(List<StaffRoleStats> roles, long unmappedAccepted) {
        public Result { roles = List.copyOf(roles); }
    }
    private final StaffingCategories categories;
    private final SeasonStaffCatalogue seasons;
    public StaffingStatisticsService(StaffingCategories categories, SeasonStaffCatalogue seasons) {
        this.categories = categories;
        this.seasons = seasons;
    }
    public Result calculate(TurnusDefinition turnus, List<RegistrationSummary> registrations) {
        var targets = turnus.staffRequirements().stream().filter(r -> r.requiredCount() > 0)
                .collect(Collectors.toMap(r -> r.categoryCode(), r -> r.requiredCount()));
        if (targets.isEmpty()) return new Result(List.of(), 0);
        var definitions = categories.get(turnus.seasonType());
        Set<Selection> known = definitions.stream().flatMap(c -> c.selections().stream()).collect(Collectors.toSet());
        var counts = new HashMap<Selection, Long>();
        long unmapped = 0;
        for (var row : registrations) {
            if (!turnus.turnusCode().equals(row.turnusCode())) continue;
            if (!"STAFF".equalsIgnoreCase(row.registrationType()) || !"ACCEPTED".equalsIgnoreCase(row.status())) continue;
            var selection = new Selection(normalize(row.role()), normalize(row.subrole()));
            if (!known.contains(selection)) { unmapped++; continue; }
            counts.merge(selection, 1L, Long::sum);
        }
        StaffCatalogue roleCatalogue = seasons.get(turnus.seasonType());
        var results = definitions.stream().filter(c -> targets.containsKey(c.code())).map(category -> {
            long accepted = category.selections().stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum();
            return new StaffRoleStats(category.code(), category.label(), accepted, targets.get(category.code()),
                    breakdown(category, counts, roleCatalogue));
        }).toList();
        return new Result(results, unmapped);
    }
    private List<SubroleCount> breakdown(Category category, Map<Selection, Long> counts, StaffCatalogue catalogue) {
        if (category.selections().size() < 2) return List.of();
        var result = new LinkedHashMap<String, SubroleCount>();
        for (var selection : category.selections()) {
            if (selection.subrole() == null && selection.role().equals(category.code())) continue;
            String key = selection.subrole() == null ? selection.role() : selection.subrole();
            var role = catalogue.roles().stream().filter(r -> r.value().equals(selection.role())).findFirst().orElseThrow();
            String label = role.label();
            if (selection.subrole() != null) {
                var function = role.subroles().stream().filter(f -> f.value().equals(selection.subrole())).findFirst().orElseThrow();
                label = function.label();
                if (!selection.role().equals(category.code()))
                    label = role.label() + " " + label.toLowerCase(Locale.forLanguageTag("pl"));
            }
            var previous = result.get(key);
            long accepted = counts.getOrDefault(selection, 0L) + (previous == null ? 0 : previous.accepted());
            result.put(key, new SubroleCount(key, previous == null ? label : previous.label(), accepted));
        }
        return List.copyOf(result.values());
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
