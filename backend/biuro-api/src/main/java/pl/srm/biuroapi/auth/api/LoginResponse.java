package pl.srm.biuroapi.auth.api;

import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.util.Set;

public record LoginResponse(String token, User user) {
    public record User(String username, String displayName, boolean passwordChangeRequired, Set<AccountRole> roles) {
        public static User from(AccountPrincipal principal) {
            return new User(principal.username(), principal.displayName(), principal.passwordChangeRequired(), principal.roles());
        }
    }
}
