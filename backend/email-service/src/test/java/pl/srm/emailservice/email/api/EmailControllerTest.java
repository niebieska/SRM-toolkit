package pl.srm.emailservice.email.api;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.mail.MailSendException;
import pl.srm.emailservice.email.service.EmailDispatchService;
import java.util.Map;
import java.net.UnknownHostException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailControllerTest {
    final EmailDispatchService service = mock(EmailDispatchService.class);
    final EmailController controller = new EmailController(service);
    final SendEmailRequest request = new SendEmailRequest("recipient@example.test", "status-update", Map.of("registrationCode", "REG-1"));

    @Test void reportsSmtpAcceptanceWithoutClaimingInboxDelivery() {
        when(service.sendEmail(request.to(), request.templateName(), request.variables())).thenReturn(true);
        var response = controller.sendEmail(request);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertEquals("Message accepted by SMTP server.", response.getBody().message());
    }
    @Test void disabledSendingIsNotReportedAsSuccess() {
        when(service.sendEmail(request.to(), request.templateName(), request.variables())).thenReturn(false);
        var response = controller.sendEmail(request);
        assertEquals(200, response.getStatusCode().value());
        assertFalse(response.getBody().success());
        assertTrue(response.getBody().message().contains("disabled"));
    }
    @Test void smtpFailureLogsProviderDetailsButKeepsResponseGeneric() {
        when(service.sendEmail(request.to(), request.templateName(), request.variables()))
                .thenThrow(new MailSendException("SMTP connection failed",
                        new UnknownHostException("smtp.example.test")));
        Logger logger = (Logger) LoggerFactory.getLogger(EmailController.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            var response = controller.sendEmail(request);
            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().success());
            assertTrue(response.getBody().message().contains("uncertain"));
            assertFalse(response.getBody().message().contains("secret"));
            assertFalse(response.getBody().message().contains("recipient@"));
            assertEquals(1, logs.list.size());
            var event = logs.list.getFirst();
            assertTrue(event.getFormattedMessage().contains(request.to()));
            assertTrue(event.getFormattedMessage().contains(request.templateName()));
            assertNotNull(event.getThrowableProxy());
            assertEquals(MailSendException.class.getName(), event.getThrowableProxy().getClassName());
            var cause = event.getThrowableProxy().getCause();
            assertNotNull(cause);
            assertEquals(UnknownHostException.class.getName(), cause.getClassName());
            assertEquals("smtp.example.test", cause.getMessage());
            assertFalse(response.getBody().message().contains("smtp.example.test"));
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
    @Test void malformedOrUnsupportedRequestsNeverClaimSuccess() {
        assertEquals(400, controller.sendEmail(null).getStatusCode().value());
        assertEquals(400, controller.sendEmail(new SendEmailRequest("", "status-update", Map.of())).getStatusCode().value());
        when(service.sendEmail(request.to(), request.templateName(), request.variables())).thenThrow(new IllegalArgumentException("private-data"));
        var response = controller.sendEmail(request);
        assertEquals(400, response.getStatusCode().value());
        assertFalse(response.getBody().success());
        assertFalse(response.getBody().message().contains("private-data"));
    }
}
