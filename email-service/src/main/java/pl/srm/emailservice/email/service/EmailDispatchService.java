package pl.srm.emailservice.email.service;

import org.springframework.stereotype.Service;
import pl.srm.emailservice.email.domain.EmailMessage;
import pl.srm.emailservice.email.domain.EmailSender;
import pl.srm.emailservice.email.template.TemplateRenderer;

import java.util.Map;

@Service
public class EmailDispatchService {

    private static final Map<String, String> SUBJECTS = Map.of(
            "registration-confirmation", "Potwierdzenie zgłoszenia rejestracyjnego",
            "status-update", "Aktualizacja statusu Twojego zgłoszenia"
    );

    private final EmailSender emailSender;
    private final TemplateRenderer templateRenderer;

    public EmailDispatchService(EmailSender emailSender,
                                TemplateRenderer templateRenderer) {
        this.emailSender = emailSender;
        this.templateRenderer = templateRenderer;
    }

    public void sendEmail(String to, String templateName, Map<String, String> variables) {
        String subject = SUBJECTS.get(templateName);
        if (subject == null) {
            throw new IllegalArgumentException("Unknown template: " + templateName);
        }

        String htmlBody = templateRenderer.render(templateName, variables == null ? Map.of() : variables);
        emailSender.send(new EmailMessage(to, subject, htmlBody));
    }
}
