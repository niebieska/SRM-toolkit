package pl.srm.registrationapi.registration.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.srm.registrationapi.registration.domain.Registration;
import pl.srm.registrationapi.registration.service.RegistrationService;

import java.util.List;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> register(@RequestBody String payload) {

        service.register(payload);

        return ResponseEntity.status(201).build();
    }


    @GetMapping("/internal")
    public List<Registration> getAll() {
        return service.getAll();
    }

}