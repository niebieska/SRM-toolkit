package pl.srm.emailservice.email.domain;

public record EmailMessage(String to, String subject, String htmlBody,
                          String templateName, String registrationCode) {
    public EmailMessage(String to, String subject, String htmlBody) {
        this(to, subject, htmlBody, null, null);
    }
}
