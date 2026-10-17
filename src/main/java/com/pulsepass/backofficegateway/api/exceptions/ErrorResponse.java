package com.pulsepass.backofficegateway.api.exceptions;

import java.util.List;

public record ErrorResponse(
        String title,
        String message,
        String code,
        List<ExtraFieldErrorResponse> extraFields
) {
}
