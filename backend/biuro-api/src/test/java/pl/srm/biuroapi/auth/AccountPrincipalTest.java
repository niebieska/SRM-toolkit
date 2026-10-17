package pl.srm.biuroapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pl.srm.biuroapi.auth.account.AccountPrincipal;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountPrincipalTest {
    @Test void springAuthenticationNameIsUsernameInsteadOfRecordOrSession() {
        var principal = new AccountPrincipal("account-id", "anna.kowalska", "Anna", false,
                Set.of(AccountRole.OPERATOR), "private-session-id");
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        assertEquals("anna.kowalska", principal.getName());
        assertEquals("anna.kowalska", authentication.getName());
    }
}
