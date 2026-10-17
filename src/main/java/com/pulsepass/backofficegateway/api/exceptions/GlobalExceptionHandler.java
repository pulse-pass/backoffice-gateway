package com.pulsepass.backofficegateway.api.exceptions;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String messageKey,
            String code,
            List<ExtraFieldErrorResponse> extraFields
    ) {
        String message = this.messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.getReasonPhrase(), message, code, extraFields));
    }

    @ExceptionHandler(ServiceRequestException.class)
    public ResponseEntity<ErrorResponse> handleServiceRequest(ServiceRequestException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", "GEN00500", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        logger.warn(
                "GlobalExceptionHandler.handleNotFound - Resource not found {}",
                Map.of("path", ex.getResourcePath())
        );
        return build(HttpStatus.NOT_FOUND, "error.not_found", "GEN00404", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex
    ) {
        logger.warn(
                "GlobalExceptionHandler.handleMethodNotAllowed - Method not allowed {}",
                Map.of("method", ex.getMethod())
        );
        return build(HttpStatus.METHOD_NOT_ALLOWED, "error.method_not_allowed", "GEN00405", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        logger.error("GlobalExceptionHandler.handleGeneric - Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", "GEN00500", List.of());
    }
}
