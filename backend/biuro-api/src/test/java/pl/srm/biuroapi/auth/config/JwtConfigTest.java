package pl.srm.biuroapi.auth.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    static final String SECRET = "test-only-signing-key-for-unit-tests-at-least-32-bytes";
    final JwtConfig jwt = new JwtConfig(SECRET, 2);

    @Test void requiresNonblankStrongSecretAndBoundedLifetime() {
        for (String secret : new String[]{"", " ", "short"})
            assertThrows(RuntimeException.class, () -> new JwtConfig(secret, 2));
        for (long hours : new long[]{0, -1, 25})
            assertThrows(IllegalArgumentException.class, () -> new JwtConfig(SECRET, hours));
    }

    @Test void rejectsOldTokensWithoutIssuerAudienceOrSessionIdentity() {
        String legacy = Jwts.builder().setSubject("biuro")
                .setExpiration(Date.from(Instant.now().plusSeconds(7200)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwt.parseClaims(legacy));
    }
}
