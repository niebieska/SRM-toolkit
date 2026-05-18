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
            emailDispatchService.sendEmail(request.to(), request.templateName(), request.variables());
            return ResponseEntity.ok(new SendEmailResponse(true, "Email sent."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new SendEmailResponse(false, exception.getMessage()));
        } catch (Exception exception) {
            LOGGER.error("Failed to send email", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SendEmailResponse(false, "Failed to send email."));
        }
    }
}
