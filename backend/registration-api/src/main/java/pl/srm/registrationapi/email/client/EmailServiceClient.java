package pl.srm.registrationapi.email.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

@Component
public class EmailServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceClient.class);
    private final RestClient restClient;

    public EmailServiceClient(RestClient.Builder builder,
            @Value("${email.service.url:http://localhost:8082}") String url,
            @Value("${email.service.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${email.service.read-timeout:PT30S}") Duration readTimeout) {
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restClient = builder.clone().baseUrl(url).requestFactory(factory).build();
    }

    public void sendEmail(String to, String templateName, Map<String, String> variables) {
        String code = safeCode(variables);
        String template = safeTemplate(templateName);
        if (to == null || to.isBlank()) {
            LOGGER.warn("Email outcome=NOT_SENT code={} template={} reason=MISSING_RECIPIENT", code, template);
            return;
        }
        // No retries: a timeout or server error can follow SMTP acceptance.
        try {
            SendEmailResponse response = restClient.post().uri("/api/email/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendEmailRequest(to, templateName, variables == null ? Map.of() : variables))
                    .retrieve().body(SendEmailResponse.class);
            if (response == null || response.success() == null) {
                LOGGER.error("Email outcome=UNCONFIRMED code={} template={} reason=INVALID_RESPONSE", code, template);
            } else if (response.success()) {
                LOGGER.info("Email outcome=SMTP_ACCEPTED code={} template={}", code, template);
            } else {
                LOGGER.warn("Email outcome=NOT_SENT code={} template={} reason=SERVICE_REPORTED_NOT_SENT", code, template);
            }
        } catch (RestClientResponseException exception) {
            // Never log response bodies/messages: they can contain personal data.
            String outcome = exception.getStatusCode().is4xxClientError() ? "NOT_SENT" : "UNCONFIRMED";
            LOGGER.error("Email outcome={} code={} template={} httpStatus={}",
                    outcome, code, template, exception.getStatusCode().value());
        } catch (Exception exception) {
            LOGGER.error("Email outcome=UNCONFIRMED code={} template={} error={}",
                    code, template, exception.getClass().getSimpleName());
        }
    }

    private static void validateTimeout(Duration timeout) {
        Assert.isTrue(timeout != null && !timeout.isNegative() && timeout.toMillis() >= 1
                && timeout.toMillis() <= Integer.MAX_VALUE, "Email HTTP timeout must be 1..2147483647 milliseconds");
    }

    private static String safeCode(Map<String, String> variables) {
        String code = variables == null ? null : variables.get("registrationCode");
        return code != null && code.matches("[A-Za-z0-9_-]{1,150}") ? code : "unknown";
    }

    private static String safeTemplate(String name) {
        if (name == null) return "unsupported";
        return switch (name) {
            case "registration-confirmation-participant", "registration-confirmation-staff", "status-update" -> name;
            default -> "unsupported";
        };
    }

    private record SendEmailRequest(String to, String templateName, Map<String, String> variables) {}
    private record SendEmailResponse(Boolean success, String message) {}
}
