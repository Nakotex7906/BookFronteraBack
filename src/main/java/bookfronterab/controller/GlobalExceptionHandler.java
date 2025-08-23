package bookfronterab.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(err(400, "BAD_REQUEST", ex.getMessage()));
    }
    @ExceptionHandler({IllegalStateException.class, SecurityException.class})
    public ResponseEntity<Map<String,Object>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(err(409, "CONFLICT", ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(err(400, "VALIDATION_ERROR", ex.getMessage()));
    }
    private Map<String,Object> err(int status, String code, String msg) {
        return Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", status,
                "code", code,
                "message", msg
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex) {
        Map<String, Object> body = Map.of(
                "code", "NOT_FOUND",
                "timestamp", Instant.now().toString(),
                "message", ex.getMessage(),
                "status", 404
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        Map<String, Object> body = Map.of(
                "code", "INTERNAL_ERROR",
                "timestamp", Instant.now().toString(),
                "message", ex.getMessage(),
                "status", 500
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

}
