package com.pulsepass.backofficegateway.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.BackofficeGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class BackofficeGatewayApplicationTest {

    @Test
    void application_class_isSpringBootApplication() {
        assertThat(BackofficeGatewayApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }
}
