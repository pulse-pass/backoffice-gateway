package com.pulsepass.backofficegateway.unit.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.exceptions.ExtraFieldErrorResponse;
import org.junit.jupiter.api.Test;

class ExtraFieldErrorResponseTest {

    @Test
    void constructor_values_exposesComponents() {
        var response = new ExtraFieldErrorResponse("body", "INVALID_FORMAT");

        assertThat(response.field()).isEqualTo("body");
        assertThat(response.error()).isEqualTo("INVALID_FORMAT");
    }
}
