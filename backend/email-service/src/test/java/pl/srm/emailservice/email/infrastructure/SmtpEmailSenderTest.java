package pl.srm.emailservice.email.infrastructure;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pl.srm.emailservice.email.domain.EmailMessage;
import java.util.Properties;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
    @Test void logsDeliveryContextAndMessageIdOnlyAfterSmtpAcceptance() throws Exception {
        MimeMessage mime = message();
        doAnswer(invocation -> {
            mime.setHeader("Message-ID", "<accepted@example.test>");
            return null;
        }).when(mailSender).send(mime);
        Logger logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        try {
            assertTrue(sender(true).send(new EmailMessage("user@example.test", "Subject", "private body",
                    "status-update", "REG-P-T1-1")));
            assertEquals(1, logs.list.size());
            String entry = logs.list.getFirst().getFormattedMessage();
            assertTrue(entry.contains("Message accepted by SMTP server"));
            assertTrue(entry.contains("to=user@example.test"));
            assertTrue(entry.contains("template=status-update"));
            assertTrue(entry.contains("registrationCode=REG-P-T1-1"));
            assertTrue(entry.contains("messageId=<accepted@example.test>"));
            assertFalse(entry.contains("private body"));
            logs.list.clear();
            doThrow(new MailSendException("SMTP down")).when(mailSender).send(mime);
            assertThrows(MailSendException.class, () -> sender(true).send(MESSAGE));
            assertTrue(logs.list.isEmpty());
        } finally { logger.detachAppender(logs); logs.stop(); }
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
    @Test void unavailableMessageIdDoesNotTurnAcceptedSendIntoFailure() throws Exception {
        MimeMessage mime = spy(message());
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new jakarta.mail.MessagingException("Header unavailable")).when(mime).getMessageID();
        assertTrue(sender(true).send(MESSAGE));
        verify(mailSender).send(mime);
    }
    @Test void validatesTheEffectiveSessionInsteadOfUnusedSenderProperties() {
        var mail = new JavaMailSenderImpl(); mail.setJavaMailProperties(boundedProperties());
        var sessionProperties = boundedProperties(); sessionProperties.setProperty("mail.smtp.timeout", "0");
        mail.setSession(Session.getInstance(sessionProperties));
        assertThrows(IllegalArgumentException.class, () -> new SmtpEmailSender(mail, "s@example.test", true));
    }
}
