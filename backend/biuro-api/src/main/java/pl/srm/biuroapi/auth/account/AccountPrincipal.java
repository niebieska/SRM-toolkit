package pl.srm.biuroapi.auth.account;

import java.util.Set;

public record AccountPrincipal(String id, String username, String displayName,
                               boolean passwordChangeRequired, Set<AccountRole> roles,
                               String sessionId) {
    public AccountPrincipal {
        roles = Set.copyOf(roles);
    }
}
