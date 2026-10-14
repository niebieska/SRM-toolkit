package pl.srm.biuroapi.turnus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.biuroapi.turnus.TurnusListsService.TurnusLists;

@RestController
public class TurnusListsController {
    private final TurnusListsService lists;
    public TurnusListsController(TurnusListsService lists) { this.lists = lists; }

    @GetMapping("/api/turnuses/{code}/lists")
    public TurnusLists lists(@PathVariable String code) { return lists.get(code); }
}
