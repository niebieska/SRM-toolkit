package pl.srm.registrationapi.common.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ActuatorSecurityTest.TestApplication.class,
        properties = "registration.service.password=test-service-password")
class ActuatorSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private Environment environment;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void publicHealthContainsOnlyStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        assertEquals("health", environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", environment.getProperty("management.endpoint.health.show-details"));
        assertEquals("never", environment.getProperty("management.endpoint.health.show-components"));
    }

    @Test
    void anonymousEnvironmentEndpointIsDenied() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import(ManagementSecurityConfig.class)
    static class TestApplication { }
}
