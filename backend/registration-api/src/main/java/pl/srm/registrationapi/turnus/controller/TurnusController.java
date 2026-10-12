package pl.srm.registrationapi.turnus.controller;

import pl.srm.registrationapi.registration.model.RegistrationType;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.registrationapi.turnus.model.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.util.List;

@RestController
@RequestMapping({"/api/turnuses", "/api/turnusy"})
public class TurnusController {

    private final TurnusProvider provider;

    public TurnusController(TurnusProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/staff-options")
    public pl.srm.registrationapi.season.StaffCatalogue staffOptions(
            @org.springframework.web.bind.annotation.RequestParam String turnusCode) {
        if (turnusCode.isBlank()) throw new pl.srm.registrationapi.registration.exception.RegistrationException(
                "INVALID_REQUEST", "Podaj kod turnusu.");
        var catalogue = provider.getByCode(turnusCode).staffCatalogue();
        if (catalogue == null) throw new pl.srm.registrationapi.registration.exception.RegistrationException(
                "TURNUS_CATALOGUE_UNAVAILABLE", "Opcje kadry są chwilowo niedostępne. Spróbuj ponownie później.");
        return catalogue;
    }

    @GetMapping
    public List<Turnus> getTurnuses(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "PARTICIPANT")
            RegistrationType registrationType) {
        return provider.getAvailableTurnuses(registrationType);
    }
}
