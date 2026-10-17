package pl.srm.emailservice.email.infrastructure;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pl.srm.emailservice.email.domain.EmailMessage;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpEmailSenderTest {
    private static final EmailMessage MESSAGE = new EmailMessage("user@example.test", "Subject", "<p>body</p>");
    private final JavaMailSenderImpl mailSender = mock(JavaMailSenderImpl.class);
    private SmtpEmailSender sender(boolean enabled) {
        when(mailSender.getSession()).thenReturn(Session.getInstance(boundedProperties()));
        var sender = new SmtpEmailSender(mailSender, "sender@example.test", enabled);
        clearInvocations(mailSender);
        return sender;
    }
    private static Properties boundedProperties() {
        var properties = new Properties();
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        return properties;
    }
    private MimeMessage message() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        return mime;
    }
    @Test void propagatesSmtpFailure() {
        message();
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        assertThrows(MailSendException.class, () -> sender(true).send(MESSAGE));
    }
    @Test void propagatesAuthenticationFailure() {
        message();
        doThrow(new MailAuthenticationException("Rejected")).when(mailSender).send(any(MimeMessage.class));
        assertThrows(MailAuthenticationException.class, () -> sender(true).send(MESSAGE));
    }
    @Test void disabledSendingReturnsFalseWithoutContactingSmtp() {
        assertFalse(sender(false).send(MESSAGE));
        verifyNoInteractions(mailSender);
    }
    @Test void successMeansSmtpAcceptedAndPreservesUtf8Content() throws Exception {
        MimeMessage mime = message();
        assertTrue(sender(true).send(new EmailMessage("user@example.test", "Żagle — potwierdzenie", "<p>Łódź</p>")));
        verify(mailSender).send(mime);
        assertEquals("Żagle — potwierdzenie", mime.getSubject());
        assertEquals("sender@example.test", mime.getFrom()[0].toString());
    }
    @Test void refusesInvalidEffectiveSmtpTimeouts() {
        for (String property : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            for (String value : new String[]{"0", "-1", "invalid", "2147483648"}) {
                var properties = boundedProperties();
                properties.setProperty("mail.smtp." + property, value);
                var mail = new JavaMailSenderImpl(); mail.setJavaMailProperties(properties);
                assertThrows(IllegalArgumentException.class, () -> new SmtpEmailSender(mail, "s@example.test", true));
            }
        }
    }
    @Test void validatesTheEffectiveSessionInsteadOfUnusedSenderProperties() {
        var mail = new JavaMailSenderImpl(); mail.setJavaMailProperties(boundedProperties());
        var sessionProperties = boundedProperties(); sessionProperties.setProperty("mail.smtp.timeout", "0");
        mail.setSession(Session.getInstance(sessionProperties));
        assertThrows(IllegalArgumentException.class, () -> new SmtpEmailSender(mail, "s@example.test", true));
    }
}
