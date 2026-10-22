package com.pulsepass.backofficegateway.api.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MessageSource messageSource;
    private final JsonMapper jsonMapper;

    public SecurityErrorHandler(MessageSource messageSource, JsonMapper jsonMapper) {
        this.messageSource = messageSource;
        this.jsonMapper = jsonMapper;
    }

    private void write(
            HttpServletResponse response,
            HttpStatus status,
            String messageKey,
            String code
    ) throws IOException {
        String message = this.messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = new ErrorResponse(status.getReasonPhrase(), message, code, List.of());
        this.jsonMapper.writeValue(response.getOutputStream(), body);
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        this.write(response, HttpStatus.UNAUTHORIZED, "error.unauthorized", "GEN00401");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        this.write(response, HttpStatus.FORBIDDEN, "error.forbidden", "GEN00403");
    }
}
