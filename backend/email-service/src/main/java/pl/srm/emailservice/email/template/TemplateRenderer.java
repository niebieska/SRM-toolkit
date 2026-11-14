package pl.srm.emailservice.email.template;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

@Component
public class TemplateRenderer {

    private final SpringTemplateEngine templateEngine;
    private final ResourceLoader resourceLoader;
    private final EmailTemplateProperties properties;

    public TemplateRenderer(SpringTemplateEngine templateEngine,
                            ResourceLoader resourceLoader,
                            EmailTemplateProperties properties) {
        this.templateEngine = templateEngine;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    public String render(String templateName, Map<String, String> variables) {
        Resource templateResource =
                resourceLoader.getResource("classpath:templates/" + templateName + ".html");

        if (!templateResource.exists()) {
            throw new IllegalArgumentException("Unknown template: " + templateName);
        }

        Map<String, Object> templateVariables = new HashMap<>();
        if (variables != null) {
            templateVariables.putAll(variables);
        }

        // Configuration-owned links, labels and contact details must not be replaceable by
        // values supplied with an email request.
        templateVariables.put("infoUrl", properties.infoUrl());
        templateVariables.put("contactEmail", properties.contactEmail());
        templateVariables.put("biuroName", properties.biuroName());
        templateVariables.put("contactPhone", properties.contactPhone());
        templateVariables.put("priestName", properties.priestName());
        templateVariables.put("priestPhone", properties.priestPhone());
        templateVariables.put("websiteUrl", properties.websiteUrl());
        templateVariables.put("websiteLabel", properties.websiteLabel());
        templateVariables.put("facebookUrl", properties.facebookUrl());
        templateVariables.put("facebookLabel", properties.facebookLabel());
        templateVariables.put("instagramUrl", properties.instagramUrl());
        templateVariables.put("instagramLabel", properties.instagramLabel());
        templateVariables.put("participantCardAdultUrl", properties.participantCardAdultUrl());
        templateVariables.put("participantCardAdultEnabled", properties.participantCardAdultEnabled());
        templateVariables.put("participantCardMinorUrl", properties.participantCardMinorUrl());
        templateVariables.put("participantBankAccount", properties.participantBankAccount());
        templateVariables.put("participantDepositAmount", properties.participantDepositAmount());
        templateVariables.put("participantPaymentInstructionsConfigured",
                StringUtils.hasText(properties.participantBankAccount())
                        && StringUtils.hasText(properties.participantDepositAmount()));

        Context context = new Context();
        context.setVariables(templateVariables);

        return templateEngine.process(templateName, context);
    }
}
