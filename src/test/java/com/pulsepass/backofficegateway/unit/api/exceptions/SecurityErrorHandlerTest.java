package com.pulsepass.backofficegateway.unit.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorHandlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private SecurityErrorHandler handler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        handler = new SecurityErrorHandler(messageSource, jsonMapper);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void commence_english_writes401Gen00401() throws Exception {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(jsonMapper.readValue(response.getContentAsString(), Map.class)).isEqualTo(Map.of(
                "title", "Unauthorized",
                "message", "Authentication is required to access this resource.",
                "code", "GEN00401",
                "extra_fields", List.of()
        ));
    }

    @Test
    void commence_portuguese_writesTranslatedMessage() throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertThat(jsonMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8))
                .get("message").asString())
                .isEqualTo("É preciso estar autenticado para acessar este recurso.");
    }

    @Test
    void handle_english_writes403Gen00403() throws Exception {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(jsonMapper.readValue(response.getContentAsString(), Map.class)).isEqualTo(Map.of(
                "title", "Forbidden",
                "message", "You do not have permission to access this resource.",
                "code", "GEN00403",
                "extra_fields", List.of()
        ));
    }

    @Test
    void handle_portuguese_writesTranslatedMessage() throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(jsonMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8))
                .get("message").asString())
                .isEqualTo("Você não tem permissão para acessar este recurso.");
    }
}
