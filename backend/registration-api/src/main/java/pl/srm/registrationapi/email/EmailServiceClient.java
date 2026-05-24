package pl.srm.registrationapi.email;

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
    private final String organizerEmail;

    public EmailServiceClient(RestClient.Builder restClientBuilder,
                              @Value("${email.service.url:http://localhost:8082}") String emailServiceUrl,
                              @Value("${email.organizer.email:}") String organizerEmail) {
        this.restClient = restClientBuilder.baseUrl(emailServiceUrl).build();
        this.organizerEmail = organizerEmail;
    }

    public void sendRegistrationConfirmation(String to,
                                             String recipientName,
                                             String registrationCode,
                                             String registrationType,
                                             String turnusCode) {
        if (to == null || to.isBlank()) {
            LOGGER.warn("Skipping registration email - missing recipient address for code {}", registrationCode);
            return;
        }

        SendEmailRequest request = new SendEmailRequest(
                to,
                "registration-confirmation",
                Map.of(
                        "registrationCode", registrationCode,
                        "registrationType", registrationType,
                        "turnusCode", turnusCode,
                        "recipientName", recipientName,
                        "status", "NEW"
                )
        );

        sendEmailRequest(request, registrationCode, "registration confirmation");
    }

    public void sendOrganizerNewRegistrationNotification(String registrationCode,
                                                         String registrationType,
                                                         String turnusCode,
                                                         String participantName) {
        if (organizerEmail == null || organizerEmail.isBlank()) {
            LOGGER.warn("Skipping organizer notification - missing organizer email for code {}", registrationCode);
            return;
        }

        SendEmailRequest request = new SendEmailRequest(
                organizerEmail,
                "organizer-new-registration",
                Map.of(
                        "registrationCode", registrationCode,
                        "registrationType", registrationType,
                        "turnusCode", turnusCode,
                        "participantName", participantName == null || participantName.isBlank() ? "Nieznany" : participantName
                )
        );

        sendEmailRequest(request, registrationCode, "organizer notification");
    }

    private void sendEmailRequest(SendEmailRequest request, String registrationCode, String emailType) {
        try {
            restClient.post()
                    .uri("/api/email/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            LOGGER.error("Failed to call email-service for {} of registration {}", emailType, registrationCode, exception);
        }
    }

    private record SendEmailRequest(String to,
                                    String templateName,
                                    Map<String, String> variables) {
    }
}
