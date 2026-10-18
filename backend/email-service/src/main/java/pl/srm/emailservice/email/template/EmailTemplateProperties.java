package pl.srm.emailservice.email.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "templates")
public record EmailTemplateProperties(
        String regulationsUrl,
        String infoUrl,
        String contactEmail,
        String contactPhone,
        String logoUrl,
        String participantCardAdultUrl,
        String participantCardMinorUrl,
        String participantBankAccount,
        String participantDepositAmount
) {
}
