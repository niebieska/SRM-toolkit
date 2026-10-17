package pl.srm.biuroapi.auth.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AccountPolicy {
    private AccountPolicy() {}

    public static String normalizeUsername(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public static String username(String value) {
        String username = normalizeUsername(value);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,63}"))
            throw new IllegalArgumentException("Login must have 3–64 ASCII letters, digits, dots, underscores or hyphens");
        return username;
    }

    public static void password(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hasło musi mieć co najmniej 12 znaków i najwyżej 72 bajty UTF-8.");
    }
}
