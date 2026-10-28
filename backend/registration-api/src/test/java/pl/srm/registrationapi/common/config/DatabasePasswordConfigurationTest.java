package pl.srm.registrationapi.common.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabasePasswordConfigurationTest {
    @ParameterizedTest
    @CsvSource({"application.yml,DB_PASSWORD", "timeout-contract/application.yml,BIURO_DB_PASSWORD"})
    void databasesHaveNoFallbackPassword(String resource, String variable) throws Exception {
        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (var source : new YamlPropertySourceLoader().load("database-config", new ClassPathResource(resource))) {
            sources.addLast(source);
        }
        assertThrows(IllegalArgumentException.class, () -> environment.getRequiredProperty("spring.datasource.password"));
        sources.addFirst(new MapPropertySource("explicit-credential", Map.of(variable, "test-database-password")));
        assertEquals("test-database-password", environment.getRequiredProperty("spring.datasource.password"));
    }
}
