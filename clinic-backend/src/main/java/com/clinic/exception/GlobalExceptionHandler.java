package com.clinic.exception;

import com.clinic.dto.response.ErrorResponse;
import com.clinic.dto.response.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

/**
 * Maps every exception leaving a controller onto the standard error envelope
 * (API contract 7 / 7a). Domain exceptions carry their own canonical
 * {@link ErrorCode}; framework exceptions are translated here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        log.debug("Domain exception {}: {}", code, ex.getMessage());
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, ex.getMessage()));
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException ex) {
        return validationError(ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        return validationError(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        lastPathSegment(violation.getPropertyPath().toString()), violation.getMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        return validationError(details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationError(List.of(new FieldErrorDetail(ex.getParameterName(), "Parameter is required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationError(List.of(new FieldErrorDetail(ex.getName(), "Value has an invalid format")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return validationError(List.of(new FieldErrorDetail("body", "Request body is missing or malformed")));
    }

    /**
     * A @PreAuthorize denial is thrown inside the controller, past the point
     * where the security filter chain's AccessDeniedHandler would see it, so it
     * has to be mapped here or it would surface as a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorCode code = ErrorCode.UNAUTHORIZED_ACCESS;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, code.defaultMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, code.defaultMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnknownRoute(NoResourceFoundException ex) {
        return transportError(HttpStatus.NOT_FOUND, "Requested resource was not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return transportError(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this resource");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return transportError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Never leak internals (API contract 6): log the detail, return a generic message.
        log.error("Unhandled exception", ex);
        return transportError(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    /**
     * Envelope for transport-level failures (unknown route, wrong method,
     * unsupported media type, unexpected error). The contract's ErrorCode enum
     * covers domain failures only, so errorCode is omitted here rather than
     * inventing a value outside section 7a.
     */
    private ResponseEntity<ErrorResponse> transportError(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(false, message, null, List.of(),
                java.time.OffsetDateTime.now(), com.clinic.config.RequestIdFilter.currentRequestId());
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ErrorResponse> validationError(List<FieldErrorDetail> details) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, code.defaultMessage(), details));
    }

    private FieldErrorDetail toDetail(FieldError error) {
        String message = error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage();
        return new FieldErrorDetail(error.getField(), message);
    }

    private String lastPathSegment(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
