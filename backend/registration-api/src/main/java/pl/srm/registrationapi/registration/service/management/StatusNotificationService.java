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
    private static final String CANDIDATE_ANIMATOR = "kandydat_na_animatora";

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
            String registeredName = fullName(person, "Uczestnik");

            Map<String, String> variables = new HashMap<>();
            variables.put("registeredName", registeredName);
            variables.put("registrationCode", registration.getRegistrationCode());
            variables.put("status", registration.getStatus());
            variables.put("statusLabel", statusLabel(registration.getStatus()));
            variables.put("registrationType", registration.getRegistrationType());
            variables.put("requiresDeposit", Boolean.toString(requiresDeposit(registration, root)));
            variables.put("isMinor", Boolean.toString(registration.isMinor()));
            variables.put("includeParticipantCard", Boolean.toString(
                    "PARTICIPANT".equals(registration.getRegistrationType())
                            || ("STAFF".equals(registration.getRegistrationType()) && registration.isMinor())));
            variables.put("rejectionReason", registration.getRejectionReason() == null
                    ? ""
                    : registration.getRejectionReason());
            variables.put("transferTitle", registration.getRegistrationCode() + " - " + registeredName);

            if ("STAFF".equals(registration.getRegistrationType()) && registration.isMinor()) {
                String personEmail = email(person);
                String guardianEmail = email(root.path("guardian"));
                sendStatusUpdate(personEmail, variables);
                if (!guardianEmail.equalsIgnoreCase(personEmail)) {
                    sendStatusUpdate(guardianEmail, variables);
                }
            } else {
                JsonNode recipient = registration.isMinor() ? root.path("guardian") : person;
                sendStatusUpdate(email(recipient), variables);
            }
        } catch (Exception exception) {
            LOGGER.error(
                    "Email preparation failed code={} template=status-update error={}",
                    registration.getRegistrationCode(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void sendStatusUpdate(String email, Map<String, String> variables) {
        emailServiceClient.sendEmail(email, STATUS_UPDATE_TEMPLATE, variables);
    }

    private String email(JsonNode recipient) {
        return recipient.path("contact").path("email").asText("").trim();
    }

    private String fullName(JsonNode node, String fallback) {
        String firstName = node.path("firstName").asText("").trim();
        String lastName = node.path("lastName").asText("").trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? fallback : fullName;
    }

    private boolean requiresDeposit(Registration registration, JsonNode root) {
        if ("PARTICIPANT".equals(registration.getRegistrationType())) return true;
        if (!"STAFF".equals(registration.getRegistrationType())) return false;

        String role = root.path("role").asText("").trim();
        String subrole = root.path("subrole").asText("").trim();
        if (role.isBlank()) role = root.path("staff").path("role").asText("").trim();
        if (subrole.isBlank()) subrole = root.path("staff").path("subrole").asText("").trim();

        return CANDIDATE_ANIMATOR.equals(role) || CANDIDATE_ANIMATOR.equals(subrole);
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
