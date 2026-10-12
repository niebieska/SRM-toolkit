package pl.srm.registrationapi.season;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class TestStaffCatalogues {
    private TestStaffCatalogues() {}
    public static StaffCatalogue load(String season) {
        try (var input = TestStaffCatalogues.class.getResourceAsStream("/staff-season-fixtures/" + season + ".json")) {
            return new ObjectMapper().readValue(input, StaffCatalogue.class);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
