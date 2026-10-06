package pl.srm.registrationapi.registration.service.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.srm.registrationapi.email.client.EmailServiceClient;
import pl.srm.registrationapi.registration.model.Registration;

import java.util.HashMap;
import java.util.Map;

@Service
public class StatusNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusNotificationService.class);
    private static final String STATUS_UPDATE_TEMPLATE = "status-update";

    private final EmailServiceClient emailServiceClient;
    private final ObjectMapper objectMapper;

    public StatusNotificationService(EmailServiceClient emailServiceClient,
                                     ObjectMapper objectMapper) {
        this.emailServiceClient = emailServiceClient;
        this.objectMapper = objectMapper;
    }

    public void sendStatusUpdate(Registration registration) {
        try {
            JsonNode root = objectMapper.readTree(registration.getPayload());
            JsonNode person = root.path("person");
            JsonNode recipient = registration.isMinor()
                    ? root.path("guardian")
                    : person;

            String recipientName = fullName(recipient, "Uczestniku");
            String registeredName = fullName(person, "Uczestnik");
            String email = recipient.path("contact").path("email").asText("").trim();

            Map<String, String> variables = new HashMap<>();
            variables.put("recipientName", recipientName);
            variables.put("registeredName", registeredName);
            variables.put("registrationCode", registration.getRegistrationCode());
            variables.put("status", registration.getStatus());
            variables.put("statusLabel", statusLabel(registration.getStatus()));
            variables.put("rejectionReason", registration.getRejectionReason() == null
                    ? ""
                    : registration.getRejectionReason());
            variables.put("transferTitle", registration.getRegistrationCode() + " - " + registeredName);

            emailServiceClient.sendEmail(email, STATUS_UPDATE_TEMPLATE, variables);
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to prepare status update email for {}",
                    registration.getRegistrationCode(),
                    exception
            );
        }
    }

    private String fullName(JsonNode node, String fallback) {
        String firstName = node.path("firstName").asText("").trim();
        String lastName = node.path("lastName").asText("").trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? fallback : fullName;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "ACCEPTED" -> "Zaakceptowane";
            case "WAITLIST" -> "Lista rezerwowa";
            case "REJECTED" -> "Odrzucone";
            default -> status;
        };
    }
}
