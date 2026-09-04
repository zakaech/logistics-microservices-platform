package com.logistics.order.exception;

/**
 * Raised when a downstream service cannot be reached or answers unusably.
 *
 * <p>Mapped to 503, not 500: nothing is broken here, and the distinction tells a client whether
 * retrying is worth anything. The offending service is named so an operator knows where to look.
 */
public class UpstreamServiceException extends RuntimeException {

    private final transient String service;

    public UpstreamServiceException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public UpstreamServiceException(String service, String message) {
        this(service, message, null);
    }

    public String getService() {
        return service;
    }
}
