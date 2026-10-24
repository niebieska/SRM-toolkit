package pl.srm.biuroapi.registration.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.api.StatusUpdateRequest;

import java.net.ConnectException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RegistrationApiClientTest {
    private MockRestServiceServer server;
    private RegistrationApiClient client;
    private final String credentials = "Basic " + HttpHeaders.encodeBasicAuth("biuro-service", "test-password", null);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RegistrationApiClient(builder, "http://registration-api:8080", "biuro-service", "test-password");
    }

    @Test
    void acceptedCountsUseServiceCredentialsAndPreserveIndependentCounts() {
        server.expect(requestTo("http://registration-api:8080/api/registrations/accepted-counts?turnusCode=T1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("{\"participants\":42,\"staff\":8}", MediaType.APPLICATION_JSON));
        var counts = client.fetchAcceptedCounts("T1");
        assertEquals(42L, counts.participants());
        assertEquals(8L, counts.staff());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"participants\":42}", "{\"participants\":-1,\"staff\":2}", "not-json"})
    void missingInvalidOrNegativeCountsCannotAuthorizeCapacityReduction(String body) {
        server.expect(requestTo("http://registration-api:8080/api/registrations/accepted-counts?turnusCode=T1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var error = assertThrows(ResponseStatusException.class, () -> client.fetchAcceptedCounts("T1"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertFalse(error.getReason().contains(body));
        server.verify();
    }

    @Test
    void unavailableCountsCannotAuthorizeCapacityReduction() {
        server.expect(requestTo("http://registration-api:8080/api/registrations/accepted-counts?turnusCode=T1"))
                .andRespond(withException(new java.net.SocketTimeoutException("timed out")));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, () -> client.fetchAcceptedCounts("T1")).getStatusCode());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "not-json", "{}"})
    void missingOrMalformedRegistrationListIsReportedAsUpstreamFailure(String body) {
        server.expect(requestTo("http://registration-api:8080/api/registrations"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, client::fetchRegistrations).getStatusCode());
        server.verify();
    }

    @Test
    void noContentRegistrationResponseIsAnUpstreamFailure() {
        server.expect(requestTo("http://registration-api:8080/api/registrations"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, client::fetchRegistrations).getStatusCode());
        server.verify();
    }

    @Test
    void authenticatesListDetailAndStatusRequests() {
        server.expect(requestTo("http://registration-api:8080/api/registrations"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://registration-api:8080/api/registrations/ABC"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("""
                        {"registrationCode":"ABC","minor":false,"statusHistory":[
                          {"id":1,"previousStatus":"NEW","newStatus":"ACCEPTED",
                           "changedAt":"2027-01-02T10:00:00","actorUsername":"operator"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://registration-api:8080/api/registrations/ABC/status"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(content().json("""
                        {"status":"WAITLIST","rejectionReason":null,
                         "actorAccountId":"actor-id","actorUsername":"operator"}
                        """))
                .andRespond(withSuccess("{\"registrationCode\":\"ABC\",\"minor\":false,\"status\":\"WAITLIST\"}", MediaType.APPLICATION_JSON));
        assertTrue(client.fetchRegistrations().isEmpty());
        var detail = client.fetchRegistration("ABC");
        assertEquals("ABC", detail.registrationCode());
        assertEquals("operator", detail.statusHistory().getFirst().actorUsername());
        assertEquals("WAITLIST", client.updateStatus("ABC", new StatusUpdateRequest("WAITLIST", null), "actor-id", "operator").status());
        server.verify();
    }

    @Test
    void reportsUpstreamAuthenticationFailureInsteadOfEmptyList() {
        server.expect(requestTo("http://registration-api:8080/api/registrations"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, client::fetchRegistrations);
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "detail", "status"})
    void reportsConnectionFailureForEveryOperation(String operation) {
        String path = switch (operation) {
            case "list" -> "/api/registrations";
            case "detail" -> "/api/registrations/ABC";
            default -> "/api/registrations/ABC/status";
        };
        server.expect(requestTo("http://registration-api:8080" + path))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withException(new ConnectException("Connection refused")));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> {
            switch (operation) {
                case "list" -> client.fetchRegistrations();
                case "detail" -> client.fetchRegistration("ABC");
                default -> client.updateStatus("ABC", new StatusUpdateRequest("WAITLIST", null), "actor-id", "operator");
            }
        });
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
        assertFalse(failure.getReason().contains("test-password"));
        assertFalse(failure.getReason().contains(credentials));
        server.verify();
    }

    @Test
    void refusesBlankPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegistrationApiClient(RestClient.builder(), "http://localhost:8080", "biuro-service", " "));
    }
    @Test
    void preservesStaffRoleAndFunctionFromTheSummaryContract() {
        server.expect(requestTo("http://registration-api:8080/api/registrations"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("""
                        [{"registrationCode":"S1","registrationType":"STAFF",
                          "status":"ACCEPTED","turnusCode":"T1","minor":false,
                          "role":"ksiadz","subrole":"sternik"}]
                        """, MediaType.APPLICATION_JSON));
        var staff = client.fetchRegistrations().getFirst();
        assertEquals("ksiadz", staff.role());
        assertEquals("sternik", staff.subrole());
        server.verify();
    }

    @Test
    void turnusListUsesServiceAuthenticationAndPreservesBirthDateAndContacts() {
        server.expect(requestTo("http://registration-api:8080/api/registrations/turnus-list?turnusCode=T1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("""
                        [{"registrationCode":"R1","registrationType":"PARTICIPANT",
                          "firstName":"Anna","lastName":"Nowak","birthDate":"2009-01-17",
                          "personPhone":"111222333","guardianPhone":"222333444","icePhone":null,
                          "incompleteData":false,"minor":true,
                          "additionalData":{"email":"person@example.org","contactFirstName":"Maria",
                            "contactLastName":"Nowak","contactRelation":"matka","guardianEmail":"guardian@example.org",
                            "imageConsent":false,"qualifications":[]}}]
                        """, MediaType.APPLICATION_JSON));
        var row = client.fetchTurnusList("T1").getFirst();
        assertEquals(LocalDate.of(2009, 1, 17), row.birthDate());
        assertEquals("222333444", row.guardianPhone());
        assertTrue(row.minor());
        assertEquals("Maria", row.additionalData().contactFirstName());
        assertEquals(Boolean.FALSE, row.additionalData().imageConsent());
        assertNull(row.icePhone());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "not-json"})
    void invalidTurnusListDoesNotBecomeAnEmptyList(String body) {
        server.expect(requestTo("http://registration-api:8080/api/registrations/turnus-list?turnusCode=T1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, () -> client.fetchTurnusList("T1")).getStatusCode());
        server.verify();
    }

    @Test
    void turnusListTimeoutReturnsSafeError() {
        server.expect(requestTo("http://registration-api:8080/api/registrations/turnus-list?turnusCode=T1"))
                .andRespond(withException(new java.net.SocketTimeoutException("secret upstream details")));
        var error = assertThrows(ResponseStatusException.class, () -> client.fetchTurnusList("T1"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertFalse(error.getReason().contains("secret"));
        server.verify();
    }
}
