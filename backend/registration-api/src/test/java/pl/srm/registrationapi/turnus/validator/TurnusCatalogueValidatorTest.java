package pl.srm.registrationapi.turnus.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class TurnusCatalogueValidatorTest {
    final TurnusCatalogueValidator validator = new TurnusCatalogueValidator();
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Turnus valid() {
        return new Turnus("T1", "Turnus", null, "Place", SeasonType.SUMMER, 2026,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 14, 60, 10, false, false, false);
    }
    Turnus changed(String field, String jsonValue) throws Exception {
        ObjectNode node = mapper.valueToTree(valid());
        node.set(field, mapper.readTree(jsonValue));
        return mapper.treeToValue(node, Turnus.class);
    }

    @Test void acceptsClosedArchivedDefinitionsAndOptionalDescription() {
        assertDoesNotThrow(() -> validator.validate(valid()));
    }

    @Test void nullDefinitionUsesValidationExceptionRatherThanNullPointer() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "turnusCode|null", "turnusCode|\" \"", "turnusName|null", "turnusName|\"\"",
            "turnusLocation|null", "turnusLocation|\" \"", "seasonType|null",
            "seasonYear|null", "seasonYear|1999", "seasonYear|10000",
            "startDate|null", "endDate|null", "endDate|\"2026-07-31\"",
            "minAge|null", "minAge|-1", "minAge|121",
            "participantCapacity|null", "participantCapacity|0", "participantCapacity|-1",
            "staffCapacity|null", "staffCapacity|0", "staffCapacity|-1"
    })
    void rejectsIncompleteAndOutOfRangeDefinitions(String field, String value) throws Exception {
        Turnus turnus = changed(field, value);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(turnus));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "seasonYear|2000", "seasonYear|9999", "minAge|0", "minAge|120",
            "participantCapacity|1", "staffCapacity|1", "endDate|\"2026-08-01\""
    })
    void acceptsInclusiveBoundaries(String field, String value) throws Exception {
        Turnus turnus = changed(field, value);
        assertDoesNotThrow(() -> validator.validate(turnus));
    }
}
