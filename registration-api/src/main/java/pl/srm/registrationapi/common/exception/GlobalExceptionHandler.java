package pl.srm.registrationapi.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TurnusNotFoundException.class)
    public ResponseEntity<?> handleTurnusNotFound(TurnusNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of(
                "error", "TURNUS_NOT_FOUND"
        ));
    }
}