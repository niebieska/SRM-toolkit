package pl.srm.biuroapi.auth.management;

import pl.srm.biuroapi.auth.account.AccountRole;
import java.util.Set;

public record AccountView(String id, String username, String displayName, boolean active,
                          boolean passwordChangeRequired, long version, Set<AccountRole> roles,
                          Set<String> turnusCodes) {
    public AccountView { roles = Set.copyOf(roles); turnusCodes = Set.copyOf(turnusCodes); }
}
