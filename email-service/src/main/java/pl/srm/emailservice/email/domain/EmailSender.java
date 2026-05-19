package pl.srm.emailservice.email.domain;

public interface EmailSender {
    void send(EmailMessage message);
}
