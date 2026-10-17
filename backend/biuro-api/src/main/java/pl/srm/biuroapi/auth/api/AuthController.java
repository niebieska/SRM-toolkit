package pl.srm.biuroapi.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.service.AuthService;
import pl.srm.biuroapi.common.api.ApiError;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public LoginResponse.User me(@AuthenticationPrincipal AccountPrincipal principal) {
        return LoginResponse.User.from(principal);
    }

    @PostMapping("/password")
    public LoginResponse changePassword(@AuthenticationPrincipal AccountPrincipal principal,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(principal, request.currentPassword(), request.newPassword());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AccountPrincipal principal) {
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ApiError> databaseUnavailable(Exception ex) {
        log.error("Biuro account operation unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(503, "Usługa jest chwilowo niedostępna. Spróbuj ponownie później."));
    }
}
