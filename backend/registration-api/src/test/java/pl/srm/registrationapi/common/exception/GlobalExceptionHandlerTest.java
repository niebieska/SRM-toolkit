package pl.srm.registrationapi.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.srm.registrationapi.common.dto.ApiError;
import pl.srm.registrationapi.registration.exception.RegistrationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAgeTooLowToUnprocessableEntity() {
        ResponseEntity<ApiError> response = handler.handleRegistrationException(
                new RegistrationException("AGE_TOO_LOW", "Za młody uczestnik.")
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("AGE_TOO_LOW", response.getBody().code());
    }
}
