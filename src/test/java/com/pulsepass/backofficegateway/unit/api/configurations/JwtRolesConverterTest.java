package com.pulsepass.backofficegateway.unit.api.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.configurations.JwtRolesConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRolesConverterTest {

    private final JwtRolesConverter converter = new JwtRolesConverter();

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    @Test
    void convert_realmRoles_returnsUppercaseRoleAuthorities() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("organizer", "admin"))));

        var authorities = converter.convert(jwt);

        assertThat(authorities).extracting("authority").containsExactly("ROLE_ORGANIZER", "ROLE_ADMIN");
    }

    @Test
    void convert_realmAccessWithoutRoles_returnsEmpty() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of()));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void convert_noRealmAccess_returnsEmpty() {
        Jwt jwt = jwt(Map.of("sub", "user"));

        assertThat(converter.convert(jwt)).isEmpty();
    }
}
