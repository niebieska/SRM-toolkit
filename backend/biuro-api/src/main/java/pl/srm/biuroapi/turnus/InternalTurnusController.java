package pl.srm.biuroapi.turnus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class InternalTurnusController {
    private final TurnusCatalogue catalogue;
    public InternalTurnusController(TurnusCatalogue catalogue) { this.catalogue = catalogue; }
    @GetMapping("/internal/turnuses")
    public List<TurnusDefinition> list() { return catalogue.list(); }
}
