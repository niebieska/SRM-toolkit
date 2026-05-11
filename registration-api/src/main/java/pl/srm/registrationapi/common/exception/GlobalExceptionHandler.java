package pl.srm.registrationapi.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.srm.registrationapi.common.api.ApiError;
import pl.srm.registrationapi.registration.exception.AgeTooLowException;
import pl.srm.registrationapi.registration.exception.AlreadyRegisteredException;
import pl.srm.registrationapi.registration.exception.TurnusUnavailableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TurnusNotFoundException.class)
    public ResponseEntity<ApiError> handleTurnusNotFound() {
        return ResponseEntity
                .status(404)
                .body(new ApiError(
                        "TURNUS_NOT_FOUND",
                        "Turnus not found"
                ));
    }

    @ExceptionHandler(AlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleAlreadyRegistered() {
        return ResponseEntity
                .status(409)
                .body(new ApiError(
                        "ALREADY_REGISTERED",
                        "User already registered for this turnus"
                ));
    }

    @ExceptionHandler(AgeTooLowException.class)
    public ResponseEntity<ApiError> handleAgeTooLow() {
        return ResponseEntity
                .badRequest()
                .body(new ApiError(
                        "AGE_TOO_LOW",
                        "User does not meet minimum age requirement"
                ));
    }

    @ExceptionHandler(TurnusUnavailableException.class)
    public ResponseEntity<ApiError> handleTurnusUnavailable(TurnusUnavailableException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ApiError(
                        ex.getCode(),   // ✅ use stable code
                        "Turnus is not available"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric() {
        return ResponseEntity
                .badRequest()
                .body(new ApiError(
                        "INVALID_REQUEST",
                        "Invalid request"
                ));
    }
}