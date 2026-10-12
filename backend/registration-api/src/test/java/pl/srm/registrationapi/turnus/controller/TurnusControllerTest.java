package pl.srm.registrationapi.turnus.controller;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.srm.registrationapi.common.exception.GlobalExceptionHandler;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.season.TestStaffCatalogues;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TurnusControllerTest {

    private final TurnusProvider provider = mock(TurnusProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(provider.getAvailableTurnuses(RegistrationType.PARTICIPANT)).thenReturn(List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(new TurnusController(provider))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    Turnus definition() {
        return new Turnus("T1", "Turnus", null, "Place",
                SeasonType.SUMMER, 2026,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10),
                14, 60, 10, true, true, true, TestStaffCatalogues.load("summer"));
    }
    @Test
    void staffOptionsUseSelectedTurnusAndAreNotIncludedInOrdinaryPublicList() throws Exception {
        when(provider.getByCode("T1")).thenReturn(definition());
        when(provider.getAvailableTurnuses(RegistrationType.PARTICIPANT))
                .thenReturn(List.of(definition()));
        mockMvc.perform(get("/api/turnusy/staff-options").param("turnusCode", "T1"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.seasonType").value("SUMMER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.roles[0].value").value("kandydat_na_animatora"));
        mockMvc.perform(get("/api/turnuses"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].staffCatalogue").doesNotExist());
    }
    @Test
    void missingBlankOrUnknownTurnusOptionsUseExplicitErrors() throws Exception {
        mockMvc.perform(get("/api/turnuses/staff-options")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/turnuses/staff-options").param("turnusCode", " ")).andExpect(status().isBadRequest());
        when(provider.getByCode("UNKNOWN")).thenThrow(new RegistrationException(
                "TURNUS_NOT_FOUND", "Nie znaleziono turnusu."));
        mockMvc.perform(get("/api/turnuses/staff-options").param("turnusCode", "UNKNOWN")).andExpect(status().isNotFound());
        when(provider.getByCode("T1")).thenReturn(new Turnus("T1", "Turnus", null, "Place",
                SeasonType.SUMMER, 2026, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10), 14, 60, 10, true, true, true));
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
        Mockito.verify(provider).getAvailableTurnuses(RegistrationType.STAFF);
        mockMvc.perform(get("/api/turnuses").param("registrationType", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    @Test
    void unavailableCatalogueReturns503WithPublicErrorContract() throws Exception {
        when(provider.getAvailableTurnuses(RegistrationType.PARTICIPANT)).thenThrow(new RegistrationException(
                "TURNUS_CATALOGUE_UNAVAILABLE", "Lista turnusów jest chwilowo niedostępna."));
        mockMvc = MockMvcBuilders.standaloneSetup(new TurnusController(provider))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String path : new String[]{"/api/turnuses", "/api/turnusy"}) {
            mockMvc.perform(get(path)).andExpect(status().isServiceUnavailable())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                            .value("TURNUS_CATALOGUE_UNAVAILABLE"));
        }
    }
}
