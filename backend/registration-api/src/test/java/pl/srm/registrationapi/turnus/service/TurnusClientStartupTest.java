package pl.srm.registrationapi.turnus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pl.srm.registrationapi.season.StaffCatalogueValidator;
import pl.srm.registrationapi.turnus.validator.TurnusCatalogueValidator;

import static org.assertj.core.api.Assertions.assertThat;

class TurnusClientStartupTest {
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
            assertThat(context.getStartupFailure()).hasStackTraceContaining("TURNUS_LOOKUP_PASSWORD");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankEnvironmentPasswordPreventsStartup(String password) {
        runner.withPropertyValues("TURNUS_LOOKUP_PASSWORD=" + password).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Turnus lookup password must not be blank");
        });
    }

    @Test
    void configuredEnvironmentCredentialsAllowStartup() {
        runner.withPropertyValues("TURNUS_LOOKUP_USERNAME=sit-service",
                "TURNUS_LOOKUP_PASSWORD=test-startup-password").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BiuroTurnusClient.class);
        });
    }

    @Configuration
    @Import({BiuroTurnusClient.class, TurnusCatalogueValidator.class, StaffCatalogueValidator.class})
    static class StartupConfiguration {
        @Bean LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
        @Bean ObjectMapper mapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
        @Bean static ApplicationConversionService conversionService() {
            return new ApplicationConversionService();
        }
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
