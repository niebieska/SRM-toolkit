package pl.srm.emailservice.email.domain;

public interface EmailSender {
    // True means SMTP accepted the message; false means sending was disabled.
    boolean send(EmailMessage message);
}
