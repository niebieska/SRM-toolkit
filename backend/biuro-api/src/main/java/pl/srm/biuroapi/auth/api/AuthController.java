package pl.srm.biuroapi.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
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
}
