package com.pulsepass.backofficegateway.infrastructure.proxies;

public class ServiceRequestException extends RuntimeException {

    public ServiceRequestException(Throwable cause) {
        super("Request to the target service failed", cause);
    }
}
