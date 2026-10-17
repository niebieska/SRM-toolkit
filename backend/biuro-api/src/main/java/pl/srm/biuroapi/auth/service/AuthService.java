package pl.srm.biuroapi.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountCredentials;
import pl.srm.biuroapi.auth.account.AccountPolicy;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.api.LoginResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AccountSessions sessions;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AccountRepository accounts, PasswordEncoder encoder, AccountSessions sessions, Clock clock) {
        this.accounts = accounts; this.encoder = encoder; this.sessions = sessions; this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(String username, String password) {
        Optional<AccountCredentials> stored = accounts.findByUsername(AccountPolicy.normalizeUsername(username));
        String hash = stored.map(AccountCredentials::passwordHash).orElse(dummyHash);
        // BCrypt has a 72-byte limit. Oversized input is rejected without invoking it.
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(password, hash) || stored.isEmpty() || !stored.get().active())
            throw invalidLogin();
        sessions.removeExpired();
        return sessions.issueVerified(stored.get());
    }

    @Transactional(readOnly = true)
    public Optional<AccountPrincipal> authenticate(String accountId, String sessionId) {
        return accounts.findSession(accountId, sessionId, clock.instant());
    }

    @Transactional
    public LoginResponse changePassword(AccountPrincipal principal, String currentPassword, String newPassword) {
        AccountPolicy.password(newPassword);
        AccountCredentials stored = accounts.lockByUsername(principal.username()).orElseThrow(AuthService::invalidLogin);
        // Recheck after obtaining the account lock, so a concurrent reset/logout cannot be bypassed.
        if (accounts.findSession(principal.id(), principal.sessionId(), clock.instant()).isEmpty())
            throw invalidLogin();
        if (currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(currentPassword, stored.passwordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Obecne hasło jest nieprawidłowe.");
        if (encoder.matches(newPassword, stored.passwordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nowe hasło musi różnić się od obecnego.");
        String newHash = encoder.encode(newPassword);
        accounts.changePassword(principal.id(), newHash);
        return sessions.issueVerified(new AccountCredentials(principal.id(), newHash, true));
    }

    @Transactional
    public void logout(AccountPrincipal principal) {
        accounts.revokeSession(principal.id(), principal.sessionId());
    }

    private static ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy login lub hasło");
    }
}
