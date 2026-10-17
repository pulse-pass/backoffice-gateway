package com.pulsepass.backofficegateway.unit.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.exceptions.ErrorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void constructor_values_exposesComponents() {
        var response = new ErrorResponse("Not Found", "missing", "GEN00404", List.of());

        assertThat(response.title()).isEqualTo("Not Found");
        assertThat(response.message()).isEqualTo("missing");
        assertThat(response.code()).isEqualTo("GEN00404");
        assertThat(response.extraFields()).isEmpty();
    }
}
