package pl.srm.emailservice.email.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email.templates")
public record EmailTemplateProperties(
        String regulationsUrl,
        String infoUrl,
        String contactEmail,
        String contactPhone,
        String logoUrl
) {
}
