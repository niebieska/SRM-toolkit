package pl.srm.registrationapi.email.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class EmailServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceClient.class);

    private final RestClient restClient;

    public EmailServiceClient(RestClient.Builder restClientBuilder,
                              @Value("${email.service.url:http://localhost:8082}") String emailServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(emailServiceUrl).build();
    }

    public void sendEmail(String to,
                          String templateName,
                          Map<String, String> variables) {
        if (to == null || to.isBlank()) {
            LOGGER.warn("Skipping email - missing recipient address for template {}", templateName);
            return;
        }

        SendEmailRequest request = new SendEmailRequest(
                to,
                templateName,
                variables == null ? Map.of() : variables
        );

        sendEmailRequest(request, templateName);
    }

    private void sendEmailRequest(SendEmailRequest request, String templateName) {
        try {
            restClient.post()
                    .uri("/api/email/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            LOGGER.debug("Sent email using template {}", templateName);
        } catch (Exception exception) {
            LOGGER.error("Failed to call email-service for template {}", templateName, exception);
        }
    }

    private record SendEmailRequest(String to,
                                    String templateName,
                                    Map<String, String> variables) {
    }
}