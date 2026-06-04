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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailServiceClient emailServiceClient;

    public RegistrationNotificationService(EmailServiceClient emailServiceClient) {
        this.emailServiceClient = emailServiceClient;
    }


   public void sendParticipantRegistrationConfirmation(String payload,
                                              RegistrationContext data,
                                              String registrationCode) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode recipient = data.isMinor() ? root.path("guardian") : root.path("person");
            JsonNode participant = root.path("person");

            String to = recipient.path("contact").path("email").asText("").trim();
            String firstName = recipient.path("firstName").asText("").trim();
            String lastName = recipient.path("lastName").asText("").trim();

            String recipientName = (firstName + " " + lastName).trim();
            if (recipientName.isBlank()) {
                recipientName = "Uczestniku";
            }

            String participantName = participantFullName(participant);

            emailServiceClient.sendRegistrationConfirmation(
                    to,
                    recipientName,
                    registrationCode,
                    RegistrationType.PARTICIPANT.name(),
                    data.turnusCode()
            );

            emailServiceClient.sendOrganizerNewRegistrationNotification(
                    registrationCode,
                    RegistrationType.PARTICIPANT.name(),
                    data.turnusCode(),
                    participantName
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }

    private String participantFullName(JsonNode participant) {
        String firstName = participant.path("firstName").asText("").trim();
        String lastName = participant.path("lastName").asText("").trim();
        String participantName = (firstName + " " + lastName).trim();

        return participantName.isBlank() ? "Nieznany uczestnik" : participantName;
    }

   public void sendStaffRegistrationConfirmation(String payload, String turnusCode, String registrationCode) {
        try {
            JsonNode person = objectMapper.readTree(payload).path("person");
            String to = person.path("contact").path("email").asText("").trim();
            String firstName = person.path("firstName").asText("").trim();
            String lastName = person.path("lastName").asText("").trim();

            String staffName = (firstName + " " + lastName).trim();
            String recipientName = staffName;

            if (staffName.isBlank()) {
                recipientName = "Kadro";
                staffName = "Nieznana kadra";
            }

            emailServiceClient.sendRegistrationConfirmation(
                    to,
                    recipientName,
                    registrationCode,
                    RegistrationType.STAFF.name(),
                    turnusCode
            );

            emailServiceClient.sendOrganizerNewRegistrationNotification(
                    registrationCode,
                    RegistrationType.STAFF.name(),
                    turnusCode,
                    staffName
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare registration confirmation email for {}", registrationCode, exception);
        }
    }

}
