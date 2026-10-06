package pl.srm.registrationapi.common.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import pl.srm.registrationapi.registration.controller.RegistrationController;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.submission.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.submission.StaffRegistrationService;
import pl.srm.registrationapi.turnus.controller.TurnusController;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagementSecurityConfigTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private RegistrationManagementService management;
    private final String credentials = "Basic " + HttpHeaders.encodeBasicAuth("biuro-service", "test-password", null);

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "registration.service.username=biuro-service", "registration.service.password=test-password");
        context.register(ManagementSecurityConfig.class, CorsConfig.class, TestMvcConfig.class);
        context.refresh();
        management = context.getBean(RegistrationManagementService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void rejectsAnonymousManagementAccessIncludingTypeLists() throws Exception {
        for (String path : List.of("/api/registrations", "/api/registrations/ABC",
                "/api/registrations/participant", "/api/registrations/staff")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(patch("/api/registrations/ABC/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WAITLIST\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(management);
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mvc.perform(get("/api/registrations").header(HttpHeaders.AUTHORIZATION,
                "Basic " + HttpHeaders.encodeBasicAuth("biuro-service", "wrong-password", null)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(management);
    }

    @Test
    void allowsAuthenticatedManagementCallsWithoutCreatingSession() throws Exception {
        for (String path : List.of("/api/registrations", "/api/registrations/ABC",
                "/api/registrations/participant", "/api/registrations/staff")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, credentials))
                    .andExpect(status().isOk()).andExpect(cookie().doesNotExist("JSESSIONID"));
        }
        mvc.perform(patch("/api/registrations/ABC/status").header(HttpHeaders.AUTHORIZATION, credentials)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WAITLIST\"}"))
                .andExpect(status().isOk());
        verify(management).getAll();
        verify(management).getDetailByCode("ABC");
        verify(management).getParticipants();
        verify(management).getStaff();
        verify(management).updateStatus(eq("ABC"), any());
        // An authenticated request must not authenticate a later anonymous request.
        mvc.perform(get("/api/registrations")).andExpect(status().isUnauthorized());
    }

    @Test
    void keepsSubmissionsAndBothTurnusAliasesPublic() throws Exception {
        for (String type : List.of("participant", "staff")) {
            mvc.perform(post("/api/registrations/" + type)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }
        verify(context.getBean(ParticipantRegistrationService.class)).register("{}");
        verify(context.getBean(StaffRegistrationService.class)).register("{}");
        mvc.perform(get("/api/turnuses")).andExpect(status().isOk());
        mvc.perform(get("/api/turnusy")).andExpect(status().isOk());
    }

    @Test
    void preservesPublicBrowserPreflight() throws Exception {
        mvc.perform(options("/api/registrations/participant")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void deniesNonHealthActuatorAndUnknownRoutesEvenForServiceAccount() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/metrics", "/actuator/info", "/unknown")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, credentials))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void refusesBlankServiceCredentials() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ManagementSecurityConfig().serviceUsers("biuro-service", " "));
    }

    @Configuration
    @EnableWebMvc
    static class TestMvcConfig {
        @Bean ParticipantRegistrationService participantService() { return mock(ParticipantRegistrationService.class); }
        @Bean StaffRegistrationService staffService() { return mock(StaffRegistrationService.class); }
        @Bean RegistrationManagementService managementService() { return mock(RegistrationManagementService.class); }
        @Bean TurnusProvider provider() { return mock(TurnusProvider.class); }
        @Bean RegistrationController registrations(ParticipantRegistrationService participant,
                StaffRegistrationService staff, RegistrationManagementService management) {
            return new RegistrationController(participant, staff, management);
        }
        @Bean TurnusController turnuses(TurnusProvider provider) { return new TurnusController(provider); }
    }
}
