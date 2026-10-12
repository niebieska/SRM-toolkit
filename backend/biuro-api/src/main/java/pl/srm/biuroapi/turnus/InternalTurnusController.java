package pl.srm.biuroapi.turnus;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.biuroapi.season.SeasonStaffCatalogue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class InternalTurnusController {
    private final TurnusCatalogue catalogue;
    private final SeasonStaffCatalogue seasons;
    private final ObjectMapper mapper;
    public InternalTurnusController(TurnusCatalogue catalogue,
            SeasonStaffCatalogue seasons, ObjectMapper mapper) {
        this.catalogue = catalogue;
        this.seasons = seasons;
        this.mapper = mapper;
    }
    @GetMapping("/internal/turnuses")
    public List<ObjectNode> list() {
        return catalogue.list().stream().map(turnus -> {
            ObjectNode response = mapper.valueToTree(turnus);
            response.remove("staffRequirements");
            response.set("staffCatalogue", mapper.valueToTree(seasons.get(turnus.seasonType())));
            return response;
        }).toList();
    }
}
