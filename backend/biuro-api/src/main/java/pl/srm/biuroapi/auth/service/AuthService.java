package pl.srm.biuroapi.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.config.JwtConfig;

@Service
public class AuthService {

    private final String expectedUsername;
    private final String expectedPassword;
    private final JwtConfig jwtConfig;

    public AuthService(@Value("${biuro.auth.username}") String expectedUsername,
                       @Value("${biuro.auth.password}") String expectedPassword,
                       JwtConfig jwtConfig) {
        this.expectedUsername = expectedUsername;
        this.expectedPassword = expectedPassword;
        this.jwtConfig = jwtConfig;
    }

    public String login(String username, String password) {
        if (!expectedUsername.equals(username) || !expectedPassword.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy login lub hasło");
        }
        return jwtConfig.generateToken(username);
    }
}
