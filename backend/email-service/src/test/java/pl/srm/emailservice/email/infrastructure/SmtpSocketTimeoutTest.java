package pl.srm.emailservice.email.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pl.srm.emailservice.email.domain.EmailMessage;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SmtpSocketTimeoutTest {
    @Test void silentSmtpServerCannotHoldTheSendIndefinitely() throws Exception {
        CountDownLatch connected = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newSingleThreadExecutor()) {
            socket.setSoTimeout(3000);
            var server = executor.submit(() -> {
                try (var client = socket.accept()) {
                    connected.countDown();
                    // Deliberately never send the SMTP greeting.
                    release.await(3, TimeUnit.SECONDS);
                } catch (Exception ex) { throw new RuntimeException(ex); }
            });
            try {
                var mail = new JavaMailSenderImpl();
                mail.setHost("127.0.0.1"); mail.setPort(socket.getLocalPort());
                mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "300");
                mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "150");
                mail.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "150");
                var sender = new SmtpEmailSender(mail, "sender@example.test", true);
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(MailSendException.class,
                        () -> sender.send(new EmailMessage("recipient@example.test", "Synthetic", "<p>Test</p>"))));
                assertTrue(connected.await(1, TimeUnit.SECONDS));
            } finally { release.countDown(); }
            server.get(2, TimeUnit.SECONDS);
        }
    }
}
