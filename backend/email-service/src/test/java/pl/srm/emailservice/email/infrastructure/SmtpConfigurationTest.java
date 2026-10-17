package pl.srm.emailservice.email.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.assertThat;

class SmtpConfigurationTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(SmtpEmailSender.class)
            .withPropertyValues("spring.mail.host=localhost");

    @Test void actualApplicationConfigInstallsBoundedSocketTimeouts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
            assertThat(properties.getProperty("mail.smtp.timeout")).isEqualTo("10000");
            assertThat(properties.getProperty("mail.smtp.writetimeout")).isEqualTo("10000");
        });
    }
    @Test void documentedVariablesConfigureSmtpAuthenticationTlsAndTimeouts() {
        runner.withPropertyValues("MAIL_PORT=587", "MAIL_USER=test-user", "MAIL_PASSWORD=test-password",
                "MAIL_SMTP_AUTH=true", "MAIL_STARTTLS=true", "MAIL_CONNECT_TIMEOUT_MS=1234",
                "MAIL_READ_TIMEOUT_MS=2345", "MAIL_WRITE_TIMEOUT_MS=3456").run(context -> {
            assertThat(context).hasNotFailed();
            var sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getUsername()).isEqualTo("test-user");
            assertThat(sender.getPassword()).isEqualTo("test-password");
            var props = sender.getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("1234");
            assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("2345");
            assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo("3456");
        });
    }
    @Test void unlimitedTimeoutPreventsStartup() {
        runner.withPropertyValues("MAIL_READ_TIMEOUT_MS=0").run(context -> assertThat(context).hasFailed());
    }
    @ParameterizedTest @ValueSource(strings = {"connectiontimeout", "timeout", "writetimeout"})
    void bracketOverrideCannotBypassEffectiveTimeoutValidation(String property) {
        runner.withPropertyValues("spring.mail.properties[mail.smtp." + property + "]=0")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void positiveBracketOverridesAreValidatedAndUsed() {
        runner.withPropertyValues("spring.mail.properties[mail.smtp.connectiontimeout]=1234",
                "spring.mail.properties[mail.smtp.timeout]=2345",
                "spring.mail.properties[mail.smtp.writetimeout]=3456").run(context -> {
            assertThat(context).hasNotFailed();
            var session = context.getBean(JavaMailSenderImpl.class).getSession();
            assertThat(session.getProperty("mail.smtp.connectiontimeout")).isEqualTo("1234");
            assertThat(session.getProperty("mail.smtp.timeout")).isEqualTo("2345");
            assertThat(session.getProperty("mail.smtp.writetimeout")).isEqualTo("3456");
        });
    }

}
