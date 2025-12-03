package bookfronterab.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException; // NUEVO IMPORT
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> resourceNotFoundException(ResourceNotFoundException ex) {
        return new ResponseEntity<>(err(404, "NOT_FOUND", ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(err(400, "BAD_REQUEST", ex.getMessage()));
    }
    
    // --- NUEVOS MANEJADORES PARA RESOLVER EL ERROR 500/403 ---

    // 1. Maneja fallos de @PreAuthorize (Acceso denegado) que Spring no mapea correctamente.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        // Devuelve 403 Forbidden
        return new ResponseEntity<>(err(403, "FORBIDDEN", "Acceso denegado. No tiene permisos."), HttpStatus.FORBIDDEN);
    }
    
    // 2. Maneja errores de lógica de negocio (ej. "Sala ya reservada", "Límite semanal alcanzado").
    // Esto evita que IllegalStateException se convierta en 500.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        // Devuelve 409 Conflict (el recurso no está en el estado correcto) o 400 Bad Request
        return new ResponseEntity<>(err(400, "BUSINESS_RULE_VIOLATION", ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Este handler ahora solo atrapa excepciones verdaderamente inesperadas.
        return new ResponseEntity<>(err(500, "INTERNAL_SERVER_ERROR", ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }


    private Map<String, Object> err(int status, String code, String msg) {
        return Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", status,
                "code", code,
                "message", msg
        );
    }
}