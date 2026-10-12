package pl.srm.biuroapi.turnus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class InternalTurnusController {
    private final TurnusCatalogue catalogue;
    private final pl.srm.biuroapi.season.SeasonStaffCatalogue seasons;
    private final tools.jackson.databind.ObjectMapper mapper;
    public InternalTurnusController(TurnusCatalogue catalogue,
            pl.srm.biuroapi.season.SeasonStaffCatalogue seasons, tools.jackson.databind.ObjectMapper mapper) {
        this.catalogue = catalogue;
        this.seasons = seasons;
        this.mapper = mapper;
    }
    @GetMapping("/internal/turnuses")
    public List<tools.jackson.databind.node.ObjectNode> list() {
        return catalogue.list().stream().map(turnus -> {
            tools.jackson.databind.node.ObjectNode response = mapper.valueToTree(turnus);
            response.set("staffCatalogue", mapper.valueToTree(seasons.get(turnus.seasonType())));
            return response;
        }).toList();
    }
}
