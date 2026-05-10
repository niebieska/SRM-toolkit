package pl.srm.registrationapi.turnus.api;

import org.springframework.web.bind.annotation.*;
import pl.srm.registrationapi.turnus.domain.Turnus;
import pl.srm.registrationapi.turnus.service.TurnusProvider;

import java.util.List;

@RestController
@RequestMapping("/api/turnusy")
public class TurnusController {

    private final TurnusProvider provider;

    public TurnusController(TurnusProvider provider) {
        this.provider = provider;
    }

    @GetMapping
    public List<Turnus> getTurnuses() {
        return provider.getAvailableTurnuses();
    }
}