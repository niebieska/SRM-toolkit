package pl.srm.registrationapi.season;

import jakarta.validation.Validator;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class StaffCatalogueValidator {
    private final Validator validator;
    public StaffCatalogueValidator(Validator validator) { this.validator = validator; }
    public void validate(StaffCatalogue catalogue, String seasonType) {
        Assert.notNull(catalogue, "Missing staff catalogue");
        Assert.isTrue(validator.validate(catalogue).isEmpty(), "Invalid staff catalogue fields");
        Assert.isTrue(seasonType.equals(catalogue.seasonType()), "Staff catalogue season mismatch");
        unique(catalogue.roles().stream().map(StaffCatalogue.Role::value).toList());
        for (var role : catalogue.roles()) unique(role.subroles().stream().map(StaffCatalogue.Function::value).toList());
        for (var certificates : catalogue.certificatesByRole().values())
            unique(certificates.stream().map(StaffCatalogue.Certificate::id).toList());
    }
    private void unique(List<String> codes) {
        Assert.isTrue(new HashSet<>(codes).size() == codes.size(), "Duplicate staff catalogue code");
    }
}
