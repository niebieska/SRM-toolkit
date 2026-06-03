package pl.srm.registrationapi.registration.service.submission;

import pl.srm.registrationapi.registration.model.Registration;

import java.util.List;

public interface RegistrationService {
    String register(String payload);
    List<Registration> getAll();
}
