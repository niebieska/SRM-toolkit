package pl.srm.registrationapi.registration.service.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;

@Service
public class RegistrationNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationNotificationService.class);

    private final EmailServiceClient emailServiceClient;
    private final ObjectMapper objectMapper;

    public RegistrationNotificationService(EmailServiceClient emailServiceClient,
                                           ObjectMapper objectMapper) {
        this.emailServiceClient = emailServiceClient;
        this.objectMapper = objectMapper;
    }

    public void sendParticipantRegistrationConfirmation(String payload,
                                                        RegistrationContext data,
                                                        String registrationCode) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode participant = root.path("person");

            JsonNode recipient = data.isMinor()
                    ? root.path("guardian")
                    : participant;

            String recipientName = fullName(recipient, "Uczestniku");
            String registeredName = fullName(participant, "Nieznany uczestnik");

            sendConfirmation(
                    recipient,
                    recipientName,
                    registeredName,
                    registrationCode,
                    RegistrationType.PARTICIPANT,
                    data.turnusCode()
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }

    public void sendStaffRegistrationConfirmation(String payload,
                                                  RegistrationContext data,
                                                  String registrationCode) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode staff = root.path("person");

            String staffName = fullName(staff, "Nieznana kadra");

            sendConfirmation(
                    staff,
                    staffName,
                    staffName,
                    registrationCode,
                    RegistrationType.STAFF,
                    data.turnusCode()
            );

            if (data.isMinor()) {
                JsonNode guardian = root.path("guardian");
                String guardianName = fullName(guardian, "Opiekunie");

                sendConfirmation(
                        guardian,
                        guardianName,
                        staffName,
                        registrationCode,
                        RegistrationType.STAFF,
                        data.turnusCode()
                );
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }

    private void sendConfirmation(JsonNode recipient,
                                  String recipientName,
                                  String registeredName,
                                  String registrationCode,
                                  RegistrationType registrationType,
                                  String turnusCode) {
        String to = recipient.path("contact").path("email").asText("").trim();

        emailServiceClient.sendRegistrationConfirmation(
                to,
                recipientName,
                registeredName,
                registrationCode,
                registrationType.name(),
                turnusCode
        );
    }

    private String fullName(JsonNode node, String fallback) {
        String firstName = node.path("firstName").asText("").trim();
        String lastName = node.path("lastName").asText("").trim();
        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? fallback : fullName;
    }
}