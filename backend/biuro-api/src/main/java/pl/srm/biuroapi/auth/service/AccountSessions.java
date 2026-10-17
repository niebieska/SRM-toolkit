package pl.srm.biuroapi.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountCredentials;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.account.AccountAudit;
import pl.srm.biuroapi.auth.api.LoginResponse;
import pl.srm.biuroapi.auth.config.JwtConfig;

import java.time.Clock;
import java.util.UUID;
import java.util.Map;

@Service
public class AccountSessions {
    private final AccountRepository accounts;
    private final JwtConfig jwt;
    private final Clock clock;
    private final AccountAudit audit;

    public AccountSessions(AccountRepository accounts, JwtConfig jwt, Clock clock, AccountAudit audit) {
        this.accounts = accounts; this.jwt = jwt; this.clock = clock; this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeExpired() {
        // Commit range-delete locks before any account lock/session insert.
        // Combining cleanup and inserts can deadlock concurrent MariaDB logins.
        accounts.removeExpiredSessions(clock.instant());
    }

    @Transactional
    public LoginResponse issueVerified(AccountCredentials verified) {
        return issue(verified, AccountAudit.Event.LOGIN_SUCCEEDED);
    }

    @Transactional
    public LoginResponse issueAfterPasswordChange(AccountCredentials verified) {
        return issue(verified, AccountAudit.Event.PASSWORD_CHANGED);
    }

    private LoginResponse issue(AccountCredentials verified, AccountAudit.Event event) {
        // BCrypt verification happened without a lock. Recheck under a short lock
        // so a concurrent password reset or disable cannot issue an outdated session.
        AccountCredentials current = accounts.lockById(verified.id()).orElseThrow(AccountSessions::invalidLogin);
        if (!current.active() || !current.passwordHash().equals(verified.passwordHash()))
            throw invalidLogin();
        var now = clock.instant();
        String sessionId = UUID.randomUUID().toString();
        String token = jwt.generateToken(current.id(), sessionId, now);
        accounts.createSession(sessionId, current.id(), jwt.expiresAt(now));
        var principal = accounts.findSession(current.id(), sessionId, now).orElseThrow(AccountSessions::invalidLogin);
        audit.record(event, principal, current.id(), Map.of());
        return new LoginResponse(token, LoginResponse.User.from(principal));
    }

    private static ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy login lub hasło");
    }
}
