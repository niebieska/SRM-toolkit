package pl.srm.registrationapi.registration.service.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.model.RegistrationStatus;
import pl.srm.registrationapi.registration.model.RegistrationType;
import pl.srm.registrationapi.registration.parser.RegistrationContext;

import java.util.Map;

@Service
public class RegistrationNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationNotificationService.class);
    private static final String PARTICIPANT_TEMPLATE = "registration-confirmation-participant";
    private static final String STAFF_TEMPLATE = "registration-confirmation-staff";

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

            sendParticipantConfirmation(
                    recipient,
                    recipientName,
                    registeredName,
                    registrationCode,
                    data.turnusCode()
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare participant registration confirmation email for {}", registrationCode, exception);
        }
    }

    public void sendStaffRegistrationConfirmation(String payload,
                                                  RegistrationContext data,
                                                  String registrationCode) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode staff = root.path("person");

            String staffRole = getStaffRole(root);
            String staffName = fullName(staff, "Nieznana kadra");

            sendStaffConfirmation(
                    staff,
                    staffName,
                    staffName,
                    staffRole,
                    registrationCode,
                    data.turnusCode()
            );

            if (data.isMinor()) {
                JsonNode guardian = root.path("guardian");
                String guardianName = fullName(guardian, "Opiekunie");

                sendStaffConfirmation(
                        guardian,
                        guardianName,
                        staffName,
                        staffRole,
                        registrationCode,
                        data.turnusCode()
                );
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to prepare staff registration confirmation email for {}", registrationCode, exception);
        }
    }

    private void sendParticipantConfirmation(JsonNode recipient,
                                             String recipientName,
                                             String registeredName,
                                             String registrationCode,
                                             String turnusCode) {
        String to = recipient.path("contact").path("email").asText("").trim();

        emailServiceClient.sendEmail(
                to,
                PARTICIPANT_TEMPLATE,
                Map.of(
                        "recipientName", recipientName,
                        "registeredName", registeredName,
                        "registrationCode", registrationCode,
                        "registrationType", RegistrationType.PARTICIPANT.name(),
                        "turnusCode", turnusCode,
                        "status", RegistrationStatus.NEW.name()
                )
        );
    }

    private void sendStaffConfirmation(JsonNode recipient,
                                       String recipientName,
                                       String registeredName,
                                       String staffRole,
                                       String registrationCode,
                                       String turnusCode) {
        String to = recipient.path("contact").path("email").asText("").trim();

        emailServiceClient.sendEmail(
                to,
                STAFF_TEMPLATE,
                Map.of(
                        "recipientName", recipientName,
                        "registeredName", registeredName,
                        "staffRole", staffRole,
                        "registrationCode", registrationCode,
                        "registrationType", RegistrationType.STAFF.name(),
                        "turnusCode", turnusCode,
                        "status", RegistrationStatus.NEW.name()
                )
        );
    }

    private String fullName(JsonNode node, String fallback) {
        String firstName = node.path("firstName").asText("").trim();
        String lastName = node.path("lastName").asText("").trim();
        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? fallback : fullName;
    }

    private String getStaffRole(JsonNode root) {
        String role = root.path("role").asText("").trim();
        String subrole = root.path("subrole").asText("").trim();

        if (role.isBlank()) {
            role = root.path("staff").path("role").asText("").trim();
        }

        if (subrole.isBlank()) {
            subrole = root.path("staff").path("subrole").asText("").trim();
        }

        if (role.isBlank()) {
            return "Kadra";
        }

        if (!subrole.isBlank()) {
            return role + " - " + subrole;
        }

        return role;
    }


}