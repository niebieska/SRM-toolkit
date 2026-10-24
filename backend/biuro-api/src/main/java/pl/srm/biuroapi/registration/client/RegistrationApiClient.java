package pl.srm.biuroapi.registration.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.api.StatusUpdateRequest;
import pl.srm.biuroapi.registration.model.RegistrationDetail;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.TurnusListPerson;

import java.util.List;

@Component
public class RegistrationApiClient {

    private static final Logger log = LoggerFactory.getLogger(RegistrationApiClient.class);

    private final RestClient restClient;
    private final String registrationApiUrl;

    private record StatusChangePayload(String status, String rejectionReason,
                                       String actorAccountId, String actorUsername) {}

    public RegistrationApiClient(RestClient.Builder restClientBuilder,
                                 @Value("${registration.api.url:http://localhost:8080}") String registrationApiUrl,
                                 @Value("${registration.api.username}") String serviceUsername,
                                 @Value("${registration.api.password}") String servicePassword) {
        Assert.hasText(serviceUsername, "Registration service username must not be blank");
        Assert.hasText(servicePassword, "Registration service password must not be blank");
        this.restClient = restClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + HttpHeaders.encodeBasicAuth(serviceUsername, servicePassword, null))
                .build();
        this.registrationApiUrl = registrationApiUrl;
    }

    public record AcceptedCounts(Long participants, Long staff) {}

    public AcceptedCounts fetchAcceptedCounts(String turnusCode) {
        try {
            AcceptedCounts counts = restClient.get()
                    .uri(registrationApiUrl + "/api/registrations/accepted-counts?turnusCode={code}", turnusCode)
                    .retrieve().body(AcceptedCounts.class);
            if (counts == null || counts.participants() == null || counts.staff() == null
                    || counts.participants() < 0 || counts.staff() < 0)
                throw new IllegalStateException("Invalid accepted counts");
            return counts;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Nie można sprawdzić liczby przyjętych zgłoszeń. Pojemność turnusu nie została zmniejszona.");
        }
    }

    public List<RegistrationSummary> fetchRegistrations() {
        try {
            List<RegistrationSummary> body = restClient.get()
                    .uri(registrationApiUrl + "/api/registrations")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (body == null) throw new IllegalStateException("Missing registration list response");
            return body;
        } catch (Exception exception) {
            log.error("Nie udało się pobrać zgłoszeń z registration-api", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Błąd komunikacji z registration-api");
        }
    }

    public List<TurnusListPerson> fetchTurnusList(String turnusCode) {
        try {
            List<TurnusListPerson> body = restClient.get()
                    .uri(registrationApiUrl + "/api/registrations/turnus-list?turnusCode={code}", turnusCode)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (body == null) throw new IllegalStateException("Missing Turnus list response");
            return body;
        } catch (Exception exception) {
            log.warn("Cannot load Turnus list for {}", turnusCode);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Nie można wczytać list turnusu. Spróbuj ponownie później.");
        }
    }

    public RegistrationDetail fetchRegistration(String code) {
        try {
            RegistrationDetail body = restClient.get()
                    .uri(registrationApiUrl + "/api/registrations/{code}", code)
                    .retrieve()
                    .body(RegistrationDetail.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia");
            }
            return body;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception exception) {
            log.error("Nie udało się pobrać zgłoszenia {} z registration-api", code, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Błąd komunikacji z registration-api");
        }
    }

    public RegistrationSummary updateStatus(String code, StatusUpdateRequest request,
                                            String actorAccountId, String actorUsername) {
        try {
            RegistrationSummary body = restClient.patch()
                    .uri(registrationApiUrl + "/api/registrations/{code}/status", code)
                    .body(new StatusChangePayload(request.status(), request.rejectionReason(),
                            actorAccountId, actorUsername))
                    .retrieve()
                    .body(RegistrationSummary.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Brak odpowiedzi z registration-api");
            }
            return body;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception exception) {
            log.error("Nie udało się zaktualizować statusu zgłoszenia {}", code, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Błąd komunikacji z registration-api");
        }
    }
}
