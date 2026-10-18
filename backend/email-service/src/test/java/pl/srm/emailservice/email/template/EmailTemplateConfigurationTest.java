package pl.srm.emailservice.email.template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pl.srm.emailservice.email.config.EmailConfig;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EmailConfig.class);

    @Test
    void trustedVariablesBindFromTheActualApplicationConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(EmailTemplateProperties.class);
            assertThat(properties.regulationsUrl()).isNotBlank()
                    .isEqualTo(context.getEnvironment().getProperty("templates.regulations-url"));
            assertThat(properties.infoUrl()).isNotBlank();
            assertThat(properties.contactEmail()).isNotBlank();
            assertThat(properties.contactPhone()).isNotBlank();
            assertThat(properties.logoUrl()).isNotBlank();
        });
    }

    @Test
    void explicitServerContactSettingIsBound() {
        runner.withPropertyValues("templates.contact-email=controlled@example.test").run(context ->
                assertThat(context.getBean(EmailTemplateProperties.class).contactEmail())
                        .isEqualTo("controlled@example.test"));
    }
}
