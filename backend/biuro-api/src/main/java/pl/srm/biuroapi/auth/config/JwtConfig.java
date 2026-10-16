package pl.srm.biuroapi.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtConfig {
    private static final String ISSUER = "srm-biuro";
    private static final String AUDIENCE = "srm-biuro-ui";
    private final SecretKey signingKey;
    private final Duration lifetime;

    public JwtConfig(@Value("${biuro.jwt.secret}") String secret,
                     @Value("${biuro.jwt.expiry-hours:2}") long expiryHours) {
        Assert.hasText(secret, "JWT signing secret must not be blank");
        Assert.isTrue(expiryHours > 0 && expiryHours <= 24, "JWT lifetime must be between 1 and 24 hours");
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofHours(expiryHours);
    }

    public Instant expiresAt(Instant issuedAt) { return issuedAt.plus(lifetime); }

    public String generateToken(String accountId, String sessionId, Instant now) {
        return Jwts.builder().setIssuer(ISSUER).setAudience(AUDIENCE).setSubject(accountId)
                .setId(sessionId).setIssuedAt(Date.from(now)).setExpiration(Date.from(expiresAt(now)))
                .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    public Claims parseClaims(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(signingKey)
                .requireIssuer(ISSUER).requireAudience(AUDIENCE).build().parseClaimsJws(token).getBody();
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getId() == null || claims.getId().isBlank() || claims.getExpiration() == null)
            throw new IllegalArgumentException("Missing session claims");
        return claims;
    }
}
