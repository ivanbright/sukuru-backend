package rw.shop.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import rw.shop.common.web.CorrelationIds;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getErrorCode().name(), ex.getMessage(), ex.getDetails(), CorrelationIds.current()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.add(Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())));
        }
        for (var error : ex.getBindingResult().getGlobalErrors()) {
            details.add(Map.of("field", error.getObjectName(), "message", String.valueOf(error.getDefaultMessage())));
        }
        return validation(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        List<Map<String, Object>> details = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation ->
                details.add(Map.of("field", violation.getPropertyPath().toString(), "message", violation.getMessage())));
        return validation(details);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return validation(List.of(Map.of("field", "request", "message", "Malformed or missing request value")));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(401)
                .body(new ApiError(ErrorCode.UNAUTHENTICATED.name(), "Invalid credentials", List.of(), CorrelationIds.current()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(new ApiError(ErrorCode.FORBIDDEN.name(), "You are not allowed to perform this action", List.of(), CorrelationIds.current()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ApiError(ErrorCode.NOT_FOUND.name(), "Resource not found", List.of(), CorrelationIds.current()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(409)
                .body(new ApiError(ErrorCode.CONFLICT.name(), "Request conflicts with existing data", List.of(), CorrelationIds.current()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(500)
                .body(new ApiError(ErrorCode.INTERNAL_ERROR.name(), "An unexpected error occurred", List.of(), CorrelationIds.current()));
    }

    private ResponseEntity<ApiError> validation(List<Map<String, Object>> details) {
        return ResponseEntity.status(400)
                .body(new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Request validation failed", details, CorrelationIds.current()));
    }
}
