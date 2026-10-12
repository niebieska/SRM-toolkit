package pl.srm.registrationapi.turnus.validator;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import pl.srm.registrationapi.turnus.model.Turnus;

/** Validates catalogue data; submission availability is checked separately. */
@Component
public class TurnusCatalogueValidator {
    public void validate(Turnus turnus) {
        Assert.notNull(turnus, "Missing Turnus definition");
        validateIdentity(turnus);
        validateSeason(turnus);
        validateDates(turnus);
        validateAge(turnus);
        validateCapacities(turnus);
    }

    private void validateIdentity(Turnus turnus) {
        Assert.hasText(turnus.turnusCode(), "Missing Turnus code");
        Assert.hasText(turnus.turnusName(), "Missing Turnus name");
        Assert.hasText(turnus.turnusLocation(), "Missing Turnus location");
    }

    private void validateSeason(Turnus turnus) {
        Assert.notNull(turnus.seasonType(), "Missing Turnus season type");
        Integer year = turnus.seasonYear();
        Assert.isTrue(year != null && year >= 2000 && year <= 9999, "Turnus season year must be between 2000 and 9999");
    }

    private void validateDates(Turnus turnus) {
        Assert.notNull(turnus.startDate(), "Missing Turnus start date");
        Assert.notNull(turnus.endDate(), "Missing Turnus end date");
        Assert.isTrue(!turnus.endDate().isBefore(turnus.startDate()), "Turnus end date precedes start date");
    }

    private void validateAge(Turnus turnus) {
        Integer age = turnus.minAge();
        Assert.isTrue(age != null && age >= 0 && age <= 120, "Turnus minimum age must be between 0 and 120");
    }

    private void validateCapacities(Turnus turnus) {
        Assert.isTrue(turnus.participantCapacity() != null && turnus.participantCapacity() >= 1,
                "Turnus participant capacity must be positive");
        Assert.isTrue(turnus.staffCapacity() != null && turnus.staffCapacity() >= 1,
                "Turnus staff capacity must be positive");
    }
}
