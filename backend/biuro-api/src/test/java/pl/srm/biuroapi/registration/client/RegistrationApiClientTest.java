package pl.srm.biuroapi.registration.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.api.StatusUpdateRequest;

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
    void authenticatesListDetailAndStatusRequests() {
        server.expect(requestTo("http://registration-api:8080/api/registrations"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://registration-api:8080/api/registrations/ABC"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("{\"registrationCode\":\"ABC\",\"minor\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://registration-api:8080/api/registrations/ABC/status"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, credentials))
                .andRespond(withSuccess("{\"registrationCode\":\"ABC\",\"minor\":false,\"status\":\"WAITLIST\"}", MediaType.APPLICATION_JSON));
        assertTrue(client.fetchRegistrations().isEmpty());
        assertEquals("ABC", client.fetchRegistration("ABC").registrationCode());
        assertEquals("WAITLIST", client.updateStatus("ABC", new StatusUpdateRequest("WAITLIST", null)).status());
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

    @Test
    void refusesBlankPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegistrationApiClient(RestClient.builder(), "http://localhost:8080", "biuro-service", " "));
    }
}
