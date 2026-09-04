package com.logistics.auth.exception;

/** Raised when a token is malformed, expired, revoked, or simply unknown. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
