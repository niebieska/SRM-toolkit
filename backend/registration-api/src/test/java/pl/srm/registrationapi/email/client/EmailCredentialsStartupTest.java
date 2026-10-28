package pl.srm.registrationapi.email.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import pl.srm.registrationapi.common.config.RestClientConfig;

import static org.assertj.core.api.Assertions.assertThat;

class EmailCredentialsStartupTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            })
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RestClientConfig.class, EmailServiceClient.class, PlaceholderConfiguration.class);

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
    void explicitEnvironmentCredentialsAllowStartup() {
        runner.withPropertyValues("EMAIL_SERVICE_PASSWORD=test-email-password")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EmailServiceClient.class));
    }

    @Configuration
    static class PlaceholderConfiguration {
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() { return new PropertySourcesPlaceholderConfigurer(); }
    }
}
