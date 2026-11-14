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
            assertThat(properties.infoUrl()).isNotBlank();
            assertThat(properties.contactEmail()).isNotBlank();
            assertThat(properties.biuroName()).isNotBlank();
            assertThat(properties.contactPhone()).isNotBlank();
            assertThat(properties.priestName()).isNotBlank();
            assertThat(properties.priestPhone()).isNotBlank();
            assertThat(properties.websiteUrl()).isNotBlank();
            assertThat(properties.websiteLabel()).isNotBlank();
            assertThat(properties.facebookUrl()).isNotBlank();
            assertThat(properties.facebookLabel()).isNotBlank();
            assertThat(properties.instagramUrl()).isNotBlank();
            assertThat(properties.instagramLabel()).isNotBlank();
            assertThat(properties.participantCardAdultUrl()).isNotBlank();
            assertThat(properties.participantCardAdultEnabled()).isFalse();
            assertThat(properties.participantCardMinorUrl()).isNotBlank();
        });
    }

    @Test
    void explicitServerContactSettingIsBound() {
        runner.withPropertyValues("email.templates.contact-email=controlled@example.test").run(context ->
                assertThat(context.getBean(EmailTemplateProperties.class).contactEmail())
                        .isEqualTo("controlled@example.test"));
    }

    @Test
    void legacyContactPhoneEnvironmentPropertyRemainsSupported() {
        runner.withPropertyValues("EMAIL_CONTACT_PHONE=+48 111 222 333").run(context ->
                assertThat(context.getBean(EmailTemplateProperties.class).contactPhone())
                        .isEqualTo("+48 111 222 333"));
    }
}
