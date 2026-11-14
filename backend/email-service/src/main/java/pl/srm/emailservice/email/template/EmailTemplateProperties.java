package pl.srm.emailservice.email.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email.templates")
public record EmailTemplateProperties(
        String infoUrl,
        String contactEmail,
        String biuroName,
        String contactPhone,
        String priestName,
        String priestPhone,
        String websiteUrl,
        String websiteLabel,
        String facebookUrl,
        String facebookLabel,
        String instagramUrl,
        String instagramLabel,
        String participantCardAdultUrl,
        boolean participantCardAdultEnabled,
        String participantCardMinorUrl,
        String participantBankAccount,
        String participantDepositAmount
) {
}
