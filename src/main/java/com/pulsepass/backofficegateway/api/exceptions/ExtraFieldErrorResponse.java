package com.pulsepass.backofficegateway.api.exceptions;

public record ExtraFieldErrorResponse(
        String field,
        String error
) {
}
