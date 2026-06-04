package pl.srm.emailservice.email.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pl.srm.emailservice.email.template.EmailTemplateProperties;

@Configuration
@EnableConfigurationProperties(EmailTemplateProperties.class)
public class EmailConfig {
}
