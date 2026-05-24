package pl.srm.emailservice.email.api;

public record SendEmailResponse(boolean success,
                                String message) {
}
