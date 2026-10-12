package pl.srm.registrationapi.turnus.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TurnusControllerTest {

    private final TurnusProvider provider = mock(TurnusProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(provider.getAvailableTurnuses(pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT)).thenReturn(List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(new TurnusController(provider))
                .setControllerAdvice(new pl.srm.registrationapi.common.exception.GlobalExceptionHandler()).build();
    }

    pl.srm.registrationapi.turnus.model.Turnus definition() {
        return new pl.srm.registrationapi.turnus.model.Turnus("T1", "Turnus", null, "Place",
                pl.srm.registrationapi.turnus.model.SeasonType.SUMMER, 2026,
                java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 10),
                14, 60, 10, true, true, true, pl.srm.registrationapi.season.TestStaffCatalogues.load("summer"));
    }
    @Test
    void staffOptionsUseSelectedTurnusAndAreNotIncludedInOrdinaryPublicList() throws Exception {
        when(provider.getByCode("T1")).thenReturn(definition());
        when(provider.getAvailableTurnuses(pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT))
                .thenReturn(List.of(definition()));
        mockMvc.perform(get("/api/turnusy/staff-options").param("turnusCode", "T1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.seasonType").value("SUMMER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.roles[0].value").value("kandydat_na_animatora"));
        mockMvc.perform(get("/api/turnuses"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].staffCatalogue").doesNotExist());
    }
    @Test
    void missingBlankOrUnknownTurnusOptionsUseExplicitErrors() throws Exception {
        mockMvc.perform(get("/api/turnuses/staff-options")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/turnuses/staff-options").param("turnusCode", " ")).andExpect(status().isBadRequest());
        when(provider.getByCode("UNKNOWN")).thenThrow(new pl.srm.registrationapi.registration.exception.RegistrationException(
                "TURNUS_NOT_FOUND", "Nie znaleziono turnusu."));
        mockMvc.perform(get("/api/turnuses/staff-options").param("turnusCode", "UNKNOWN")).andExpect(status().isNotFound());
        when(provider.getByCode("T1")).thenReturn(new pl.srm.registrationapi.turnus.model.Turnus("T1", "Turnus", null, "Place",
                pl.srm.registrationapi.turnus.model.SeasonType.SUMMER, 2026, java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 10), 14, 60, 10, true, true, true));
        mockMvc.perform(get("/api/turnuses/staff-options").param("turnusCode", "T1")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void supportsCanonicalTurnusesEndpoint() throws Exception {
        mockMvc.perform(get("/api/turnuses"))
                .andExpect(status().isOk());
    }

    @Test
    void keepsLegacyTurnusyEndpointAsAlias() throws Exception {
        mockMvc.perform(get("/api/turnusy"))
                .andExpect(status().isOk());
    }

    @Test
    void staffQueryUsesStaffCatalogueAndInvalidTypesAreBadRequests() throws Exception {
        mockMvc.perform(get("/api/turnuses").param("registrationType", "STAFF")).andExpect(status().isOk());
        org.mockito.Mockito.verify(provider).getAvailableTurnuses(pl.srm.registrationapi.registration.model.RegistrationType.STAFF);
        mockMvc.perform(get("/api/turnuses").param("registrationType", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    @Test
    void unavailableCatalogueReturns503WithPublicErrorContract() throws Exception {
        when(provider.getAvailableTurnuses(pl.srm.registrationapi.registration.model.RegistrationType.PARTICIPANT)).thenThrow(new pl.srm.registrationapi.registration.exception.RegistrationException(
                "TURNUS_CATALOGUE_UNAVAILABLE", "Lista turnusów jest chwilowo niedostępna."));
        mockMvc = MockMvcBuilders.standaloneSetup(new TurnusController(provider))
                .setControllerAdvice(new pl.srm.registrationapi.common.exception.GlobalExceptionHandler()).build();
        for (String path : new String[]{"/api/turnuses", "/api/turnusy"}) {
            mockMvc.perform(get(path)).andExpect(status().isServiceUnavailable())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                            .value("TURNUS_CATALOGUE_UNAVAILABLE"));
        }
    }
}
