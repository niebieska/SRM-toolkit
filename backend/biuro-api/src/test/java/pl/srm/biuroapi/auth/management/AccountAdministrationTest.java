package pl.srm.biuroapi.auth.management;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AccountAdministrationTest extends AccountManagementHttpSupport {
    @Test void sysAdminCanSelfAssignOperationalRolesWithoutRelogin() throws Exception {
        assertEquals(200, request("GET", "/api/admin/accounts", adminToken, null).statusCode());
        assertEquals(403, request("GET", "/api/turnuses", adminToken, null).statusCode());
        assertEquals(200, update(adminToken, adminId, 0, true, Set.of(AccountRole.SYS_ADMIN, AccountRole.OPERATOR), Set.of()).statusCode());
        assertEquals(200, request("GET", "/api/turnuses", adminToken, null).statusCode());
        assertEquals(1, events("ACCOUNT_UPDATED", adminId));
        String actor = jdbc.queryForObject("SELECT actor_account_id FROM biuro_account_audit WHERE event_type='ACCOUNT_UPDATED'", String.class);
        assertEquals(adminId, actor);
        var options = request("GET", "/api/admin/accounts/turnus-options", adminToken, null);
        assertEquals(200, options.statusCode());
        assertFalse(options.body().contains("staffRequirements"));
    }

    @Test void nonAdministratorsCannotManageOrInspectAccounts() throws Exception {
        var body = Map.of("version", 0, "displayName", "Attack", "active", true,
                "roles", Set.of(AccountRole.SYS_ADMIN), "turnusCodes", Set.of());
        for (String token : new String[]{null, operatorToken, viewerToken}) {
            int expected = token == null ? 401 : 403;
            for (String path : new String[]{"/api/admin/accounts", "/api/admin/accounts/turnus-options", "/api/admin/accounts/" + adminId + "/audit"})
                assertEquals(expected, request("GET", path, token, null).statusCode());
            assertEquals(expected, request("PATCH", "/api/admin/accounts/" + viewerId, token, body).statusCode());
            assertEquals(expected, request("POST", "/api/admin/accounts/" + adminId + "/password", token,
                    Map.of("version", 0, "password", "replacement-password-456")).statusCode());
            assertEquals(expected, request("POST", "/api/admin/accounts", token, body).statusCode());
        }
        assertEquals(Set.of(AccountRole.VIEWER), management.get(viewerId, false).roles());
        assertEquals(0, events("ACCOUNT_UPDATED", viewerId));
    }

    @Test void createsHashedTemporaryAccountAndRejectsDuplicateOrInvalidInput() throws Exception {
        var body = Map.of("username", "New.User", "displayName", "Nowy użytkownik", "password", "temporary-password-456",
                "roles", Set.of(AccountRole.VIEWER), "turnusCodes", Set.of("ZAGLE26T2"));
        var created = request("POST", "/api/admin/accounts", adminToken, body);
        assertEquals(201, created.statusCode(), created.body());
        String id = JsonPath.read(created.body(), "$.id");
        assertEquals("new.user", JsonPath.read(created.body(), "$.username"));
        assertEquals(true, JsonPath.read(created.body(), "$.passwordChangeRequired"));
        assertFalse(created.body().contains("temporary-password"));
        assertFalse(created.body().contains("passwordHash"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM biuro_account WHERE id = ?", String.class, id);
        assertTrue(encoder.matches("temporary-password-456", hash));
        assertEquals(409, request("POST", "/api/admin/accounts", adminToken, body).statusCode());
        String token = login("NEW.USER", "temporary-password-456");
        assertEquals(403, request("GET", "/api/viewer/turnuses", token, null).statusCode());
        var changed = request("POST", "/api/auth/password", token,
                Map.of("currentPassword", "temporary-password-456", "newPassword", "personal-password-789"));
        assertEquals(200, changed.statusCode(), changed.body());
        String next = JsonPath.read(changed.body(), "$.token");
        assertEquals(200, request("GET", "/api/viewer/turnuses", next, null).statusCode());
        assertEquals(1, events("ACCOUNT_CREATED", id));
        assertEquals(1, events("PASSWORD_CHANGED", id));
        String details = jdbc.queryForObject("SELECT details FROM biuro_account_audit WHERE event_type='ACCOUNT_CREATED' AND target_account_id=?", String.class, id);
        assertFalse(details.contains(hash)); assertFalse(details.contains("temporary-password-456"));
        assertEquals(400, request("POST", "/api/admin/accounts", adminToken, Map.of("username", "bad user", "displayName", "Bad",
                "password", PASSWORD, "roles", Set.of(AccountRole.VIEWER), "turnusCodes", Set.of())).statusCode());
        assertEquals(400, request("POST", "/api/admin/accounts", adminToken, Map.of("username", "other", "displayName", "Bad",
                "password", PASSWORD, "roles", Set.of(AccountRole.VIEWER), "turnusCodes", Set.of("UNKNOWN"))).statusCode());
    }

    @Test void resetsPasswordRevokesAllSessionsAndRequiresNewPasswordChange() throws Exception {
        String secondToken = login("operator", PASSWORD);
        var result = request("POST", "/api/admin/accounts/" + operatorId + "/password", adminToken,
                Map.of("version", 0, "password", "temporary-password-456"));
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(1, ((Number)JsonPath.read(result.body(), "$.version")).longValue());
        for (String token : new String[]{operatorToken, secondToken}) assertEquals(401, request("GET", "/api/auth/me", token, null).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", null, Map.of("username", "operator", "password", PASSWORD)).statusCode());
        String temporary = login("operator", "temporary-password-456");
        assertEquals(403, request("GET", "/api/turnuses", temporary, null).statusCode());
        var changed = request("POST", "/api/auth/password", temporary,
                Map.of("currentPassword", "temporary-password-456", "newPassword", "personal-password-789"));
        assertEquals(200, changed.statusCode());
        String next = JsonPath.read(changed.body(), "$.token");
        assertEquals(200, request("GET", "/api/turnuses", next, null).statusCode());
        assertEquals(1, events("PASSWORD_RESET", operatorId));
        assertEquals(2, management.get(operatorId, false).version());
        assertEquals(409, request("POST", "/api/admin/accounts/" + operatorId + "/password", adminToken,
                Map.of("version", 1, "password", "another-password-123")).statusCode());
        assertEquals(200, request("GET", "/api/auth/me", next, null).statusCode());
    }

    @Test void disableAndReactivateNeverRevivesOldTokens() throws Exception {
        assertEquals(200, update(adminToken, viewerId, 0, false, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T1")).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", viewerToken, null).statusCode());
        assertEquals(200, update(adminToken, viewerId, 1, true, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T1")).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", viewerToken, null).statusCode());
        assertEquals(200, request("GET", "/api/viewer/turnuses", login("viewer", PASSWORD), null).statusCode());
    }

    @Test void protectsLastAdminAndRejectsStaleVersion() throws Exception {
        assertEquals(409, update(adminToken, adminId, 0, false, Set.of(AccountRole.SYS_ADMIN), Set.of()).statusCode());
        assertEquals(409, update(adminToken, adminId, 0, true, Set.of(AccountRole.OPERATOR), Set.of()).statusCode());
        assertEquals(0, management.get(adminId, false).version());
        assertEquals(0, events("ACCOUNT_UPDATED", adminId));
        assertEquals(200, update(adminToken, viewerId, 0, true, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T2")).statusCode());
        assertEquals(409, update(adminToken, viewerId, 0, true, Set.of(AccountRole.OPERATOR), Set.of()).statusCode());
        assertEquals(Set.of(AccountRole.VIEWER), management.get(viewerId, false).roles());
        assertEquals(400, request("PATCH", "/api/admin/accounts/" + viewerId, adminToken, Map.of("displayName", "Missing version",
                "active", true, "roles", Set.of(AccountRole.VIEWER), "turnusCodes", Set.of())).statusCode());
    }

    @Test void auditWriteFailureRollsBackAccountAndSessionChanges() throws Exception {
        jdbc.execute("ALTER TABLE biuro_account_audit RENAME TO biuro_account_audit_unavailable");
        try {
            var failed = update(adminToken, viewerId, 0, false, Set.of(AccountRole.OPERATOR), Set.of("ZAGLE26T2"));
            assertEquals(503, failed.statusCode(), failed.body());
            assertFalse(failed.body().contains("UPDATE"));
        } finally { jdbc.execute("ALTER TABLE biuro_account_audit_unavailable RENAME TO biuro_account_audit"); }
        var result = management.get(viewerId, false);
        assertTrue(result.active()); assertEquals(0, result.version());
        assertEquals(Set.of(AccountRole.VIEWER), result.roles());
        assertEquals(Set.of("ZAGLE26T1"), result.turnusCodes());
        assertEquals(200, request("GET", "/api/auth/me", viewerToken, null).statusCode());
    }

    @Test void rechecksActorPermissionsInsideAdministrativeTransaction() throws Exception {
        account("second.admin", Set.of(AccountRole.SYS_ADMIN), Set.of());
        String secondToken = login("second.admin", PASSWORD);
        String sid = jdbc.queryForObject("SELECT id FROM biuro_session WHERE account_id = ?", String.class, adminId);
        var staleActor = accounts.findSession(adminId, sid, Instant.now()).orElseThrow();
        assertEquals(200, update(secondToken, adminId, 0, true, Set.of(AccountRole.OPERATOR), Set.of()).statusCode());
        var denied = assertThrows(ResponseStatusException.class, () -> service.update(staleActor, viewerId, 0,
                "Attempt", true, Set.of(AccountRole.OPERATOR), Set.of()));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals(0, management.get(viewerId, false).version());
    }

    @Test void auditHistoryRecordsIdentityAndRejectsInvalidLimits() throws Exception {
        assertEquals(200, update(adminToken, viewerId, 0, true, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T2")).statusCode());
        var history = request("GET", "/api/admin/accounts/" + viewerId + "/audit?limit=1", adminToken, null);
        assertEquals(200, history.statusCode(), history.body());
        assertEquals("ACCOUNT_UPDATED", JsonPath.read(history.body(), "$[0].event"));
        assertEquals(adminId, JsonPath.read(history.body(), "$[0].actorAccountId"));
        assertFalse(history.body().contains(PASSWORD)); assertFalse(history.body().contains(viewerToken));
        assertEquals(400, request("GET", "/api/admin/accounts/" + viewerId + "/audit?limit=201", adminToken, null).statusCode());
        assertEquals(400, request("GET", "/api/admin/accounts/" + viewerId + "/audit?limit=bad", adminToken, null).statusCode());
        assertEquals(204, request("POST", "/api/auth/logout", viewerToken, null).statusCode());
        assertEquals(1, events("LOGOUT", viewerId));
    }
}
