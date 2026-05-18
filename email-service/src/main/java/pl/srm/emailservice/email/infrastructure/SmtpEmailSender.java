package pl.srm.emailservice.email.infrastructure;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pl.srm.emailservice.email.domain.EmailMessage;
import pl.srm.emailservice.email.domain.EmailSender;

@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean enabled;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${email.from:noreply@srm.pl}") String from,
                           @Value("${email.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.from = from;
        this.enabled = enabled;
    }

    @Override
    public void send(EmailMessage message) {
        if (!enabled) {
            LOGGER.info("Email sending disabled");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            mailSender.send(mimeMessage);
            LOGGER.info("Email sent successfully to {}", message.to());
        } catch (Exception exception) {
            LOGGER.error("Failed to send email to {}", message.to(), exception);
            throw new RuntimeException("Failed to send email.", exception);
        }
    }
}
