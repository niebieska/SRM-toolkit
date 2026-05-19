package pl.srm.biuroapi.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.config.JwtConfig;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private final JwtConfig jwtConfig = new JwtConfig("this-is-a-very-long-secret-key-for-tests-123", 8);
    private final AuthService authService = new AuthService("biuro", "secret", jwtConfig);

    @Test
    void shouldGenerateTokenForValidCredentials() {
        String token = authService.login("biuro", "secret");

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("biuro", jwtConfig.extractUsername(token));
    }

    @Test
    void shouldRejectInvalidCredentials() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login("biuro", "wrong")
        );

        assertEquals(HttpStatus.UNAUTHORIZED.value(), exception.getStatusCode().value());
    }
}
