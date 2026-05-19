package pl.srm.emailservice.email.domain;

public record EmailMessage(String to, String subject, String htmlBody) {
}
