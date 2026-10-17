package pl.srm.biuroapi.auth.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import pl.srm.biuroapi.auth.service.AuthService;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtFilterTest {
    final JwtConfig jwt = new JwtConfig("test-only-signing-key-for-unit-tests-at-least-32-bytes", 2);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void databaseAndTransactionFailuresFailClosedWithSafe503() throws Exception {
        for (RuntimeException failure : new RuntimeException[]{
                new DataAccessResourceFailureException("private database detail"),
                new CannotCreateTransactionException("private connection detail")}) {
            var auth = mock(AuthService.class);
            when(auth.authenticate("account-id", "session-id")).thenThrow(failure);
            var request = new MockHttpServletRequest("GET", "/api/auth/me");
            String token = jwt.generateToken("account-id", "session-id", Instant.now());
            request.addHeader("Authorization", "Bearer " + token);
            var response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            new JwtFilter(jwt, auth).doFilter(request, response, chain);
            assertEquals(503, response.getStatus());
            assertTrue(response.getContentType().startsWith("application/json"));
            assertTrue(response.getContentAsString().contains("chwilowo niedostępna"));
            assertFalse(response.getContentAsString().contains("private"));
            assertFalse(response.getContentAsString().contains(token));
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verifyNoInteractions(chain);
        }
    }
}
