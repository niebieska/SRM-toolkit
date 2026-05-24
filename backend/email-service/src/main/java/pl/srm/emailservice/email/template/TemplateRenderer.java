package pl.srm.emailservice.email.template;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

@Component
public class TemplateRenderer {

    private final SpringTemplateEngine templateEngine;
    private final ResourceLoader resourceLoader;

    public TemplateRenderer(SpringTemplateEngine templateEngine,
                            ResourceLoader resourceLoader) {
        this.templateEngine = templateEngine;
        this.resourceLoader = resourceLoader;
    }

    public String render(String templateName, Map<String, String> variables) {
        Resource templateResource = resourceLoader.getResource("classpath:templates/" + templateName + ".html");
        if (!templateResource.exists()) {
            throw new IllegalArgumentException("Unknown template: " + templateName);
        }

        Context context = new Context();
        context.setVariables(new HashMap<>(variables));
        return templateEngine.process(templateName, context);
    }
}
