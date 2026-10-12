package pl.srm.registrationapi.season;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import static org.junit.jupiter.api.Assertions.*;

class StaffRoleValidatorTest {
    final ObjectMapper mapper = new ObjectMapper();
    final StaffRoleValidator validator = new StaffRoleValidator();
    final StaffCatalogue winter = TestStaffCatalogues.load("winter");
    final StaffCatalogue summer = TestStaffCatalogues.load("summer");
    ObjectNode payload(String role, String function) {
        ObjectNode root = mapper.createObjectNode();
        root.put("role", role); root.put("subrole", function);
        root.putObject("certificates"); root.putObject("certificateDetails");
        return root;
    }
    @ParameterizedTest
    @CsvSource(value={"kucharka|", "kierowca|", "animator|", "animator|kandydat_na_animatora", "ksiadz|",
            "ksiadz|prowadzacy", "ksiadz|pomoc", "ksiadz|instruktor_narty", "ksiadz|instruktor_snowboard",
            "instruktor|narty", "instruktor|snowboard"}, delimiter='|', nullValues="")
    void acceptsAgreedAdultWinterChoicesWithoutCertificates(String role, String function) {
        assertDoesNotThrow(() -> validator.validate(payload(role, function == null ? "" : function), false, winter));
    }
    @Test void winterMinorMayOnlyChooseAnimatorCandidate() {
        assertDoesNotThrow(() -> validator.validate(payload("animator", "kandydat_na_animatora"), true, winter));
        for (String role : new String[]{"animator", "ksiadz", "instruktor", "kucharka", "kierowca"})
            assertThrows(RegistrationException.class, () -> validator.validate(payload(role, ""), true, winter));
        assertThrows(RegistrationException.class, () -> validator.validate(payload("instruktor", "narty"), true, winter));
    }
    @Test void rejectsSummerOnlyRolesAndMultipleOrUnknownWinterFunctions() {
        for (var choice : new String[][]{{"sternik", ""}, {"ratownik", ""}, {"kandydat_na_animatora", ""},
                {"animator", "animator_wychowawca"}, {"instruktor", ""}, {"instruktor", "narty,snowboard"}, {"ksiadz", "sternik"}})
            assertThrows(RegistrationException.class, () -> validator.validate(payload(choice[0], choice[1]), false, winter));
        var root = payload("instruktor", "narty"); root.putArray("subrole").add("narty").add("snowboard");
        assertThrows(RegistrationException.class, () -> validator.validate(root, false, winter));
    }
    @Test void summerBehaviorRemainsAndWinterChoicesAreNotSilentlyAcceptedInSummer() {
        assertDoesNotThrow(() -> validator.validate(payload("sternik_z_opiekunem", ""), true, summer));
        assertDoesNotThrow(() -> validator.validate(payload("kandydat_na_animatora", ""), true, summer));
        assertThrows(RegistrationException.class, () -> validator.validate(payload("animator", "kandydat_na_animatora"), true, summer));
        assertDoesNotThrow(() -> validator.validate(payload("animator", "kandydat_na_animatora"), false, summer));
        assertThrows(RegistrationException.class, () -> validator.validate(payload("instruktor", "narty"), false, summer));
    }
    @Test void detailsAreRequiredOnlyForSelectedConfiguredCertificates() {
        var root = payload("instruktor", "narty");
        ((ObjectNode) root.path("certificates")).put("inne", true);
        assertThrows(RegistrationException.class, () -> validator.validate(root, false, winter));
        ((ObjectNode) root.path("certificateDetails")).put("inne", "Opis uprawnień");
        assertDoesNotThrow(() -> validator.validate(root, false, winter));
        ((ObjectNode) root.path("certificates")).put("patent_zeglarski", true);
        assertThrows(RegistrationException.class, () -> validator.validate(root, false, winter));
    }
    @Test void missingCatalogueDoesNotFallBackToSummer() {
        assertEquals("TURNUS_CATALOGUE_UNAVAILABLE", assertThrows(RegistrationException.class,
                () -> validator.validate(payload("sternik", ""), false, null)).getCode());
    }
}
