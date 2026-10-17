package com.pulsepass.backofficegateway.unit.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.exceptions.ErrorResponse;
import com.pulsepass.backofficegateway.api.exceptions.GlobalExceptionHandler;
import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.internal", Locale.ENGLISH, "internal");
        messages.addMessage("error.not_found", Locale.ENGLISH, "not found");
        messages.addMessage("error.method_not_allowed", Locale.ENGLISH, "method not allowed");
        handler = new GlobalExceptionHandler(messages);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void handleServiceRequest_transportFailure_returns500WithGen00500() {
        var response = handler.handleServiceRequest(new ServiceRequestException(new RuntimeException("refused")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(
                new ErrorResponse("Internal Server Error", "internal", "GEN00500", List.of()));
    }

    @Test
    void handleNotFound_missingResource_returns404WithGen00404() {
        var response = handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/unknown", "/unknown"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Not Found", "not found", "GEN00404", List.of()));
    }

    @Test
    void handleMethodNotAllowed_unsupportedMethod_returns405WithGen00405() {
        var response = handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("GET"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isEqualTo(
                new ErrorResponse("Method Not Allowed", "method not allowed", "GEN00405", List.of()));
    }

    @Test
    void handleGeneric_unexpectedException_returns500WithGen00500() {
        var response = handler.handleGeneric(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(
                new ErrorResponse("Internal Server Error", "internal", "GEN00500", List.of()));
    }

    @Test
    void handleGeneric_portugueseLocale_usesPortugueseMessage() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.internal", Locale.of("pt"), "interno");
        LocaleContextHolder.setLocale(Locale.of("pt"));

        var response = new GlobalExceptionHandler(messages).handleGeneric(new IllegalStateException("boom"));

        assertThat(response.getBody().message()).isEqualTo("interno");
    }
}
