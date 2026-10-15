package pl.srm.registrationapi.registration.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.registrationapi.registration.dto.response.TurnusListPerson;
import pl.srm.registrationapi.registration.service.management.TurnusListService;
import pl.srm.registrationapi.registration.exception.RegistrationException;

@RestController
public class TurnusListController {
    private final TurnusListService lists;
    public TurnusListController(TurnusListService lists) { this.lists = lists; }

    @GetMapping("/api/registrations/turnus-list")
    public List<TurnusListPerson> accepted(@RequestParam String turnusCode) {
        if (turnusCode.isBlank()) throw new RegistrationException("INVALID_REQUEST", "Podaj kod turnusu.");
        return lists.accepted(turnusCode);
    }
}
