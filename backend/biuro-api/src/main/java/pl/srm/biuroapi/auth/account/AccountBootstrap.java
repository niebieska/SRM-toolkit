package pl.srm.biuroapi.auth.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

@Component
public class AccountBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final String username;
    private final String displayName;
    private final String password;
    private final AccountAudit audit;

    public AccountBootstrap(AccountRepository accounts, PasswordEncoder encoder, Clock clock,
            PlatformTransactionManager manager, AccountAudit audit,
            @Value("${biuro.bootstrap.enabled:false}") boolean enabled,
            @Value("${biuro.bootstrap.username:}") String username,
            @Value("${biuro.bootstrap.display-name:}") String displayName,
            @Value("${biuro.bootstrap.password:}") String password) {
        this.accounts = accounts; this.encoder = encoder; this.clock = clock;
        this.audit = audit;
        this.transaction = new TransactionTemplate(manager);
        this.enabled = enabled; this.username = username; this.displayName = displayName; this.password = password;
    }

    @Override public void run(ApplicationArguments args) {
        if (!enabled) return;
        transaction.executeWithoutResult(status -> {
            if (!accounts.lockSetupAndNeedsAccount()) return;
            String login = AccountPolicy.username(username);
            AccountPolicy.password(password);
            if (displayName.isBlank() || displayName.length() > 150)
                throw new IllegalArgumentException("Bootstrap display name is required (maximum 150 characters)");
            String id = UUID.randomUUID().toString();
            accounts.create(id, login, displayName.strip(), encoder.encode(password),
                    Set.of(AccountRole.SYS_ADMIN), clock.instant());
            accounts.completeSetup();
            audit.record(AccountAudit.Event.BOOTSTRAP_ADMIN, null, id, Map.of("username", login));
            log.info("Initial SYS_ADMIN account created; password change is required. Disable bootstrap configuration.");
        });
    }
}
