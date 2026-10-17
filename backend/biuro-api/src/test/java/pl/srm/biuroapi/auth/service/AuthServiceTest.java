package pl.srm.biuroapi.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountCredentials;
import pl.srm.biuroapi.auth.account.AccountAudit;
import pl.srm.biuroapi.auth.account.AccountRepository;
import java.time.Clock;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountSessions sessions = mock(AccountSessions.class);
    private final AuthService service = new AuthService(accounts, encoder, sessions, Clock.systemUTC(), mock(AccountAudit.class));

    @Test void unknownWrongAndDisabledAccountsHaveSameResponseAndNoSession() {
        when(accounts.findByUsername("unknown")).thenReturn(Optional.empty());
        when(accounts.findByUsername("disabled")).thenReturn(Optional.of(
                new AccountCredentials("disabled-id", encoder.encode("correct-password"), false)));
        when(accounts.findByUsername("operator")).thenReturn(Optional.of(
                new AccountCredentials("operator-id", encoder.encode("correct-password"), true)));
        for (String username : new String[]{"unknown", "disabled", "operator"}) {
            var ex = assertThrows(ResponseStatusException.class, () -> service.login(username, "wrong-password"));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
            assertEquals("Nieprawidłowy login lub hasło", ex.getReason());
        }
        assertThrows(ResponseStatusException.class, () -> service.login("disabled", "correct-password"));
        verify(accounts, never()).createSession(any(), any(), any());
    }

    @Test void oversizedUtf8PasswordIsRejectedInsteadOfServerError() {
        when(accounts.findByUsername("operator")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.login("operator", "ą".repeat(40)));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
