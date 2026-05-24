package pl.srm.registrationapi.registration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.registrationapi.registration.dto.response.RegistrationDetailResponse;
import pl.srm.registrationapi.registration.dto.response.RegistrationResponse;
import pl.srm.registrationapi.registration.dto.response.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.service.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.StaffRegistrationService;

import java.util.List;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final ParticipantRegistrationService participantService;
    private final StaffRegistrationService staffService;
    private final RegistrationManagementService managementService;

    public RegistrationController(ParticipantRegistrationService participantService,
                                  StaffRegistrationService staffService,
                                  RegistrationManagementService managementService) {
        this.participantService = participantService;
        this.staffService = staffService;
        this.managementService = managementService;
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

    @GetMapping("/{code}")
    public RegistrationDetailResponse getRegistration(@PathVariable String code) {
        return managementService.getDetailByCode(code);
    }

    @GetMapping
    public List<RegistrationSummaryResponse> getAllRegistrations() {
        return managementService.getAll();
    }

    @PatchMapping("/{code}/status")
    public RegistrationSummaryResponse updateStatus(@PathVariable String code,
                                                    @RequestBody StatusUpdateRequest request) {
        return managementService.updateStatus(code, request);
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
