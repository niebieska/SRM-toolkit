package pl.srm.biuroapi.auth.management;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewerTurnusAccessTest extends AccountManagementHttpSupport {
    @Test void viewerOnlyGetsAssignedTurnusSummariesAndListData() throws Exception {
        var index = request("GET", "/api/viewer/turnuses", viewerToken, null);
        assertEquals(200, index.statusCode(), index.body());
        assertEquals(1, (int)JsonPath.read(index.body(), "$.length()"));
        assertEquals("ZAGLE26T1", JsonPath.read(index.body(), "$[0].turnusCode"));
        Map<String, Object> summary = JsonPath.read(index.body(), "$[0]");
        assertEquals(Set.of("turnusCode", "turnusName", "turnusLocation", "startDate", "endDate"), summary.keySet());
        var lists = request("GET", "/api/viewer/turnuses/ZAGLE26T1/lists", viewerToken, null);
        assertEquals(200, lists.statusCode(), lists.body());
        assertEquals("00210112345", JsonPath.read(lists.body(), "$.entries[0].person.pesel"));
        assertEquals("001234567", JsonPath.read(lists.body(), "$.entries[0].person.personPhone"));
        assertEquals("test@example.invalid", JsonPath.read(lists.body(), "$.entries[0].person.additionalData.email"));
        assertFalse(lists.body().contains("staffRequirements"));
        verify(registrations).fetchTurnusList("ZAGLE26T1");
    }

    @Test void viewerCannotProbeOtherTurnusyOrUseOperatorAndWriteRoutes() throws Exception {
        for (String code : new String[]{"ZAGLE26T2", "UNKNOWN"})
            assertEquals(403, request("GET", "/api/viewer/turnuses/" + code + "/lists", viewerToken, null).statusCode());
        for (String path : new String[]{"/api/turnuses", "/api/turnuses/ZAGLE26T1", "/api/turnuses/ZAGLE26T1/lists",
                "/api/biuro/registrations", "/api/turnuses/staffing-categories?seasonType=SUMMER"})
            assertEquals(403, request("GET", path, viewerToken, null).statusCode());
        assertEquals(403, request("PATCH", "/api/biuro/registrations/TEST/status", viewerToken, Map.of("status", "ACCEPTED")).statusCode());
        for (String method : new String[]{"POST", "PATCH", "DELETE"})
            assertEquals(403, request(method, "/api/viewer/turnuses/ZAGLE26T1/lists", viewerToken, Map.of()).statusCode());
        assertEquals(401, request("GET", "/api/viewer/turnuses", null, null).statusCode());
        assertEquals(403, request("GET", "/api/viewer/turnuses", adminToken, null).statusCode());
        verifyNoInteractions(registrations);
    }

    @Test void assignmentRemovalAppliesImmediatelyWithoutRelogin() throws Exception {
        assertEquals(200, update(adminToken, viewerId, 0, true, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T2")).statusCode());
        assertEquals(403, request("GET", "/api/viewer/turnuses/ZAGLE26T1/lists", viewerToken, null).statusCode());
        var available = request("GET", "/api/viewer/turnuses", viewerToken, null);
        assertEquals("ZAGLE26T2", JsonPath.read(available.body(), "$[0].turnusCode"));
        assertEquals(200, request("GET", "/api/viewer/turnuses/ZAGLE26T2/lists", viewerToken, null).statusCode());
        assertEquals(200, update(adminToken, viewerId, 1, true, Set.of(AccountRole.VIEWER), Set.of()).statusCode());
        assertEquals("[]", request("GET", "/api/viewer/turnuses", viewerToken, null).body());
        assertEquals(403, request("GET", "/api/viewer/turnuses/ZAGLE26T2/lists", viewerToken, null).statusCode());
        verify(registrations, times(1)).fetchTurnusList(anyString());
    }

    @Test void operatorRoleOverridesViewerAssignmentsAndRemovalTakesEffect() throws Exception {
        assertEquals(200, update(adminToken, viewerId, 0, true, Set.of(AccountRole.VIEWER, AccountRole.OPERATOR), Set.of()).statusCode());
        var index = request("GET", "/api/viewer/turnuses", viewerToken, null);
        assertEquals(2, (int)JsonPath.read(index.body(), "$.length()"));
        assertEquals(200, request("GET", "/api/turnuses", viewerToken, null).statusCode());
        assertEquals(200, request("GET", "/api/viewer/turnuses/ZAGLE26T2/lists", viewerToken, null).statusCode());
        assertEquals(200, update(adminToken, viewerId, 1, true, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T1")).statusCode());
        assertEquals(403, request("GET", "/api/turnuses", viewerToken, null).statusCode());
        assertEquals(403, request("GET", "/api/viewer/turnuses/ZAGLE26T2/lists", viewerToken, null).statusCode());
        assertEquals(2, (int)JsonPath.read(request("GET", "/api/viewer/turnuses", operatorToken, null).body(), "$.length()"));
    }
}
