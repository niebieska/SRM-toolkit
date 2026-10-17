package pl.srm.biuroapi.auth.management;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountAudit;
import pl.srm.biuroapi.auth.account.AccountPolicy;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRepository;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountManagementService {
    private final AccountRepository accounts;
    private final AccountManagementRepository management;
    private final PasswordEncoder encoder;
    private final AccountAudit audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AccountManagementService(AccountRepository accounts, AccountManagementRepository management,
            PasswordEncoder encoder, AccountAudit audit, Clock clock, PlatformTransactionManager manager) {
        this.accounts = accounts; this.management = management; this.encoder = encoder;
        this.audit = audit; this.clock = clock; this.transaction = new TransactionTemplate(manager);
    }

    @Transactional(readOnly = true)
    public List<AccountView> list(AccountPrincipal actor) { requireAdmin(actor); return management.list(); }

    @Transactional(readOnly = true)
    public List<AccountAudit.Entry> history(AccountPrincipal actor, String id, int limit) {
        requireAdmin(actor);
        management.get(id, false);
        if (limit < 1 || limit > 200) throw invalid("Limit historii musi wynosić od 1 do 200.");
        return audit.forAccount(id, limit);
    }

    public AccountView create(AccountPrincipal actor, String username, String displayName, String password,
                              Set<AccountRole> roles, Set<String> codes) {
        String login;
        try { login = AccountPolicy.username(username); }
        catch (IllegalArgumentException ex) { throw invalid("Login musi mieć 3–64 litery ASCII, cyfry lub znaki . _ -."); }
        validate(displayName, roles, codes);
        AccountPolicy.password(password);
        // BCrypt is deliberately outside the administrative lock/transaction.
        String hash = encoder.encode(password);
        return transaction.execute(status -> {
            management.lockAdministration();
            AccountPrincipal currentActor = requireAdmin(actor);
            validateTurnuses(codes);
            String id = UUID.randomUUID().toString();
            try { accounts.create(id, login, displayName.strip(), hash, roles, clock.instant()); }
            catch (DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Login jest już używany."); }
            management.replaceAssignments(id, codes);
            var result = management.get(id, false);
            audit.record(AccountAudit.Event.ACCOUNT_CREATED, currentActor, id, Map.of("account", result));
            return result;
        });
    }

    public AccountView update(AccountPrincipal actor, String id, long version, String displayName,
                              boolean active, Set<AccountRole> roles, Set<String> codes) {
        validate(displayName, roles, codes);
        return transaction.execute(status -> {
            management.lockAdministration();
            AccountPrincipal currentActor = requireAdmin(actor);
            AccountView before = management.get(id, true);
            checkVersion(before, version);
            validateTurnuses(codes);
            boolean removingActiveAdmin = before.active() && before.roles().contains(AccountRole.SYS_ADMIN)
                    && (!active || !roles.contains(AccountRole.SYS_ADMIN));
            if (removingActiveAdmin && management.activeAdministrators() <= 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Nie można wyłączyć ani odebrać roli ostatniemu aktywnemu administratorowi.");
            management.update(id, displayName.strip(), active, roles, codes);
            if (!active) accounts.revokeAllSessions(id);
            var result = management.get(id, false);
            audit.record(AccountAudit.Event.ACCOUNT_UPDATED, currentActor, id, Map.of("before", before, "after", result));
            return result;
        });
    }

    public AccountView resetPassword(AccountPrincipal actor, String id, long version, String password) {
        AccountPolicy.password(password);
        String hash = encoder.encode(password);
        return transaction.execute(status -> {
            management.lockAdministration();
            AccountPrincipal currentActor = requireAdmin(actor);
            AccountView before = management.get(id, true);
            checkVersion(before, version);
            management.resetPassword(id, hash);
            accounts.revokeAllSessions(id);
            var result = management.get(id, false);
            audit.record(AccountAudit.Event.PASSWORD_RESET, currentActor, id,
                    Map.of("previousVersion", before.version(), "version", result.version()));
            return result;
        });
    }

    private AccountPrincipal requireAdmin(AccountPrincipal supplied) {
        AccountPrincipal current = accounts.findSession(supplied.id(), supplied.sessionId(), clock.instant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wymagane jest ponowne logowanie."));
        if (current.passwordChangeRequired() || !current.roles().contains(AccountRole.SYS_ADMIN))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień do zarządzania kontami.");
        return current;
    }
    private void validate(String name, Set<AccountRole> roles, Set<String> codes) {
        if (name == null || name.isBlank() || name.length() > 150) throw invalid("Podaj nazwę użytkownika (do 150 znaków).");
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(role -> role == null)) throw invalid("Wybierz co najmniej jedną rolę.");
        if (codes == null || codes.stream().anyMatch(code -> code == null || code.isBlank() || code.length() > 50))
            throw invalid("Nieprawidłowe przypisania turnusów.");
    }
    private void validateTurnuses(Set<String> codes) {
        for (String code : codes) if (!management.turnusExists(code)) throw invalid("Nie znaleziono przypisanego turnusu: " + code);
    }
    private void checkVersion(AccountView current, long expected) {
        if (current.version() != expected) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Konto zostało zmienione. Odśwież dane przed zapisem.");
    }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
