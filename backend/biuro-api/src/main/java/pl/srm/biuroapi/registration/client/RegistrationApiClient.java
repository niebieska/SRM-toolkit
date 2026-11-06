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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import pl.srm.biuroapi.registration.api.StatusUpdateRequest;
import pl.srm.biuroapi.registration.model.RegistrationDetail;
import pl.srm.biuroapi.registration.model.RegistrationSummary;
import pl.srm.biuroapi.registration.model.TurnusListPerson;
import pl.srm.biuroapi.registration.model.PaymentLedgerEntry;
import pl.srm.biuroapi.registration.api.PaymentChangeRequest;
import pl.srm.biuroapi.registration.api.DiscountChangeRequest;
import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;

@Component
public class RegistrationApiClient {

    private static final Logger log = LoggerFactory.getLogger(RegistrationApiClient.class);

    private final RestClient restClient;
    private final String registrationApiUrl;

    private record StatusChangePayload(String status, String rejectionReason,
                                       String actorAccountId, String actorUsername) {}
    private record RegistrationChangePayload(Long expectedVersion, java.util.Map<String, Object> changes,
                                             String actorAccountId, String actorUsername) {}
    private record PaymentPayload(LocalDate date, BigDecimal amount, String method, String label, String note,
                                  String correctionReason, String actorAccountId, String actorUsername) {}
    private record DiscountPayload(BigDecimal amount, String description, BigDecimal feeAmount,
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

    public List<PaymentLedgerEntry> fetchPaymentLedger() {
        try {
            List<PaymentLedgerEntry> body = restClient.get().uri(registrationApiUrl + "/api/registrations/payment-ledger")
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (body == null) throw new IllegalStateException("Missing payment ledger response");
            return body;
        } catch (Exception exception) {
            log.error("Nie udało się pobrać rejestru wpłat z registration-api", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie można wczytać rejestru wpłat.");
        }
    }

    public PaymentLedgerEntry addPayment(String code, PaymentChangeRequest request, String idempotencyKey,
                                         String accountId, String username) {
        return paymentWrite(code, request, idempotencyKey, null, accountId, username);
    }

    public PaymentLedgerEntry editPayment(String code, long paymentId, PaymentChangeRequest request,
                                          String accountId, String username) {
        return paymentWrite(code, request, null, paymentId, accountId, username);
    }

    private PaymentLedgerEntry paymentWrite(String code, PaymentChangeRequest request, String idempotencyKey, Long paymentId,
                                             String accountId, String username) {
        try {
            PaymentPayload payload = new PaymentPayload(request.date(), request.amount(), request.method(),
                    request.label(), request.note(), request.reason(), accountId, username);
            var spec = restClient.post();
            var uri = paymentId == null
                    ? registrationApiUrl + "/api/registrations/{code}/payments"
                    : registrationApiUrl + "/api/registrations/{code}/payments/{paymentId}";
            var requestSpec = paymentId == null ? spec.uri(uri, code).header("Idempotency-Key", idempotencyKey)
                    : restClient.put().uri(uri, code, paymentId);
            PaymentLedgerEntry body = requestSpec.body(payload).retrieve().body(PaymentLedgerEntry.class);
            if (body == null) throw new IllegalStateException("Missing payment response");
            return body;
        } catch (RestClientResponseException exception) {
            throw upstreamPaymentError(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Nie udało się zapisać wpłaty dla zgłoszenia {}", code, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie można zapisać wpłaty.");
        }
    }

    public PaymentLedgerEntry setDiscount(String code, DiscountChangeRequest request, BigDecimal fee,
                                          String accountId, String username) {
        try {
            DiscountPayload payload = new DiscountPayload(request.amount(), request.description(), fee, accountId, username);
            PaymentLedgerEntry body = restClient.put()
                    .uri(registrationApiUrl + "/api/registrations/{code}/discount", code)
                    .body(payload).retrieve().body(PaymentLedgerEntry.class);
            if (body == null) throw new IllegalStateException("Missing discount response");
            return body;
        } catch (RestClientResponseException exception) {
            throw upstreamPaymentError(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Nie udało się zapisać rabatu dla zgłoszenia {}", code, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie można zapisać rabatu.");
        }
    }

    private ResponseStatusException upstreamPaymentError(RestClientResponseException exception) {
        if (exception.getStatusCode().is4xxClientError())
            return new ResponseStatusException(exception.getStatusCode(), "Nie można zapisać danych finansowych. Odśwież dane i spróbuj ponownie.");
        log.error("Registration API rejected payment operation with HTTP {}", exception.getStatusCode().value());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie można zapisać danych finansowych.");
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

    public RegistrationDetail updateRegistrationData(String code, pl.srm.biuroapi.registration.api.RegistrationEditRequest request,
                                                       String actorAccountId, String actorUsername) {
        try {
            RegistrationDetail body = restClient.patch()
                    .uri(registrationApiUrl + "/api/registrations/{code}/data", code)
                    .body(new RegistrationChangePayload(request.expectedVersion(), request.changes(), actorAccountId, actorUsername))
                    .retrieve()
                    .body(RegistrationDetail.class);
            if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Brak odpowiedzi z registration-api");
            return body;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                String message = exception.getStatusCode().value() == 409
                        ? "Zgłoszenie zostało wcześniej zmienione. Odśwież szczegóły przed zapisem."
                        : "Nie można zapisać zmian. Sprawdź wprowadzone dane.";
                throw new ResponseStatusException(exception.getStatusCode(), message);
            }
            log.error("Nie udało się zaktualizować danych zgłoszenia {}", code, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Błąd komunikacji z registration-api");
        } catch (Exception exception) {
            log.error("Nie udało się zaktualizować danych zgłoszenia {}", code, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Błąd komunikacji z registration-api");
        }
    }
}
