package pl.srm.biuroapi.turnus;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import java.util.List;

@RestController
@RequestMapping("/api/viewer/turnuses")
public class ViewerTurnusController {
    private final TurnusListAccess access;
    public ViewerTurnusController(TurnusListAccess access) { this.access = access; }
    @GetMapping public List<TurnusListAccess.Summary> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return access.available(principal);
    }
    @GetMapping("/{code}/lists") public TurnusListAccess.ViewerLists lists(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable String code) { return access.lists(principal, code); }
}
