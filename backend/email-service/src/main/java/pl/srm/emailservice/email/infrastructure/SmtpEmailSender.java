package pl.srm.emailservice.email.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import jakarta.mail.Session;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import pl.srm.emailservice.email.domain.EmailMessage;
import pl.srm.emailservice.email.domain.EmailSender;

@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSenderImpl mailSender;
    private final String from;
    private final boolean enabled;

    public SmtpEmailSender(JavaMailSenderImpl mailSender,
                           @Value("${email.from:noreply@srm.pl}") String from,
                           @Value("${email.enabled:true}") boolean enabled) {
        Session session = mailSender.getSession();
        String protocol = mailSender.getProtocol();
        if (protocol == null) protocol = session.getProperty("mail.transport.protocol");
        if (protocol == null) protocol = "smtp";
        // Inspect the session used by the transport, including bracket-map and
        // externally supplied Session overrides; do not rebind separate values.
        for (String timeout : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            String property = "mail." + protocol + "." + timeout;
            int milliseconds;
            try { milliseconds = Integer.parseInt(session.getProperty(property)); }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException(property + " must be positive integer milliseconds");
            }
            Assert.isTrue(milliseconds > 0, property + " must be positive integer milliseconds");
        }
        this.mailSender = mailSender;
        this.from = from;
        this.enabled = enabled;
    }

    @Override
    public boolean send(EmailMessage message) {
        if (!enabled) {
            LOGGER.info("SMTP sending disabled; no message sent");
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            mailSender.send(mimeMessage);
            LOGGER.info("Message accepted by SMTP server");
            return true;
        } catch (MessagingException exception) {
            throw new MailPreparationException("Cannot prepare email message", exception);
        }
    }
}
