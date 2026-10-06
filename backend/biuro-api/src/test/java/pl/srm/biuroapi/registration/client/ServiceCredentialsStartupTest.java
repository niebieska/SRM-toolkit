package pl.srm.biuroapi.registration.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import pl.srm.biuroapi.common.config.RestClientConfig;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCredentialsStartupTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(context -> {
                // Isolate configuration tests from credentials set on the developer/CI machine.
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                try {
                    for (var source : new YamlPropertySourceLoader()
                            .load("application", new ClassPathResource("application.yml"))) {
                        sources.addLast(source);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            })
            .withUserConfiguration(StartupConfiguration.class);

    @Test
    void missingEnvironmentPasswordPreventsStartup() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("REGISTRATION_SERVICE_PASSWORD");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankEnvironmentPasswordPreventsStartup(String password) {
        runner.withPropertyValues("REGISTRATION_SERVICE_PASSWORD=" + password).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Registration service password must not be blank");
        });
    }

    @Test
    void configuredEnvironmentCredentialsAllowStartup() {
        runner.withPropertyValues("REGISTRATION_SERVICE_USERNAME=sit-service",
                "REGISTRATION_SERVICE_PASSWORD=test-startup-password").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RegistrationApiClient.class);
        });
    }

    @Configuration
    @Import({RegistrationApiClient.class, RestClientConfig.class})
    static class StartupConfiguration {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
