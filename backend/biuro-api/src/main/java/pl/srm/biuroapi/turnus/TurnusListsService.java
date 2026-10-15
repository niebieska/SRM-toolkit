package pl.srm.biuroapi.turnus;

import java.time.Period;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.client.RegistrationApiClient;
import pl.srm.biuroapi.registration.model.TurnusListPerson;
import pl.srm.biuroapi.season.SeasonStaffCatalogue;
import pl.srm.biuroapi.season.StaffCatalogue;

@Service
public class TurnusListsService {
    public record ListEntry(TurnusListPerson person, Integer age, String roleLabel, String subroleLabel,
            List<String> qualificationLabels) {
        public ListEntry { qualificationLabels = List.copyOf(qualificationLabels); }
    }
    public record TurnusLists(TurnusDefinition turnus, List<ListEntry> entries) {
        public TurnusLists { entries = List.copyOf(entries); }
    }
    private final TurnusCatalogue turnuses;
    private final RegistrationApiClient registrations;
    private final SeasonStaffCatalogue seasons;

    public TurnusListsService(TurnusCatalogue turnuses, RegistrationApiClient registrations, SeasonStaffCatalogue seasons) {
        this.turnuses = turnuses;
        this.registrations = registrations;
        this.seasons = seasons;
    }

    public TurnusLists get(String code) {
        var turnus = turnuses.get(code);
        var catalogue = seasons.get(turnus.seasonType());
        var entries = registrations.fetchTurnusList(code).stream()
                .map(person -> entry(person, turnus, catalogue)).toList();
        return new TurnusLists(turnus, entries);
    }

    private ListEntry entry(TurnusListPerson person, TurnusDefinition turnus, StaffCatalogue catalogue) {
        if (person == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Biuro otrzymało nieprawidłowe dane listy.");
        if (!"PARTICIPANT".equals(person.registrationType()) && !"STAFF".equals(person.registrationType()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Biuro otrzymało nieprawidłowe dane listy.");
        Integer age = null;
        if (person.birthDate() != null && !person.birthDate().isAfter(turnus.startDate()))
            age = Period.between(person.birthDate(), turnus.startDate()).getYears();
        String roleLabel = null;
        String subroleLabel = null;
        if ("STAFF".equals(person.registrationType())) {
            var role = catalogue.roles().stream().filter(r -> r.value().equals(person.role())).findFirst();
            roleLabel = role.map(StaffCatalogue.Role::label).orElse(person.role());
            if (role.isPresent()) subroleLabel = role.get().subroles().stream()
                    .filter(f -> f.value().equals(person.subrole())).map(StaffCatalogue.Function::label)
                    .findFirst().orElse(person.subrole());
            else subroleLabel = person.subrole();
        }
        return new ListEntry(person, age, roleLabel, subroleLabel, qualificationLabels(person, catalogue));
    }

    private List<String> qualificationLabels(TurnusListPerson person, StaffCatalogue catalogue) {
        if (!"STAFF".equals(person.registrationType()) || person.additionalData() == null) return List.of();
        var options = catalogue.certificatesByRole();
        String source = person.role();
        if (person.subrole() != null) {
            String combined = person.role() + "_" + person.subrole();
            if (options.containsKey(combined)) source = combined;
            else if (options.containsKey(person.subrole())) source = person.subrole();
        }
        var available = source == null ? List.<StaffCatalogue.Certificate>of() : options.getOrDefault(source, List.of());
        return person.additionalData().qualifications().stream().map(qualification -> {
            String label = available.stream().filter(option -> option.id().equals(qualification.code()))
                    .map(StaffCatalogue.Certificate::label).findFirst().orElse(qualification.code());
            return qualification.detail() == null ? label : label + ": " + qualification.detail();
        }).toList();
    }
}
