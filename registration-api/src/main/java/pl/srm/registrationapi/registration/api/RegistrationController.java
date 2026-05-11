package pl.srm.registrationapi.registration.api;

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
    public void registerParticipant(@RequestBody String payload) {
        participantService.register(payload);
    }

    @PostMapping("/staff")
    public void registerStaff(@RequestBody String payload) {
        staffService.register(payload);
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