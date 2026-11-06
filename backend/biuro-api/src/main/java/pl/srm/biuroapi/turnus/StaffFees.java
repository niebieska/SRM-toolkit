package pl.srm.biuroapi.turnus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.season.SeasonStaffCatalogue;
import pl.srm.biuroapi.season.StaffCatalogue;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class StaffFees {
    private final ObjectMapper mapper;
    private final SeasonStaffCatalogue seasons;

    public StaffFees(ObjectMapper mapper, SeasonStaffCatalogue seasons) {
        this.mapper = mapper;
        this.seasons = seasons;
    }

    public List<StaffFee> validate(List<StaffFee> fees, TurnusDefinition.SeasonType season) {
        if (fees == null) return List.of();
        var catalogue = seasons.get(season);
        var seen = new HashSet<StaffFee.Selection>();
        var rolesWithBaseFee = new HashSet<String>();
        var rolesWithSubroleFees = new HashSet<String>();
        for (var fee : fees) {
            if (fee == null || fee.amount() == null || fee.amount().signum() < 0
                    || fee.amount().scale() > 2 || integerDigits(fee.amount()) > 8)
                throw invalid("Kwota opłaty kadry musi być nieujemna i mieć maksymalnie dwa miejsca po przecinku.");
            StaffCatalogue.Role role = catalogue.roles().stream().filter(item -> item.value().equals(fee.role()))
                    .findFirst().orElseThrow(() -> invalid("Nieprawidłowa rola z przypisaną opłatą."));
            if (fee.subrole() == null) {
                if (role.subroleRequired()) throw invalid("Wybierz funkcję dla roli z wymaganym podtypem.");
            } else if (role.subroles().stream().noneMatch(item -> item.value().equals(fee.subrole()))) {
                throw invalid("Nieprawidłowa funkcja z przypisaną opłatą.");
            }
            if (fee.subrole() == null) rolesWithBaseFee.add(fee.role());
            else rolesWithSubroleFees.add(fee.role());
            if (!seen.add(fee.selection())) throw invalid("Opłata dla tej roli i funkcji powtarza się.");
        }
        if (rolesWithBaseFee.stream().anyMatch(rolesWithSubroleFees::contains))
            throw invalid("Ustaw opłatę dla roli albo jej funkcji — opłaty nie mogą się nakładać.");
        return fees.stream().filter(fee -> fee.amount().signum() > 0).toList();
    }

    public List<StaffFeeOption> options(TurnusDefinition.SeasonType season) {
        var options = new ArrayList<StaffFeeOption>();
        for (var role : seasons.get(season).roles()) {
            if (!role.subroleRequired()) options.add(new StaffFeeOption(role.value(), role.label(), null, null));
            for (var subrole : role.subroles())
                options.add(new StaffFeeOption(role.value(), role.label(), subrole.value(), subrole.label()));
        }
        return List.copyOf(options);
    }

    private int integerDigits(java.math.BigDecimal amount) {
        return Math.max(0, amount.precision() - amount.scale());
    }

    public String write(List<StaffFee> fees) { return mapper.writeValueAsString(fees); }
    public List<StaffFee> read(String json) {
        return List.copyOf(mapper.readValue(json, new TypeReference<List<StaffFee>>() {}));
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
