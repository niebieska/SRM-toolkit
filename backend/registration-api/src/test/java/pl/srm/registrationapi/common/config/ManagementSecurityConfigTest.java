package pl.srm.registrationapi.common.config;

import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import pl.srm.registrationapi.common.exception.GlobalExceptionHandler;
import pl.srm.registrationapi.registration.controller.AcceptedCountsController;
import pl.srm.registrationapi.registration.controller.TurnusListController;
import pl.srm.registrationapi.registration.service.management.TurnusListService;
import pl.srm.registrationapi.registration.controller.RegistrationController;
import pl.srm.registrationapi.registration.exception.RegistrationException;
import pl.srm.registrationapi.registration.repository.RegistrationRepository;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.submission.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.submission.StaffRegistrationService;
import pl.srm.registrationapi.season.TestStaffCatalogues;
import pl.srm.registrationapi.turnus.controller.TurnusController;
import pl.srm.registrationapi.turnus.model.SeasonType;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

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
                "/api/registrations/participant", "/api/registrations/staff", "/api/registrations/accepted-counts?turnusCode=T1",
                "/api/registrations/turnus-list?turnusCode=T1")) {
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

    @ParameterizedTest
    @MethodSource("invalidAuthenticationHeaders")
    void rejectsMalformedOrUnsupportedAuthenticationWithoutInvokingManagement(String header) throws Exception {
        mvc.perform(patch("/api/registrations/ABC/status")
                .header(HttpHeaders.AUTHORIZATION, header)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WAITLIST\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        verifyNoInteractions(management);
    }

    static Stream<String> invalidAuthenticationHeaders() {
        return Stream.of(
                "Basic",
                "Basic ",
                "Basic not-valid-base64!",
                "Basic " + Base64.getEncoder().encodeToString(
                        "no-colon".getBytes(StandardCharsets.UTF_8)),
                "Basic " + HttpHeaders.encodeBasicAuth("unknown-service", "test-password", null),
                "Bearer not-a-service-credential"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "participant, INVALID_PESEL, 400",
            "participant, MISSING_GUARDIAN, 400",
            "participant, ALREADY_REGISTERED, 409",
            "staff, INVALID_PESEL, 400",
            "staff, MISSING_CONSENTS, 400",
            "staff, ALREADY_REGISTERED, 409"
    })
    void preservesPublicValidationErrorsThroughSecurity(String type, String code, int expectedStatus)
            throws Exception {
        String message = "Registration validation failed";
        RegistrationException failure = new RegistrationException(code, message);
        if ("participant".equals(type)) {
            when(context.getBean(ParticipantRegistrationService.class).register(any())).thenThrow(failure);
        } else {
            when(context.getBean(StaffRegistrationService.class).register(any())).thenThrow(failure);
        }
        mvc.perform(post("/api/registrations/" + type)
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        verifyNoInteractions(management);
    }

    @Test
    void allowsAuthenticatedManagementCallsWithoutCreatingSession() throws Exception {
        for (String path : List.of("/api/registrations", "/api/registrations/ABC",
                "/api/registrations/participant", "/api/registrations/staff", "/api/registrations/accepted-counts?turnusCode=T1")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, credentials))
                    .andExpect(status().isOk()).andExpect(cookie().doesNotExist("JSESSIONID"));
        }
        mvc.perform(patch("/api/registrations/ABC/status").header(HttpHeaders.AUTHORIZATION, credentials)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"WAITLIST\",\"actorAccountId\":\"actor-id\",\"actorUsername\":\"operator\"}"))
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
    void staffOptionsArePublicWithoutExposingManagementOperations() throws Exception {
        var provider = context.getBean(TurnusProvider.class);
        when(provider.getByCode("T1")).thenReturn(new Turnus("T1", "Turnus", null, "Place",
                SeasonType.WINTER, 2027,
                LocalDate.of(2027, 1, 17), LocalDate.of(2027, 1, 24),
                14, 50, 10, true, true, true, TestStaffCatalogues.load("winter")));
        mvc.perform(get("/api/turnusy/staff-options").param("turnusCode", "T1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonType").value("WINTER"));
        mvc.perform(get("/api/registrations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(management);
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
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ManagementSecurityConfig().serviceUsers("biuro-service", " "));
    }

    @Test
    void turnusListsRequireCredentialsAndRejectMissingCode() throws Exception {
        var lists = context.getBean(TurnusListService.class);
        when(lists.accepted("T1")).thenReturn(List.of());
        mvc.perform(get("/api/registrations/turnus-list").param("turnusCode", "T1")
                .header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(lists).accepted("T1");
        clearInvocations(lists);
        mvc.perform(get("/api/registrations/turnus-list").header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/registrations/turnus-list").param("turnusCode", " ")
                .header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lists);
    }

    @Test
    void missingOrBlankTurnusCodeReturns400WithoutQueryingDatabase() throws Exception {
        var repository = context.getBean(RegistrationRepository.class);
        mvc.perform(get("/api/registrations/accepted-counts").header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        for (String code : new String[]{"", " "}) {
            mvc.perform(get("/api/registrations/accepted-counts").param("turnusCode", code)
                    .header(HttpHeaders.AUTHORIZATION, credentials))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void acceptedCountsRequireServiceCredentialsAndUseTypeSpecificDatabaseCounts() throws Exception {
        var repository = context.getBean(RegistrationRepository.class);
        when(repository.countByTurnusCodeAndRegistrationTypeAndStatus("T1", "PARTICIPANT", "ACCEPTED")).thenReturn(42L);
        when(repository.countByTurnusCodeAndRegistrationTypeAndStatus("T1", "STAFF", "ACCEPTED")).thenReturn(8L);
        mvc.perform(get("/api/registrations/accepted-counts").param("turnusCode", "T1")
                .header(HttpHeaders.AUTHORIZATION, credentials)).andExpect(status().isOk())
                .andExpect(jsonPath("$.participants").value(42)).andExpect(jsonPath("$.staff").value(8));
    }

    @Configuration
    @EnableWebMvc
    static class TestMvcConfig {
        @Bean TurnusListService listsService() { return mock(TurnusListService.class); }
        @Bean TurnusListController lists(TurnusListService service) { return new TurnusListController(service); }
        @Bean RegistrationRepository repository() {
            return mock(RegistrationRepository.class);
        }
        @Bean AcceptedCountsController counts(
                RegistrationRepository repository) {
            return new AcceptedCountsController(repository);
        }
        @Bean GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
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
