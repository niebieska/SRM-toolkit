package pl.srm.emailservice.email.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.emailservice.email.service.EmailDispatchService;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailController.class);

    private final EmailDispatchService emailDispatchService;

    public EmailController(EmailDispatchService emailDispatchService) {
        this.emailDispatchService = emailDispatchService;
    }

    @PostMapping("/send")
    public ResponseEntity<SendEmailResponse> sendEmail(@RequestBody SendEmailRequest request) {
        if (request == null || request.to() == null || request.to().isBlank()) {
            return ResponseEntity.badRequest().body(new SendEmailResponse(false, "Missing required field: to"));
        }
        if (request.templateName() == null || request.templateName().isBlank()) {
            return ResponseEntity.badRequest().body(new SendEmailResponse(false, "Missing required field: templateName"));
        }

        try {
            boolean accepted = emailDispatchService.sendEmail(request.to(), request.templateName(), request.variables());
            return ResponseEntity.ok(new SendEmailResponse(accepted, accepted
                    ? "Message accepted by SMTP server." : "Email sending disabled; no message sent."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new SendEmailResponse(false, "Invalid email request or template."));
        } catch (Exception exception) {
            // Provider details belong in restricted server logs, never in the API response.
            LOGGER.error("Email request failed; SMTP delivery may be uncertain (to={}, template={}, registrationCode={})",
                    request.to(), request.templateName(), safeRegistrationCode(request.variables()), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SendEmailResponse(false, "Email sending failed; delivery may be uncertain."));
        }
    }

    private String safeRegistrationCode(Map<String, String> variables) {
        String code = variables == null ? null : variables.get("registrationCode");
        return code != null && code.matches("[A-Za-z0-9_-]{1,150}") ? code : "unknown";
    }
}
