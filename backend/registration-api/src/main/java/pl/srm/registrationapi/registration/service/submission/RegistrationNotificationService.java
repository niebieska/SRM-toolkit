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
import pl.srm.registrationapi.season.StaffCatalogue;
import pl.srm.registrationapi.turnus.model.Turnus;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class RegistrationNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationNotificationService.class);
    private static final String PARTICIPANT_TEMPLATE = "registration-confirmation-participant";
    private static final String STAFF_TEMPLATE = "registration-confirmation-staff";
    private static final DateTimeFormatter EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EmailServiceClient emailServiceClient;
    private final ObjectMapper objectMapper;

    public RegistrationNotificationService(EmailServiceClient emailServiceClient,
                                           ObjectMapper objectMapper) {
        this.emailServiceClient = emailServiceClient;
        this.objectMapper = objectMapper;
    }

    public void sendParticipantRegistrationConfirmation(String payload,
                                                        RegistrationContext data,
                                                        String registrationCode,
                                                        Turnus turnus) {
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
                    turnus
            );
        } catch (Exception exception) {
            LOGGER.error("Email preparation failed code={} template=registration-confirmation-participant error={}",
                    registrationCode, exception.getClass().getSimpleName());
        }
    }

    public void sendStaffRegistrationConfirmation(String payload,
                                                  RegistrationContext data,
                                                  String registrationCode,
                                                  Turnus turnus) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode staff = root.path("person");

            String staffRole = getStaffRole(root, turnus.staffCatalogue());
            String staffName = fullName(staff, "Nieznana kadra");

            sendStaffConfirmation(
                    staff,
                    staffName,
                    staffName,
                    staffRole,
                    registrationCode,
                    turnus
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
                        turnus
                );
            }
        } catch (Exception exception) {
            LOGGER.error("Email preparation failed code={} template=registration-confirmation-staff error={}",
                    registrationCode, exception.getClass().getSimpleName());
        }
    }

    private void sendParticipantConfirmation(JsonNode recipient,
                                             String recipientName,
                                             String registeredName,
                                             String registrationCode,
                                             Turnus turnus) {
        String to = recipient.path("contact").path("email").asText("").trim();
        Map<String, String> variables = new java.util.HashMap<>();

        variables.put("recipientName", recipientName);
        variables.put("registeredName", registeredName);
        variables.put("registrationCode", registrationCode);
        variables.put("registrationType", RegistrationType.PARTICIPANT.name());
        variables.put("status", RegistrationStatus.NEW.name());
        variables.putAll(turnusVariables(turnus));

        emailServiceClient.sendEmail(
                to,
                PARTICIPANT_TEMPLATE,
                variables
        );
    }

    private void sendStaffConfirmation(JsonNode recipient,
                                       String recipientName,
                                       String registeredName,
                                       String staffRole,
                                       String registrationCode,
                                       Turnus turnus) {
        String to = recipient.path("contact").path("email").asText("").trim();
        Map<String, String> variables = new java.util.HashMap<>();

        variables.put("recipientName", recipientName);
        variables.put("registeredName", registeredName);
        variables.put("registrationCode", registrationCode);
        variables.put("staffRole", staffRole);
        variables.put("registrationType", RegistrationType.STAFF.name());
        variables.put("status", RegistrationStatus.NEW.name());
        variables.putAll(turnusVariables(turnus));

        emailServiceClient.sendEmail(
                to,
                STAFF_TEMPLATE,
                variables
        );
    }

    private String fullName(JsonNode node, String fallback) {
        String firstName = node.path("firstName").asText("").trim();
        String lastName = node.path("lastName").asText("").trim();
        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? fallback : fullName;
    }

    private String getStaffRole(JsonNode root, StaffCatalogue catalogue) {
        String role = root.path("role").asText("").trim();
        String subrole = root.path("subrole").asText("").trim();

        if (role.isBlank()) {
            role = root.path("staff").path("role").asText("").trim();
        }

        if (subrole.isBlank()) {
            subrole = root.path("staff").path("subrole").asText("").trim();
        }

        if (role.isBlank()) {
            return subrole.isBlank() ? "Kadra" : labelForUnscopedSubrole(catalogue, subrole);
        }

        String roleLabel = roleLabel(catalogue, role);
        if (subrole.isBlank()) return roleLabel;

        String subroleLabel = subroleLabel(catalogue, role, subrole);
        return roleLabel + " - " + subroleLabel;
    }

    private String roleLabel(StaffCatalogue catalogue, String code) {
        if (catalogue != null) {
            for (StaffCatalogue.Role role : catalogue.roles()) {
                if (role.value().equals(code)) return role.label();
            }
        }
        return humanizeCode(code);
    }

    private String subroleLabel(StaffCatalogue catalogue, String roleCode, String subroleCode) {
        if (catalogue != null) {
            for (StaffCatalogue.Role role : catalogue.roles()) {
                if (role.value().equals(roleCode)) {
                    for (StaffCatalogue.Function subrole : role.subroles()) {
                        if (subrole.value().equals(subroleCode)) return subrole.label();
                    }
                    break;
                }
            }
        }
        return humanizeCode(subroleCode);
    }

    private String labelForUnscopedSubrole(StaffCatalogue catalogue, String code) {
        if (catalogue != null) {
            String matchingLabel = null;
            for (StaffCatalogue.Role role : catalogue.roles()) {
                for (StaffCatalogue.Function subrole : role.subroles()) {
                    if (subrole.value().equals(code)) {
                        if (matchingLabel != null && !matchingLabel.equals(subrole.label())) {
                            return humanizeCode(code);
                        }
                        matchingLabel = subrole.label();
                    }
                }
            }
            if (matchingLabel != null) return matchingLabel;
        }
        return humanizeCode(code);
    }

    private String humanizeCode(String code) {
        String label = code.replace('_', ' ');
        if (label.isBlank()) return "Kadra";
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private Map<String, String> turnusVariables(Turnus turnus) {
        return Map.of(
                "turnusCode", turnus.turnusCode(),
                "turnusName", turnus.turnusName(),
                "turnusStartDate", turnus.startDate().format(EMAIL_DATE_FORMAT),
                "turnusEndDate", turnus.endDate().format(EMAIL_DATE_FORMAT),
                "turnusDescription", turnus.turnusDescription() == null ? "" : turnus.turnusDescription()
        );
    }

}
