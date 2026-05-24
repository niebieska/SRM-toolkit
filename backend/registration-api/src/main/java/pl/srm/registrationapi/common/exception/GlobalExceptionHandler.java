package pl.srm.registrationapi.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.srm.registrationapi.common.dto.ApiError;
import pl.srm.registrationapi.registration.exception.RegistrationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ApiError> handleRegistrationException(RegistrationException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "ALREADY_REGISTERED" -> HttpStatus.CONFLICT;
            case "AGE_TOO_LOW" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "TURNUS_NOT_FOUND", "REGISTRATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TURNUS_INACTIVE", "REGISTRATION_CLOSED" -> HttpStatus.GONE;
            case "INVALID_PESEL", "MISSING_GUARDIAN", "MISSING_CONSENTS", "INVALID_STATUS", "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Wystąpił nieoczekiwany błąd."));
    }
}
