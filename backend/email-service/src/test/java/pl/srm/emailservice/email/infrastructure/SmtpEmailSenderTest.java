package pl.srm.emailservice.email.infrastructure;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import pl.srm.emailservice.email.domain.EmailMessage;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    @Test
    void sendSwallowsSendFailures() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "noreply@srm.pl", true);

        assertDoesNotThrow(() -> sender.send(new EmailMessage("user@example.com", "Subject", "<html>body</html>")));
    }
}
