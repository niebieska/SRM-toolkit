package pl.srm.registrationapi.turnus.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.srm.registrationapi.turnus.controller.TurnusController;
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
}
