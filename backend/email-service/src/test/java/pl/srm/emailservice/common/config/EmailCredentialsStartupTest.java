package pl.srm.emailservice.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

class EmailCredentialsStartupTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(StartupConfiguration.class);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingOrBlankPasswordPreventsStartup(String password) {
        var configured = password == null ? runner : runner.withPropertyValues("EMAIL_SERVICE_PASSWORD=" + password);
        configured.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(password == null
                    ? "EMAIL_SERVICE_PASSWORD" : "Email service password must not be blank");
        });
    }

    @Test
    void blankUsernamePreventsStartup() {
        runner.withPropertyValues("EMAIL_SERVICE_PASSWORD=test-email-password", "EMAIL_SERVICE_USERNAME=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitCredentialsAllowStartup() {
        runner.withPropertyValues("EMAIL_SERVICE_PASSWORD=test-email-password").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(UserDetailsService.class);
            assertThat(context.getBean(UserDetailsService.class).loadUserByUsername("registration-email-sender"))
                    .isNotNull();
        });
    }

    @Configuration
    @EnableWebMvc
    @Import(EmailSecurityConfig.class)
    static class StartupConfiguration {
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() { return new PropertySourcesPlaceholderConfigurer(); }
    }
}
