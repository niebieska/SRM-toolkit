package pl.srm.biuroapi.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import static org.assertj.core.api.Assertions.assertThat;

class JwtStartupTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                try {
                    for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
                        sources.addLast(source);
                } catch (IOException ex) { throw new UncheckedIOException(ex); }
            }).withUserConfiguration(Startup.class);

    @Test void missingSecretFailsStartupWithoutKnownDefault() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_SECRET");
        });
    }

    @Test void suppliedSecretStartsAndUsesTwoHourLifetime() {
        runner.withPropertyValues("JWT_SECRET=" + JwtConfigTest.SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            var issued = java.time.Instant.now();
            assertThat(context.getBean(JwtConfig.class).expiresAt(issued)).isEqualTo(issued.plusSeconds(7200));
        });
    }

    @Configuration
    @Import(JwtConfig.class)
    static class Startup {
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() { return new PropertySourcesPlaceholderConfigurer(); }
    }
}
