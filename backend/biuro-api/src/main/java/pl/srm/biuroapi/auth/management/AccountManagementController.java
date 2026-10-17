package pl.srm.biuroapi.auth.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.srm.biuroapi.auth.account.AccountAudit;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import pl.srm.biuroapi.common.api.ApiError;
import pl.srm.biuroapi.turnus.TurnusListAccess;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/accounts")
public class AccountManagementController {
    public record CreateRequest(@NotBlank @Size(max=64) String username, @NotBlank @Size(max=150) String displayName,
            @NotBlank @Size(min=12, max=72) String password, @NotEmpty Set<@NotNull AccountRole> roles,
            @NotNull Set<@NotBlank @Size(max=50) String> turnusCodes) {
        @Override public String toString() { return "CreateRequest[redacted]"; }
    }
    public record UpdateRequest(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=150) String displayName,
            @NotNull Boolean active, @NotEmpty Set<@NotNull AccountRole> roles,
            @NotNull Set<@NotBlank @Size(max=50) String> turnusCodes) {}
    public record ResetRequest(@NotNull @PositiveOrZero Long version, @NotBlank @Size(min=12, max=72) String password) {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }
    private static final Logger log = LoggerFactory.getLogger(AccountManagementController.class);
    private final AccountManagementService management;
    private final TurnusListAccess access;
    public AccountManagementController(AccountManagementService management, TurnusListAccess access) {
        this.management = management; this.access = access;
    }
    @GetMapping public List<AccountView> list(@AuthenticationPrincipal AccountPrincipal actor) { return management.list(actor); }
    @GetMapping("/turnus-options") public List<TurnusListAccess.Summary> turnuses() { return access.allSummaries(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody CreateRequest request) {
        return management.create(actor, request.username(), request.displayName(), request.password(), request.roles(), request.turnusCodes());
    }
    @PatchMapping("/{id}") public AccountView update(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        return management.update(actor, id, request.version(), request.displayName(), request.active(), request.roles(), request.turnusCodes());
    }
    @PostMapping("/{id}/password") public AccountView reset(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String id, @Valid @RequestBody ResetRequest request) {
        return management.resetPassword(actor, id, request.version(), request.password());
    }
    @GetMapping("/{id}/audit") public List<AccountAudit.Entry> history(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String id, @RequestParam(defaultValue="100") int limit) { return management.history(actor, id, limit); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidParameter() { return ResponseEntity.badRequest().body(new ApiError(400, "Nieprawidłowy parametr żądania.")); }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ApiError> unavailable(Exception ex) {
        log.error("Biuro account administration unavailable", ex);
        return ResponseEntity.status(503).body(new ApiError(503, "Usługa jest chwilowo niedostępna. Spróbuj ponownie później."));
    }
}
