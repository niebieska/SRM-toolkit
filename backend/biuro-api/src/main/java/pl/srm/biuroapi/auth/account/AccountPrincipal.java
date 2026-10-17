package pl.srm.biuroapi.auth.account;

import org.springframework.security.core.AuthenticatedPrincipal;
import java.util.Set;

public record AccountPrincipal(String id, String username, String displayName,
                               boolean passwordChangeRequired, Set<AccountRole> roles,
                               String sessionId) implements AuthenticatedPrincipal {
    public AccountPrincipal {
        roles = Set.copyOf(roles);
    }

    @Override public String getName() { return username; }
}
