package pl.srm.biuroapi.auth.config;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.srm.biuroapi.auth.service.AuthService;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtConfig jwt;
    private final AuthService auth;

    public JwtFilter(JwtConfig jwt, AuthService auth) { this.jwt = jwt; this.auth = auth; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var claims = jwt.parseClaims(header.substring(BEARER_PREFIX.length()));
                auth.authenticate(claims.getSubject(), claims.getId()).ifPresent(principal -> {
                    var authorities = principal.passwordChangeRequired()
                            ? List.of(new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"))
                            : principal.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (DataAccessException | TransactionException exception) {
                SecurityContextHolder.clearContext();
                log.error("Biuro account authentication unavailable", exception);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"status\":503,\"message\":\"Usługa jest chwilowo niedostępna. Spróbuj ponownie później.\"}");
                return;
            } catch (JwtException | IllegalArgumentException exception) {
                // Invalid/legacy tokens remain unauthenticated; never log the token.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
