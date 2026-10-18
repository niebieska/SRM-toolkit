package pl.srm.emailservice.common.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import pl.srm.emailservice.email.api.EmailController;
import pl.srm.emailservice.email.service.EmailDispatchService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailSecurityConfigTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private EmailDispatchService dispatch;
    private final String credentials = "Basic " + HttpHeaders.encodeBasicAuth("email-sender", "test-email-password", null);
    private static final String REQUEST = """
            {"to":"recipient@example.test","templateName":"status-update","variables":{}}
            """;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "email.service.username=email-sender", "email.service.password=test-email-password");
        context.register(EmailSecurityConfig.class, TestConfiguration.class);
        context.refresh();
        dispatch = context.getBean(EmailDispatchService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void anonymousSendIsRejectedBeforeDispatch() throws Exception {
        mvc.perform(post("/api/email/send").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatch);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic !!!", "Basic", "Basic bm9jb2xvbg==", "Basic ZW1haWwtc2VuZGVyOndyb25n", "Bearer invalid"})
    void malformedOrWrongCredentialsCannotSend(String header) throws Exception {
        mvc.perform(post("/api/email/send").header(HttpHeaders.AUTHORIZATION, header)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatch);
    }

    @Test
    void anotherServiceAccountCannotSendEvenWithTheSamePassword() throws Exception {
        String managementCredentials = "Basic " + HttpHeaders.encodeBasicAuth("biuro-service", "test-email-password", null);
        mvc.perform(post("/api/email/send").header(HttpHeaders.AUTHORIZATION, managementCredentials)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatch);
    }

    @Test
    void authenticatedSendPreservesDisabledAndFailureResponses() throws Exception {
        mvc.perform(post("/api/email/send").header(HttpHeaders.AUTHORIZATION, credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
        when(dispatch.sendEmail(anyString(), anyString(), anyMap())).thenThrow(new IllegalStateException("private"));
        mvc.perform(post("/api/email/send").header(HttpHeaders.AUTHORIZATION, credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Email sending failed; delivery may be uncertain."));
        verify(dispatch, times(2)).sendEmail(eq("recipient@example.test"), eq("status-update"), anyMap());
    }

    @Test
    void authenticatedInvalidRequestRemainsBadRequest() throws Exception {
        mvc.perform(post("/api/email/send").header(HttpHeaders.AUTHORIZATION, credentials)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dispatch);
    }

    @Test
    void credentialsDoNotGrantOtherMethodsOrRoutes() throws Exception {
        mvc.perform(get("/api/email/send").header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isForbidden());
        mvc.perform(post("/other").header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dispatch);
    }

    @Configuration
    @EnableWebMvc
    static class TestConfiguration {
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() { return new PropertySourcesPlaceholderConfigurer(); }
        @Bean EmailDispatchService dispatch() { return mock(EmailDispatchService.class); }
        @Bean EmailController controller(EmailDispatchService dispatch) { return new EmailController(dispatch); }
    }
}
