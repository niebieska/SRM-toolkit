package pl.srm.registrationapi.registration.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.registrationapi.registration.dto.response.RegistrationDetailResponse;
import pl.srm.registrationapi.registration.dto.response.RegistrationResponse;
import pl.srm.registrationapi.registration.dto.response.RegistrationSummaryResponse;
import pl.srm.registrationapi.registration.dto.request.RegistrationEditRequest;
import pl.srm.registrationapi.registration.dto.request.StatusUpdateRequest;
import pl.srm.registrationapi.registration.model.Registration;
import pl.srm.registrationapi.registration.service.submission.ParticipantRegistrationService;
import pl.srm.registrationapi.registration.service.management.RegistrationManagementService;
import pl.srm.registrationapi.registration.service.management.RegistrationEditService;
import pl.srm.registrationapi.registration.service.submission.StaffRegistrationService;
import pl.srm.registrationapi.registration.service.submission.ManualStaffRegistrationService;

import java.util.List;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final ParticipantRegistrationService participantService;
    private final StaffRegistrationService staffService;
    private final ManualStaffRegistrationService manualStaffRegistrationService;
    private final RegistrationManagementService managementService;
    private final RegistrationEditService editService;

    public RegistrationController(ParticipantRegistrationService participantService,
                                  StaffRegistrationService staffService,
                                  ManualStaffRegistrationService manualStaffRegistrationService,
                                  RegistrationManagementService managementService,
                                  RegistrationEditService editService) {
        this.participantService = participantService;
        this.staffService = staffService;
        this.manualStaffRegistrationService = manualStaffRegistrationService;
        this.managementService = managementService;
        this.editService = editService;
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

    @PostMapping("/staff/manual")
    public ResponseEntity<RegistrationResponse> registerStaffManually(
            @RequestBody String payload,
            @RequestHeader("X-Actor-Account-Id") String actorAccountId,
            @RequestHeader("X-Actor-Username") String actorUsername) {
        String code = manualStaffRegistrationService.register(payload, actorAccountId, actorUsername);
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
                                                    @Valid @RequestBody StatusUpdateRequest request) {
        return managementService.updateStatus(code, request);
    }

    @PatchMapping("/{code}/data")
    public RegistrationDetailResponse updateRegistrationData(@PathVariable String code,
                                                              @Valid @RequestBody RegistrationEditRequest request) {
        editService.update(code, request);
        return managementService.getDetailByCode(code);
    }

    @GetMapping("/participant")
    public List<RegistrationSummaryResponse> getParticipants() {
        return managementService.getParticipants();
    }

    @GetMapping("/staff")
    public List<RegistrationSummaryResponse> getStaff() {
        return managementService.getStaff();
    }
}
