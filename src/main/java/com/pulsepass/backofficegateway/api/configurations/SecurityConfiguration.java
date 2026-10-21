package com.pulsepass.backofficegateway.api.configurations;

import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfiguration {

    private final String publicKey;
    private final String issuer;
    private final SecurityErrorHandler securityErrorHandler;

    public SecurityConfiguration(
            @Value("${app.security.jwt.public-key}") String publicKey,
            @Value("${app.security.jwt.issuer}") String issuer,
            SecurityErrorHandler securityErrorHandler
    ) {
        this.publicKey = publicKey;
        this.issuer = issuer;
        this.securityErrorHandler = securityErrorHandler;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRolesConverter());
        return converter;
    }

    private RequestMatcher publicRoutes() {
        PathPatternRequestMatcher.Builder matchers = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                matchers.matcher(HttpMethod.OPTIONS, "/**"),
                matchers.matcher(HttpMethod.POST, "/legal_persons"),
                matchers.matcher(HttpMethod.POST, "/auth/login"),
                matchers.matcher(HttpMethod.POST, "/auth/refresh"),
                matchers.matcher(HttpMethod.GET, "/actuator/health/**")
        );
    }

    private BearerTokenResolver bearerTokenResolver(RequestMatcher publicRoutes) {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> publicRoutes.matches(request) ? null : resolver.resolve(request);
    }

    @Bean
    public JwtDecoder jwtDecoder() throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(this.publicKey);
        RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(this.issuer));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher publicRoutes = this.publicRoutes();
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(publicRoutes).permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                        .anyRequest().hasAnyRole("ORGANIZER", "ADMIN"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this.securityErrorHandler)
                        .accessDeniedHandler(this.securityErrorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(this.bearerTokenResolver(publicRoutes))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(this.jwtAuthenticationConverter()))
                        .authenticationEntryPoint(this.securityErrorHandler)
                        .accessDeniedHandler(this.securityErrorHandler))
                .build();
    }
}
