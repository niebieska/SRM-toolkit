package pl.srm.emailservice.email.api;

import java.util.Map;

public record SendEmailRequest(String to,
                               String templateName,
                               Map<String, String> variables) {
}
