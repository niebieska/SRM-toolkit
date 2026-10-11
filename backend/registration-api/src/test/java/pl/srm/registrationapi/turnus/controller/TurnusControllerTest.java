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
        when(provider.getAvailableTurnuses()).thenReturn(List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(new TurnusController(provider)).build();
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
    void unavailableCatalogueReturns503WithPublicErrorContract() throws Exception {
        when(provider.getAvailableTurnuses()).thenThrow(new pl.srm.registrationapi.registration.exception.RegistrationException(
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
