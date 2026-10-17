package com.pulsepass.backofficegateway.unit.infrastructure.proxies;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import org.junit.jupiter.api.Test;

class ServiceRequestExceptionTest {

    @Test
    void constructor_cause_keepsLiteralMessageAndCause() {
        var cause = new IllegalStateException("boom");

        var exception = new ServiceRequestException(cause);

        assertThat(exception.getMessage()).isEqualTo("Request to the target service failed");
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
