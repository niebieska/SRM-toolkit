package pl.srm.registrationapi.registration.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.service.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.StaffRegistrationService;

import java.util.List;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final ParticipantRegistrationService participantService;
    private final StaffRegistrationService staffService;

    public RegistrationController(ParticipantRegistrationService participantService,
                                  StaffRegistrationService staffService) {
        this.participantService = participantService;
        this.staffService = staffService;
    }

    @PostMapping("/participant")
    public ResponseEntity<RegistrationResponse> registerParticipant(@RequestBody String payload) {
        String code = participantService.register(payload);
        return ResponseEntity.ok(new RegistrationResponse(code));
    }

    @PostMapping("/staff")
    public ResponseEntity<RegistrationResponse> registerStaff(@RequestBody String payload) {
        String code = staffService.register(payload);
        return ResponseEntity.ok(new RegistrationResponse(code));
    }

    @GetMapping("/participant")
    public List<Registration> getParticipants() {
        return participantService.getAll();
    }

    @GetMapping("/staff")
    public List<Registration> getStaff() {
        return staffService.getAll();
    }
}
